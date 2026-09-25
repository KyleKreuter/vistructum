package de.kylekreuter.vistructum.ui.gui;

import de.kylekreuter.vistructum.api.BlockBox;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

final class Scene {

    private static final int MARGIN = 16;
    private static final int WELL = 0x2E2E2E;

    private Scene() {
    }

    static Picture empty() {
        return Picture.solid(Layout.MAP_PIXELS, WELL);
    }

    static CompletableFuture<Picture> capture(Plugin plugin, World world, BlockBox box) {
        int size = Math.max(Layout.MAP_PIXELS, Math.max(box.maxX() - box.minX(), box.maxZ() - box.minZ()) + 1 + MARGIN);
        int left = box.centerX() - size / 2;
        int top = box.centerZ() - size / 2;
        int firstChunkX = left >> 4;
        int lastChunkX = (left + size - 1) >> 4;
        int firstChunkZ = (top - 1) >> 4;
        int lastChunkZ = (top + size - 1) >> 4;
        List<CompletableFuture<?>> loads = new ArrayList<>();
        for (int chunkX = firstChunkX; chunkX <= lastChunkX; chunkX++) {
            for (int chunkZ = firstChunkZ; chunkZ <= lastChunkZ; chunkZ++) {
                loads.add(world.getChunkAtAsync(chunkX, chunkZ).thenAccept(chunk -> chunk.addPluginChunkTicket(plugin)));
            }
        }
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new))
                .thenApply(loaded -> Picture.surface(world, box.centerX(), box.centerZ(), size).resample(Layout.MAP_PIXELS))
                .whenComplete((picture, error) -> {
                    for (int chunkX = firstChunkX; chunkX <= lastChunkX; chunkX++) {
                        for (int chunkZ = firstChunkZ; chunkZ <= lastChunkZ; chunkZ++) {
                            world.removePluginChunkTicket(chunkX, chunkZ, plugin);
                        }
                    }
                });
    }
}
