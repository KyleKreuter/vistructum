package de.kylekreuter.vistructum.ui.gui;

import de.kylekreuter.vistructum.api.BlockBox;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

final class Scene {

    private static final int MARGIN = 16;
    private static final int WELL = 0x2E2E2E;

    private Scene() {
    }

    static Picture empty() {
        return Picture.solid(Layout.MAP_PIXELS, WELL);
    }

    static CompletableFuture<Picture> capture(Plugin plugin, World world, BlockBox box, Executor mainThread,
                                              Executor background) {
        View view = View.of(box);
        int size = Math.max(Layout.MAP_PIXELS, Math.max(view.planeWidth(box), view.planeHeight(box)) + 1 + MARGIN);
        int left = box.centerX() - size / 2;
        int top = box.centerZ() - size / 2;
        int firstChunkX = switch (view) {
            case TOP, ALONG_Z -> left >> 4;
            case ALONG_X -> box.minX() >> 4;
        };
        int lastChunkX = switch (view) {
            case TOP, ALONG_Z -> (left + size - 1) >> 4;
            case ALONG_X -> (box.maxX() + Picture.BACKDROP) >> 4;
        };
        int firstChunkZ = switch (view) {
            case TOP -> (top - 1) >> 4;
            case ALONG_X -> top >> 4;
            case ALONG_Z -> box.minZ() >> 4;
        };
        int lastChunkZ = switch (view) {
            case TOP, ALONG_X -> (top + size - 1) >> 4;
            case ALONG_Z -> (box.maxZ() + Picture.BACKDROP) >> 4;
        };
        List<CompletableFuture<Chunk>> loads = new ArrayList<>();
        for (int chunkX = firstChunkX; chunkX <= lastChunkX; chunkX++) {
            for (int chunkZ = firstChunkZ; chunkZ <= lastChunkZ; chunkZ++) {
                loads.add(world.getChunkAtAsync(chunkX, chunkZ).thenApply(chunk -> {
                    chunk.addPluginChunkTicket(plugin);
                    return chunk;
                }));
            }
        }
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new))
                .thenApplyAsync(loaded -> snapshot(world, loads), mainThread)
                .whenCompleteAsync((blocks, error) -> {
                    for (int chunkX = firstChunkX; chunkX <= lastChunkX; chunkX++) {
                        for (int chunkZ = firstChunkZ; chunkZ <= lastChunkZ; chunkZ++) {
                            world.removePluginChunkTicket(chunkX, chunkZ, plugin);
                        }
                    }
                }, mainThread)
                .thenApplyAsync(blocks -> (view == View.TOP
                        ? Picture.surface(blocks, box.centerX(), box.centerZ(), size)
                        : Picture.side(blocks, box, view, size)).resample(Layout.MAP_PIXELS), background);
    }

    private static Blocks snapshot(World world, List<CompletableFuture<Chunk>> loads) {
        Map<Long, ChunkSnapshot> chunks = new HashMap<>();
        for (CompletableFuture<Chunk> load : loads) {
            Chunk chunk = load.join();
            chunks.put(SnapshotBlocks.key(chunk.getX(), chunk.getZ()), chunk.getChunkSnapshot(false, false, false));
        }
        return new SnapshotBlocks(world.getMinHeight(), world.getMaxHeight(), chunks);
    }
}
