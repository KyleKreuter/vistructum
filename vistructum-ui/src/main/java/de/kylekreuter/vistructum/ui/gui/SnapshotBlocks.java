package de.kylekreuter.vistructum.ui.gui;

import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;

record SnapshotBlocks(int minHeight, int maxHeight, Map<Long, ChunkSnapshot> chunks) implements Blocks {

    private static final int SECTION = 16;
    private static final EnumSet<Material> AIR = EnumSet.of(Material.AIR, Material.CAVE_AIR, Material.VOID_AIR);

    SnapshotBlocks {
        chunks = Map.copyOf(Objects.requireNonNull(chunks, "chunks"));
    }

    static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    @Override
    public int surfaceY(int x, int z) {
        ChunkSnapshot chunk = chunk(x, z);
        if (chunk == null) {
            return minHeight - 1;
        }
        for (int section = (maxHeight - minHeight) / SECTION - 1; section >= 0; section--) {
            if (chunk.isSectionEmpty(section)) {
                continue;
            }
            int bottom = minHeight + section * SECTION;
            for (int y = bottom + SECTION - 1; y >= bottom; y--) {
                if (!AIR.contains(chunk.getBlockType(x & 15, y, z & 15))) {
                    return y;
                }
            }
        }
        return minHeight - 1;
    }

    @Override
    public boolean isEmpty(int x, int y, int z) {
        ChunkSnapshot chunk = chunk(x, z);
        return chunk == null || y < minHeight || y >= maxHeight || AIR.contains(chunk.getBlockType(x & 15, y, z & 15));
    }

    @Override
    public int mapColor(int x, int y, int z) {
        if (isEmpty(x, y, z)) {
            return 0;
        }
        return chunk(x, z).getBlockData(x & 15, y, z & 15).getMapColor().asRGB();
    }

    private ChunkSnapshot chunk(int x, int z) {
        return chunks.get(key(x >> 4, z >> 4));
    }
}
