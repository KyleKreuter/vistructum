package de.kylekreuter.vistructum.core.scan;

import de.kylekreuter.vistructum.core.scene.Luminance;
import de.kylekreuter.vistructum.core.scene.SurfaceScene;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;

import java.util.Map;

/**
 * Reads the top-down surface of a rectangle from chunk snapshots; safe off the main thread.
 *
 * <p>The surface is the MOTION_BLOCKING one the models were trained on: the highest block that blocks movement or
 * holds a fluid, so leaves and water count while grass, flowers, torches and thin snow do not. Columns in chunks
 * without a snapshot are {@link SurfaceScene#UNKNOWN}.
 */
public final class SurfaceSampler {

    private final int minY;

    public SurfaceSampler(int minY) {
        this.minY = minY;
    }

    /** @param snapshots keyed by {@link #chunkKey(int, int)} */
    public SurfaceScene sample(Map<Long, ChunkSnapshot> snapshots, int originX, int originZ, int width, int height) {
        int count = width * height;
        short[] blocks = new short[count];
        short[] heights = new short[count];
        byte[] luminance = new byte[count];
        for (int row = 0; row < height; row++) {
            int z = originZ + row;
            for (int col = 0; col < width; col++) {
                int x = originX + col;
                int index = row * width + col;
                ChunkSnapshot snapshot = snapshots.get(chunkKey(x >> 4, z >> 4));
                if (snapshot == null) {
                    blocks[index] = SurfaceScene.UNKNOWN;
                    continue;
                }
                int y = snapshot.getHighestBlockYAt(x & 15, z & 15);
                BlockData data = snapshot.getBlockData(x & 15, y, z & 15);
                while (y > minY && !isSurface(data)) {
                    y--;
                    data = snapshot.getBlockData(x & 15, y, z & 15);
                }
                blocks[index] = (short) data.getMaterial().ordinal();
                heights[index] = (short) y;
                luminance[index] = (byte) Luminance.of(data.getMapColor());
            }
        }
        return new SurfaceScene(width, height, blocks, heights, luminance, new byte[count]);
    }

    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xffffffffL);
    }

    static boolean isSurface(BlockData data) {
        Material material = data.getMaterial();
        return material.isSolid() || material == Material.WATER || material == Material.LAVA
                || data instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
    }
}
