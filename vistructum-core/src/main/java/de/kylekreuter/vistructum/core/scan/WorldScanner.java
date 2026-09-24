package de.kylekreuter.vistructum.core.scan;

import com.google.gson.JsonObject;
import de.kylekreuter.vistructum.api.BlockBox;
import de.kylekreuter.vistructum.api.Preview;
import de.kylekreuter.vistructum.api.ScanCause;
import de.kylekreuter.vistructum.api.ScanJob;
import de.kylekreuter.vistructum.api.ScanStatus;
import de.kylekreuter.vistructum.api.Source;
import de.kylekreuter.vistructum.core.MainThread;
import de.kylekreuter.vistructum.core.alert.FindingDraft;
import de.kylekreuter.vistructum.core.alert.FindingReporter;
import de.kylekreuter.vistructum.core.alert.PreviewCrop;
import de.kylekreuter.vistructum.core.scene.SurfaceScene;
import de.kylekreuter.vistructum.core.sidecar.Detection;
import de.kylekreuter.vistructum.core.sidecar.InferResult;
import de.kylekreuter.vistructum.core.sidecar.SidecarClient;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WorldScanner {

    private static final Pattern REGION_FILE = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

    private final MainThread mainThread;
    private final ScanStore scans;
    private final SidecarClient client;
    private final FindingReporter reporter;
    private final int chunksPerTick;
    private final Clock clock;
    private final Logger logger;
    private final ExecutorService sampler = Executors.newSingleThreadExecutor(r -> new Thread(r, "vistructum-scan"));

    private BukkitTask task;
    private long generation;
    private boolean waiting;
    private ScanJob job;
    private World world;
    private Deque<int[]> regions;
    private Set<Long> chunks;
    private ScanPlan.Tile tile;
    private Deque<int[]> pendingLoads;
    private Map<Long, ChunkSnapshot> snapshots;
    private int awaitedLoads;

    public WorldScanner(MainThread mainThread, ScanStore scans, SidecarClient client, FindingReporter reporter,
                        int chunksPerTick, Clock clock) {
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.scans = Objects.requireNonNull(scans, "scans");
        this.client = Objects.requireNonNull(client, "client");
        this.reporter = Objects.requireNonNull(reporter, "reporter");
        this.chunksPerTick = chunksPerTick;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = mainThread.plugin().getLogger();
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

    public CompletableFuture<Integer> cancel() {
        reset();
        return scans.cancelAll(clock.instant());
    }

    public void close() {
        stopTimer();
        reset();
        sampler.shutdownNow();
    }

    private void tick() {
        if (waiting) {
            return;
        }
        if (job == null) {
            await(scans.next(), next -> next.ifPresentOrElse(this::begin, this::stopTimer));
        } else if (job.status() == ScanStatus.QUEUED) {
            planStep();
        } else if (tile == null) {
            await(scans.nextTile(job.id()), next -> next.ifPresentOrElse(this::loadTile, () -> finish(ScanStatus.DONE)));
        } else if (!pendingLoads.isEmpty()) {
            requestLoads();
        } else if (awaitedLoads == 0) {
            infer();
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
        if (next.status() == ScanStatus.QUEUED) {
            regions = regionsOf(world);
            chunks = new LinkedHashSet<>();
            for (Chunk chunk : world.getLoadedChunks()) {
                chunks.add(SurfaceSampler.chunkKey(chunk.getX(), chunk.getZ()));
            }
            logger.info("fullscan #" + next.id() + " of " + world.getName() + " started, " + regions.size()
                    + " region files");
        } else {
            logger.info("fullscan #" + next.id() + " of " + world.getName() + " resumed at tile " + next.tilesDone()
                    + "/" + next.tilesTotal());
        }
    }

    private void planStep() {
        int[] region = regions.poll();
        if (region != null) {
            for (int cx = region[0] << 5; cx < (region[0] + 1) << 5; cx++) {
                for (int cz = region[1] << 5; cz < (region[1] + 1) << 5; cz++) {
                    if (world.isChunkGenerated(cx, cz)) {
                        chunks.add(SurfaceSampler.chunkKey(cx, cz));
                    }
                }
            }
            return;
        }
        List<int[]> coords = new ArrayList<>(chunks.size());
        chunks.forEach(key -> coords.add(new int[]{(int) (key >> 32), (int) (long) key}));
        int chunkCount = chunks.size();
        regions = null;
        chunks = null;
        await(scans.plan(job.id(), ScanPlan.tiles(coords)), planned -> {
            job = planned;
            logger.info("fullscan #" + planned.id() + " of " + planned.world() + ": " + chunkCount + " chunks in "
                    + planned.tilesTotal() + " tiles");
        });
    }

    private void loadTile(ScanPlan.Tile next) {
        tile = next;
        pendingLoads = new ArrayDeque<>();
        int minCx = next.originX() >> 4;
        int minCz = next.originZ() >> 4;
        int span = ScanPlan.TILE_SIZE >> 4;
        for (int cx = minCx; cx < minCx + span; cx++) {
            for (int cz = minCz; cz < minCz + span; cz++) {
                if (world.isChunkGenerated(cx, cz)) {
                    pendingLoads.add(new int[]{cx, cz});
                }
            }
        }
        snapshots = new HashMap<>();
        awaitedLoads = 0;
    }

    private void requestLoads() {
        Map<Long, ChunkSnapshot> target = snapshots;
        for (int i = 0; i < chunksPerTick && !pendingLoads.isEmpty(); i++) {
            int[] chunk = pendingLoads.poll();
            awaitedLoads++;
            world.getChunkAtAsync(chunk[0], chunk[1], false, loaded -> {
                if (target != snapshots) {
                    return;
                }
                if (loaded != null) {
                    target.put(SurfaceSampler.chunkKey(chunk[0], chunk[1]), loaded.getChunkSnapshot(true, false, false));
                }
                awaitedLoads--;
            });
        }
    }

    private void infer() {
        ScanJob running = job;
        ScanPlan.Tile current = tile;
        Map<Long, ChunkSnapshot> taken = snapshots;
        SurfaceSampler surface = new SurfaceSampler(world.getMinHeight());
        CompletableFuture<Integer> reported = CompletableFuture
                .supplyAsync(() -> surface.sample(taken, current.originX(), current.originZ(), ScanPlan.TILE_SIZE,
                        ScanPlan.TILE_SIZE), sampler)
                .thenCompose(scene -> client.infer(Source.FULLSCAN.modelKind(), scene, context(running, current))
                        .thenCompose(result -> reporter.reportAll(drafts(running.world(), current, scene, result))));
        CompletableFuture<Void> completed = reported.handle((count, error) -> {
            if (error != null) {
                logger.warning("fullscan #" + running.id() + " tile " + current + " failed: " + error);
            }
            return scans.completeTile(running.id(), current, count == null ? 0 : count, error != null);
        }).thenCompose(stored -> stored);
        await(completed, done -> {
            tile = null;
            snapshots = null;
        });
    }

    private void finish(ScanStatus status) {
        await(scans.finish(job.id(), status, clock.instant()), finished -> {
            logger.info("fullscan #" + finished.id() + " of " + finished.world() + " " + finished.status() + ": "
                    + finished.findings() + " findings, " + finished.failures() + " failed tiles");
            reset();
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
                logger.warning("fullscan stopped: " + error);
                reset();
                stopTimer();
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
        regions = null;
        chunks = null;
        tile = null;
        pendingLoads = null;
        snapshots = null;
        awaitedLoads = 0;
    }

    private void stopTimer() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private static List<FindingDraft> drafts(String worldName, ScanPlan.Tile tile, SurfaceScene scene,
                                             InferResult result) {
        if (!result.flagged()) {
            return List.of();
        }
        List<FindingDraft> drafts = new ArrayList<>();
        for (Detection detection : result.detections()) {
            surfaceBox(tile, scene, detection).ifPresent(box -> drafts.add(new FindingDraft(Source.FULLSCAN, worldName,
                    box, detection.score(), detection.votes(), Set.of(),
                    "Kachel " + tile.originX() + "," + tile.originZ(), result.modelVersion(),
                    PreviewCrop.ofLuminance(scene.luminance(), scene.width(), scene.height(), detection.top(),
                            detection.left(), detection.bottom(), detection.right()))));
        }
        return drafts;
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

    private static Deque<int[]> regionsOf(World world) {
        Deque<int[]> regions = new ArrayDeque<>();
        File[] files = new File(world.getWorldFolder(), regionFolder(world)).listFiles();
        for (File file : files == null ? new File[0] : files) {
            Matcher m = REGION_FILE.matcher(file.getName());
            if (m.matches()) {
                regions.add(new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))});
            }
        }
        return regions;
    }

    private static String regionFolder(World world) {
        return switch (world.getEnvironment()) {
            case NETHER -> "DIM-1/region";
            case THE_END -> "DIM1/region";
            default -> "region";
        };
    }
}
