package de.kylekreuter.vistructum.core.scan;

import com.google.gson.JsonObject;
import de.kylekreuter.vistructum.api.BlockBox;
import de.kylekreuter.vistructum.api.FindingCandidate;
import de.kylekreuter.vistructum.api.ScanCause;
import de.kylekreuter.vistructum.api.ScanFinishedEvent;
import de.kylekreuter.vistructum.api.ScanJob;
import de.kylekreuter.vistructum.api.ScanProgressEvent;
import de.kylekreuter.vistructum.api.ScanStartedEvent;
import de.kylekreuter.vistructum.api.ScanStatus;
import de.kylekreuter.vistructum.api.Source;
import de.kylekreuter.vistructum.core.MainThread;
import de.kylekreuter.vistructum.core.alert.FindingReporter;
import de.kylekreuter.vistructum.core.alert.PreviewCrop;
import de.kylekreuter.vistructum.core.inference.Inference;
import de.kylekreuter.vistructum.core.region.ChunkColumn;
import de.kylekreuter.vistructum.core.region.RegionChunk;
import de.kylekreuter.vistructum.core.region.RegionFile;
import de.kylekreuter.vistructum.core.scene.BlockPos;
import de.kylekreuter.vistructum.core.volume.ShapeFilter;
import de.kylekreuter.vistructum.core.volume.VolumeSettings;
import de.kylekreuter.vistructum.core.volume.VolumeShape;
import de.kylekreuter.vistructum.core.volume.VolumeShapes;
import de.kylekreuter.vistructum.inference.Detection;
import de.kylekreuter.vistructum.inference.InferResult;
import de.kylekreuter.vistructum.inference.ModelKind;
import de.kylekreuter.vistructum.inference.SurfaceScene;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public final class WorldScanner {

    private static final long RETRY_TICKS = 20L * 60;

    private final MainThread mainThread;
    private final ScanStore scans;
    private final Inference inference;
    private final FindingReporter reporter;
    private final int chunksPerTick;
    private final VolumeSettings volume;
    private final Clock clock;
    private final Logger logger;
    private final ExecutorService workers;

    private BukkitTask task;
    private long generation;
    private boolean waiting;
    private ScanJob job;
    private World world;
    private PaperBlockStates states;
    private int dataVersion;
    private ScanPlan.Tile tile;
    private Map<Long, ChunkColumn> columns;
    private Map<Long, ChunkSnapshot> snapshots;
    private Deque<Long> pendingLoads;
    private int awaitedLoads;

    public WorldScanner(MainThread mainThread, ScanStore scans, Inference inference, FindingReporter reporter,
                        int chunksPerTick, int workerCount, VolumeSettings volume, Clock clock) {
        if (workerCount < 1) {
            throw new IllegalArgumentException("workerCount must be >= 1, got " + workerCount);
        }
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.scans = Objects.requireNonNull(scans, "scans");
        this.inference = Objects.requireNonNull(inference, "inference");
        this.reporter = Objects.requireNonNull(reporter, "reporter");
        this.chunksPerTick = chunksPerTick;
        this.volume = Objects.requireNonNull(volume, "volume");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = mainThread.plugin().getLogger();
        this.workers = Executors.newFixedThreadPool(workerCount, r -> {
            Thread thread = new Thread(r, "vistructum-scan");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(mainThread.plugin(), this::tick, 1, 1);
        }
    }

    public CompletableFuture<Optional<ScanJob>> enqueue(String worldName, ScanCause cause) {
        return scans.enqueue(worldName, cause, clock.instant()).thenApply(enqueued -> {
            enqueued.ifPresent(queued -> mainThread.run(this::start));
            return enqueued;
        });
    }

    public CompletableFuture<List<ScanJob>> cancel() {
        reset();
        return scans.cancelAll(clock.instant()).thenCompose(cancelled -> mainThread.supply(() -> {
            cancelled.forEach(cancelledJob -> Bukkit.getPluginManager().callEvent(new ScanFinishedEvent(cancelledJob)));
            return cancelled;
        }));
    }

    public void close() {
        stopTimer();
        reset();
        workers.shutdownNow();
    }

    private void tick() {
        if (waiting) {
            return;
        }
        if (job == null) {
            await(scans.next(), next -> next.ifPresentOrElse(this::begin, this::stopTimer));
        } else if (job.status() == ScanStatus.QUEUED) {
            plan();
        } else if (tile == null) {
            await(scans.nextTile(job.id()), next -> next.ifPresentOrElse(this::readTile, () -> finish(ScanStatus.DONE)));
        } else if (!pendingLoads.isEmpty()) {
            requestLoads();
        } else if (awaitedLoads == 0) {
            process();
        }
    }

    private void begin(ScanJob next) {
        job = next;
        world = Bukkit.getWorld(next.world());
        if (world == null) {
            logger.warning("fullscan #" + next.id() + ": world " + next.world() + " is not loaded");
            finish(ScanStatus.FAILED);
            return;
        }
        states = new PaperBlockStates();
        dataVersion = Bukkit.getUnsafe().getDataVersion();
        if (next.status() != ScanStatus.QUEUED) {
            logger.info("fullscan #" + next.id() + " of " + world.getName() + " resumed at tile " + next.tilesDone()
                    + "/" + next.tilesTotal());
        }
    }

    private void plan() {
        ScanJob planning = job;
        Path folder = regionFolder(world);
        Set<Long> chunks = new HashSet<>();
        for (Chunk chunk : world.getLoadedChunks()) {
            chunks.add(ChunkKey.of(chunk.getX(), chunk.getZ()));
        }
        CompletableFuture<ScanJob> planned = CompletableFuture.supplyAsync(() -> {
            List<Path> files = regionFiles(folder);
            logger.info("fullscan #" + planning.id() + " of " + planning.world() + " started, " + files.size()
                    + " region files");
            for (Path file : files) {
                try {
                    RegionFile.present(file).forEach(chunk -> chunks.add(ChunkKey.of(chunk[0], chunk[1])));
                } catch (IOException e) {
                    logger.warning("fullscan #" + planning.id() + ": cannot read " + file.getFileName() + ": " + e);
                }
            }
            return chunks.stream().map(key -> new int[]{ChunkKey.x(key), ChunkKey.z(key)}).toList();
        }, workers).thenCompose(coords -> scans.plan(planning.id(), ScanPlan.tiles(coords)).thenApply(stored -> {
            logger.info("fullscan #" + stored.id() + " of " + stored.world() + ": " + coords.size() + " chunks in "
                    + stored.tilesTotal() + " tiles");
            return stored;
        }));
        await(planned, stored -> {
            job = stored;
            Bukkit.getPluginManager().callEvent(new ScanStartedEvent(stored));
        });
    }

    private void readTile(ScanPlan.Tile next) {
        tile = next;
        int minChunkX = next.originX() >> 4;
        int minChunkZ = next.originZ() >> 4;
        int span = ScanPlan.TILE_SIZE >> 4;
        Map<Long, ChunkSnapshot> loaded = new ConcurrentHashMap<>();
        for (int chunkX = minChunkX; chunkX < minChunkX + span; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ < minChunkZ + span; chunkZ++) {
                if (world.isChunkLoaded(chunkX, chunkZ)) {
                    loaded.put(ChunkKey.of(chunkX, chunkZ),
                            world.getChunkAt(chunkX, chunkZ).getChunkSnapshot(true, false, false));
                }
            }
        }
        Path folder = regionFolder(world);
        int serverVersion = dataVersion;
        CompletableFuture<TileRead> read = CompletableFuture.supplyAsync(() ->
                readRegions(folder, minChunkX, minChunkZ, span, loaded.keySet(), serverVersion), workers);
        await(read, result -> {
            columns = result.columns();
            snapshots = loaded;
            pendingLoads = result.fallback();
            awaitedLoads = 0;
            if (pendingLoads.isEmpty()) {
                process();
            }
        });
    }

    private record TileRead(Map<Long, ChunkColumn> columns, Deque<Long> fallback) {
    }

    private static TileRead readRegions(Path folder, int minChunkX, int minChunkZ, int span, Set<Long> loaded,
                                        int serverVersion) {
        Map<Long, ChunkColumn> columns = new ConcurrentHashMap<>();
        Deque<Long> fallback = new ArrayDeque<>();
        int maxChunkX = minChunkX + span - 1;
        int maxChunkZ = minChunkZ + span - 1;
        for (int regionX = Math.floorDiv(minChunkX, RegionFile.SIDE); regionX <= Math.floorDiv(maxChunkX, RegionFile.SIDE);
             regionX++) {
            for (int regionZ = Math.floorDiv(minChunkZ, RegionFile.SIDE);
                 regionZ <= Math.floorDiv(maxChunkZ, RegionFile.SIDE); regionZ++) {
                Path file = folder.resolve("r." + regionX + "." + regionZ + ".mca");
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                List<RegionChunk> chunks;
                try {
                    chunks = RegionFile.read(file, (x, z) -> x >= minChunkX && x <= maxChunkX && z >= minChunkZ
                            && z <= maxChunkZ && !loaded.contains(ChunkKey.of(x, z)));
                } catch (IOException e) {
                    chunks = List.of();
                }
                for (RegionChunk chunk : chunks) {
                    long key = ChunkKey.of(chunk.chunkX(), chunk.chunkZ());
                    Optional<ChunkColumn> column = decode(chunk);
                    if (column.isEmpty() || column.get().dataVersion() < serverVersion) {
                        fallback.add(key);
                    } else if (column.get().dataVersion() == serverVersion && column.get().full()) {
                        columns.put(key, column.get());
                    }
                }
            }
        }
        return new TileRead(columns, fallback);
    }

    private static Optional<ChunkColumn> decode(RegionChunk chunk) {
        if (chunk.nbt().isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ChunkColumn.decode(chunk.chunkX(), chunk.chunkZ(), chunk.nbt().get()));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private void requestLoads() {
        Map<Long, ChunkSnapshot> target = snapshots;
        for (int i = 0; i < chunksPerTick && !pendingLoads.isEmpty(); i++) {
            long key = pendingLoads.poll();
            awaitedLoads++;
            world.getChunkAtAsync(ChunkKey.x(key), ChunkKey.z(key), false, loaded -> {
                if (target != snapshots) {
                    return;
                }
                if (loaded != null) {
                    target.put(key, loaded.getChunkSnapshot(true, false, false));
                }
                awaitedLoads--;
            });
        }
    }

    private void process() {
        ScanJob running = job;
        ScanPlan.Tile current = tile;
        Map<Long, ChunkColumn> read = columns;
        Map<Long, ChunkSnapshot> taken = snapshots;
        PaperBlockStates blockStates = states;
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        int version = dataVersion;
        CompletableFuture<Integer> reported = CompletableFuture.supplyAsync(() -> {
            taken.forEach((key, snapshot) -> read.put(key, SnapshotColumns.of(snapshot, minY, maxY, version)));
            return read;
        }, workers).thenCompose(tileColumns -> tileColumns.values().stream().noneMatch(ChunkColumn::hasBlocks)
                ? CompletableFuture.completedFuture(List.<FindingCandidate>of())
                : surface(running, current, tileColumns, minY, blockStates)
                        .thenCombine(volume(running, tileColumns, blockStates), (surface, volumes) -> {
                            List<FindingCandidate> all = new ArrayList<>(surface);
                            all.addAll(volumes);
                            return all;
                        })).thenCompose(reporter::reportAll);
        CompletableFuture<TileProgress> completed = reported.handle((count, error) -> {
            if (error != null) {
                logger.warning("fullscan #" + running.id() + " tile " + current + " failed: " + error);
            }
            return scans.completeTile(running.id(), current, count == null ? 0 : count, error != null);
        }).thenCompose(stored -> stored).thenCompose(stored -> scans.nextTile(stored.id())
                .thenApply(next -> new TileProgress(stored, next)));
        await(completed, progress -> {
            tile = null;
            columns = null;
            snapshots = null;
            pendingLoads = null;
            job = progress.job();
            Bukkit.getPluginManager().callEvent(new ScanProgressEvent(progress.job()));
            progress.next().ifPresentOrElse(this::readTile, () -> finish(ScanStatus.DONE));
        });
    }

    private record TileProgress(ScanJob job, Optional<ScanPlan.Tile> next) {
    }

    private CompletableFuture<List<FindingCandidate>> surface(ScanJob running, ScanPlan.Tile current,
                                                              Map<Long, ChunkColumn> tileColumns, int minY,
                                                              BlockStates blockStates) {
        return CompletableFuture.supplyAsync(() -> new ColumnSurface(minY, blockStates).sample(tileColumns,
                        current.originX(), current.originZ(), ScanPlan.TILE_SIZE, ScanPlan.TILE_SIZE), workers)
                .thenCompose(scene -> inference.infer(ModelKind.FULLSCAN, scene, context(running, current))
                        .thenApply(result -> candidates(running.world(), current, scene, result)));
    }

    private CompletableFuture<List<FindingCandidate>> volume(ScanJob running, Map<Long, ChunkColumn> tileColumns,
                                                             PaperBlockStates blockStates) {
        VolumeShapes shapes = new VolumeShapes(volume, blockStates::solid);
        return CompletableFuture.supplyAsync(() -> shapes.candidates(tileColumns.values()), workers)
                .thenCompose(byMaterial -> {
                    List<CompletableFuture<List<FindingCandidate>>> checks = new ArrayList<>();
                    byMaterial.forEach((material, positions) -> checks.add(CompletableFuture
                            .supplyAsync(() -> shapes.shapes(material, positions), workers)
                            .thenCompose(found -> checkShapes(running, found))));
                    return CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new))
                            .thenApply(done -> checks.stream().flatMap(check -> check.join().stream()).toList());
                });
    }

    private CompletableFuture<List<FindingCandidate>> checkShapes(ScanJob running, List<VolumeShape> found) {
        List<CompletableFuture<List<FindingCandidate>>> checks = found.stream()
                .map(shape -> inference.infer(ModelKind.MASK, shape.projection().scene(), context(running, shape))
                        .thenApply(result -> candidates(running.world(), shape, result)))
                .toList();
        return CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new))
                .thenApply(done -> checks.stream().flatMap(check -> check.join().stream()).toList());
    }

    private void finish(ScanStatus status) {
        await(scans.finish(job.id(), status, clock.instant()), finished -> {
            logger.info("fullscan #" + finished.id() + " of " + finished.world() + " " + finished.status() + ": "
                    + finished.findings() + " findings, " + finished.failures() + " failed tiles");
            reset();
            Bukkit.getPluginManager().callEvent(new ScanFinishedEvent(finished));
        });
    }

    private <T> void await(CompletableFuture<T> step, Consumer<T> then) {
        waiting = true;
        long started = generation;
        step.whenComplete((value, error) -> mainThread.run(() -> {
            if (started != generation) {
                return;
            }
            waiting = false;
            if (error != null) {
                logger.warning("fullscan paused, retrying in " + RETRY_TICKS / 20 + " s: " + error);
                reset();
                stopTimer();
                Bukkit.getScheduler().runTaskLater(mainThread.plugin(), this::start, RETRY_TICKS);
                return;
            }
            then.accept(value);
        }));
    }

    private void reset() {
        generation++;
        waiting = false;
        job = null;
        world = null;
        states = null;
        tile = null;
        columns = null;
        snapshots = null;
        pendingLoads = null;
        awaitedLoads = 0;
    }

    private void stopTimer() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private static List<FindingCandidate> candidates(String worldName, VolumeShape shape, InferResult result) {
        if (!result.flagged()) {
            return List.of();
        }
        SurfaceScene scene = shape.projection().scene();
        return result.detections().stream().filter(detection -> !ShapeFilter.implausible(scene.modified(),
                scene.width(), scene.height(), detection.top(), detection.left(), detection.bottom(),
                detection.right())).map(detection -> new FindingCandidate(Source.FULLSCAN, worldName,
                shape.projection().toWorld(detection.top(), detection.left(), detection.bottom(), detection.right()),
                detection.score(), detection.votes(), Set.of(),
                "Volumen " + shape.material().replace("minecraft:", "") + ", Achse " + shape.projection().axis(),
                result.modelVersion(), PreviewCrop.ofMask(scene.modified(), scene.width(), scene.height(),
                detection.top(), detection.left(), detection.bottom(), detection.right()))).toList();
    }

    private static List<FindingCandidate> candidates(String worldName, ScanPlan.Tile tile, SurfaceScene scene,
                                             InferResult result) {
        if (!result.flagged()) {
            return List.of();
        }
        List<FindingCandidate> candidates = new ArrayList<>();
        for (Detection detection : result.detections()) {
            surfaceBox(tile, scene, detection).ifPresent(box -> candidates.add(new FindingCandidate(Source.FULLSCAN, worldName,
                    box, detection.score(), detection.votes(), Set.of(),
                    "Kachel " + tile.originX() + "," + tile.originZ(), result.modelVersion(),
                    PreviewCrop.ofLuminance(scene.luminance(), scene.width(), scene.height(), detection.top(),
                            detection.left(), detection.bottom(), detection.right()))));
        }
        return candidates;
    }

    private static Optional<BlockBox> surfaceBox(ScanPlan.Tile tile, SurfaceScene scene, Detection detection) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int row = Math.max(0, detection.top()); row < Math.min(scene.height(), detection.bottom()); row++) {
            for (int col = Math.max(0, detection.left()); col < Math.min(scene.width(), detection.right()); col++) {
                int index = scene.index(row, col);
                if (scene.blocks()[index] != SurfaceScene.UNKNOWN) {
                    minY = Math.min(minY, scene.heights()[index]);
                    maxY = Math.max(maxY, scene.heights()[index]);
                }
            }
        }
        if (minY > maxY) {
            return Optional.empty();
        }
        return Optional.of(new BlockBox(tile.originX() + detection.left(), minY, tile.originZ() + detection.top(),
                tile.originX() + detection.right() - 1, maxY, tile.originZ() + detection.bottom() - 1));
    }

    private static JsonObject context(ScanJob job, ScanPlan.Tile tile) {
        JsonObject context = new JsonObject();
        context.addProperty("source", Source.FULLSCAN.modelKind());
        context.addProperty("world", job.world());
        context.addProperty("origin_x", tile.originX());
        context.addProperty("origin_z", tile.originZ());
        return context;
    }

    private static JsonObject context(ScanJob job, VolumeShape shape) {
        JsonObject context = new JsonObject();
        context.addProperty("source", Source.MASK.modelKind());
        context.addProperty("world", job.world());
        context.addProperty("axis", shape.projection().axis().name());
        BlockPos min = shape.min();
        context.addProperty("min_x", min.x());
        context.addProperty("min_y", min.y());
        context.addProperty("min_z", min.z());
        context.addProperty("blocks", shape.blocks());
        context.addProperty("material", shape.material());
        return context;
    }

    private static List<Path> regionFiles(Path folder) {
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(file -> RegionFile.coordinates(file).isPresent()).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static Path regionFolder(World world) {
        Path root = world.getWorldFolder().toPath();
        return switch (world.getEnvironment()) {
            case NETHER -> root.resolve("DIM-1").resolve("region");
            case THE_END -> root.resolve("DIM1").resolve("region");
            default -> root.resolve("region");
        };
    }
}
