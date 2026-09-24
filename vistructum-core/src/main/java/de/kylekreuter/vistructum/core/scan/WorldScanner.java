package de.kylekreuter.vistructum.core.scan;

import com.google.gson.JsonObject;
import de.kylekreuter.vistructum.core.alert.DetectionReporter;
import de.kylekreuter.vistructum.core.alert.Finding;
import de.kylekreuter.vistructum.core.scene.SurfaceScene;
import de.kylekreuter.vistructum.core.scene.WorldBox;
import de.kylekreuter.vistructum.core.sidecar.Detection;
import de.kylekreuter.vistructum.core.sidecar.InferResult;
import de.kylekreuter.vistructum.core.sidecar.SidecarClient;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The daily fullscan: walks all generated chunks of a world tile by tile and checks each tile's surface with the
 * fullscan model.
 *
 * <p>All state lives on the main thread, driven by a one-tick timer. Per tick it checks one region file for generated
 * chunks, or requests up to {@code chunksPerTick} chunk loads and snapshots; sampling and the sidecar call run off
 * the main thread, one tile at a time, so a scan never competes with players for more than a sliver of a tick.
 */
public final class WorldScanner {

    private static final String KIND = "fullscan";
    private static final Pattern REGION_FILE = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

    private final Plugin plugin;
    private final SidecarClient client;
    private final DetectionReporter reporter;
    private final int chunksPerTick;
    private final ExecutorService sampler = Executors.newSingleThreadExecutor(r -> new Thread(r, "vistructum-scan"));
    private final Deque<World> queue = new ArrayDeque<>();

    private BukkitTask task;
    private World world;
    private Deque<int[]> regions;
    private final Set<Long> chunks = new LinkedHashSet<>();
    private Deque<ScanPlan.Tile> tiles;
    private int tileCount;
    private ScanPlan.Tile tile;
    private Deque<int[]> pendingLoads;
    private Map<Long, ChunkSnapshot> snapshots;
    private int awaitedLoads;
    private boolean inferring;
    private int findings;
    private int failures;

    public WorldScanner(Plugin plugin, SidecarClient client, DetectionReporter reporter, int chunksPerTick) {
        this.plugin = plugin;
        this.client = client;
        this.reporter = reporter;
        this.chunksPerTick = chunksPerTick;
    }

    public boolean running() {
        return world != null;
    }

    public void enqueue(World next) {
        if (!queue.contains(next) && next != world) {
            queue.add(next);
        }
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
        }
    }

    public void stop() {
        queue.clear();
        finish("abgebrochen");
    }

    public void close() {
        stop();
        sampler.shutdownNow();
    }

    public String status() {
        if (world == null) {
            return "kein Scan aktiv";
        }
        if (tiles == null) {
            return "Scan " + world.getName() + ": suche generierte Chunks (" + chunks.size() + " bisher)";
        }
        int done = tileCount - tiles.size() - (tile == null ? 0 : 1);
        return "Scan " + world.getName() + ": Kachel " + done + "/" + tileCount + ", " + findings + " Funde, "
                + failures + " Fehler, " + queue.size() + " Welten in der Warteschlange";
    }

    private void tick() {
        if (world == null && !begin()) {
            task.cancel();
            task = null;
            return;
        }
        if (tiles == null) {
            findChunks();
        } else if (tile == null) {
            nextTile();
        } else if (!pendingLoads.isEmpty()) {
            requestLoads();
        } else if (awaitedLoads == 0 && !inferring) {
            infer();
        }
    }

    private boolean begin() {
        world = queue.poll();
        if (world == null) {
            return false;
        }
        regions = new ArrayDeque<>();
        File[] files = new File(world.getWorldFolder(), regionFolder(world)).listFiles();
        for (File file : files == null ? new File[0] : files) {
            Matcher m = REGION_FILE.matcher(file.getName());
            if (m.matches()) {
                regions.add(new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))});
            }
        }
        chunks.clear();
        // chunks generated since the last save are not in the region files yet
        for (Chunk chunk : world.getLoadedChunks()) {
            chunks.add(SurfaceSampler.chunkKey(chunk.getX(), chunk.getZ()));
        }
        tiles = null;
        tile = null;
        findings = 0;
        failures = 0;
        plugin.getLogger().info("fullscan of " + world.getName() + " started, " + regions.size() + " region files");
        return true;
    }

    private void findChunks() {
        int[] region = regions.poll();
        if (region == null) {
            List<int[]> coords = new ArrayList<>(chunks.size());
            chunks.forEach(key -> coords.add(new int[]{(int) (key >> 32), (int) (long) key}));
            tiles = new ArrayDeque<>(ScanPlan.tiles(coords));
            tileCount = tiles.size();
            plugin.getLogger().info("fullscan of " + world.getName() + ": " + chunks.size() + " chunks in " + tileCount + " tiles");
            return;
        }
        for (int cx = region[0] << 5; cx < (region[0] + 1) << 5; cx++) {
            for (int cz = region[1] << 5; cz < (region[1] + 1) << 5; cz++) {
                if (world.isChunkGenerated(cx, cz)) {
                    chunks.add(SurfaceSampler.chunkKey(cx, cz));
                }
            }
        }
    }

    private void nextTile() {
        tile = tiles.poll();
        if (tile == null) {
            finish("fertig");
            return;
        }
        pendingLoads = new ArrayDeque<>();
        int minCx = tile.originX() >> 4;
        int minCz = tile.originZ() >> 4;
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
        World scanned = world;
        Map<Long, ChunkSnapshot> target = snapshots;
        for (int i = 0; i < chunksPerTick && !pendingLoads.isEmpty(); i++) {
            int[] c = pendingLoads.poll();
            awaitedLoads++;
            scanned.getChunkAtAsync(c[0], c[1], false, chunk -> {
                if (target == snapshots) {
                    if (chunk != null) {
                        target.put(SurfaceSampler.chunkKey(c[0], c[1]), chunk.getChunkSnapshot(true, false, false));
                    }
                    awaitedLoads--;
                }
            });
        }
    }

    private void infer() {
        inferring = true;
        World scanned = world;
        ScanPlan.Tile current = tile;
        Map<Long, ChunkSnapshot> taken = snapshots;
        SurfaceSampler surface = new SurfaceSampler(scanned.getMinHeight());
        CompletableFuture
                .supplyAsync(() -> surface.sample(taken, current.originX(), current.originZ(), ScanPlan.TILE_SIZE,
                        ScanPlan.TILE_SIZE), sampler)
                .thenCompose(scene -> client.infer(KIND, scene, context(scanned, current))
                        .thenApply(result -> new Object[]{scene, result}))
                .whenComplete((pair, error) -> {
                    if (!plugin.isEnabled()) {
                        return;
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (scanned != world || current != tile) {
                            return;
                        }
                        if (error != null) {
                            failures++;
                            plugin.getLogger().warning("fullscan tile " + current + " failed: " + error);
                        } else {
                            report(scanned, current, (SurfaceScene) pair[0], (InferResult) pair[1]);
                        }
                        snapshots = null;
                        tile = null;
                        inferring = false;
                    });
                });
    }

    private void report(World scanned, ScanPlan.Tile current, SurfaceScene scene, InferResult result) {
        if (!result.flagged()) {
            return;
        }
        for (Detection d : result.detections()) {
            int minY = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;
            for (int row = Math.max(0, d.top()); row < Math.min(scene.height(), d.bottom()); row++) {
                for (int col = Math.max(0, d.left()); col < Math.min(scene.width(), d.right()); col++) {
                    int i = scene.index(row, col);
                    if (scene.blocks()[i] != SurfaceScene.UNKNOWN) {
                        minY = Math.min(minY, scene.heights()[i]);
                        maxY = Math.max(maxY, scene.heights()[i]);
                    }
                }
            }
            if (minY > maxY) {
                continue;
            }
            WorldBox box = new WorldBox(current.originX() + d.left(), minY, current.originZ() + d.top(),
                    current.originX() + d.right() - 1, maxY, current.originZ() + d.bottom() - 1);
            if (reporter.report(new Finding(KIND, scanned.getName(), box, d.score(), d.votes(), Set.of(),
                    "Kachel " + current.originX() + "," + current.originZ(), Instant.now()))) {
                findings++;
            }
        }
    }

    private void finish(String how) {
        if (world != null) {
            plugin.getLogger().info("fullscan of " + world.getName() + " " + how + ": " + findings + " findings, "
                    + failures + " failed tiles");
        }
        world = null;
        regions = null;
        tiles = null;
        tile = null;
        snapshots = null;
        inferring = false;
        chunks.clear();
    }

    private static JsonObject context(World world, ScanPlan.Tile tile) {
        JsonObject context = new JsonObject();
        context.addProperty("source", KIND);
        context.addProperty("world", world.getName());
        context.addProperty("origin_x", tile.originX());
        context.addProperty("origin_z", tile.originZ());
        return context;
    }

    private static String regionFolder(World world) {
        return switch (world.getEnvironment()) {
            case NETHER -> "DIM-1/region";
            case THE_END -> "DIM1/region";
            default -> "region";
        };
    }
}
