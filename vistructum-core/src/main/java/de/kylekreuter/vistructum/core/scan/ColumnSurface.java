package de.kylekreuter.vistructum.core.scan;

import de.kylekreuter.vistructum.core.region.ChunkColumn;
import de.kylekreuter.vistructum.core.region.PaletteEntry;
import de.kylekreuter.vistructum.core.region.Section;
import de.kylekreuter.vistructum.inference.SurfaceScene;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ColumnSurface {

    private static final int HEIGHTMAP_BITS = 9;
    private static final int HEIGHTMAP_PER_LONG = Long.SIZE / HEIGHTMAP_BITS;

    private final int minY;
    private final BlockStates states;

    public ColumnSurface(int minY, BlockStates states) {
        this.minY = minY;
        this.states = states;
    }

    public SurfaceScene sample(Map<Long, ChunkColumn> columns, int originX, int originZ, int width, int height) {
        int count = width * height;
        short[] blocks = new short[count];
        short[] heights = new short[count];
        byte[] luminance = new byte[count];
        Arrays.fill(blocks, SurfaceScene.UNKNOWN);
        Map<PaletteEntry, BlockStates.StateInfo> infos = new HashMap<>();
        for (int chunkX = originX >> 4; chunkX <= (originX + width - 1) >> 4; chunkX++) {
            for (int chunkZ = originZ >> 4; chunkZ <= (originZ + height - 1) >> 4; chunkZ++) {
                ChunkColumn column = columns.get(ChunkKey.of(chunkX, chunkZ));
                if (column != null) {
                    sampleChunk(column, infos, originX, originZ, width, height, blocks, heights, luminance);
                }
            }
        }
        return new SurfaceScene(width, height, blocks, heights, luminance, new byte[count]);
    }

    private void sampleChunk(ChunkColumn column, Map<PaletteEntry, BlockStates.StateInfo> infos, int originX,
                             int originZ, int width, int height, short[] blocks, short[] heights, byte[] luminance) {
        Map<Integer, Section> sections = new HashMap<>();
        int top = Integer.MIN_VALUE;
        for (Section section : column.sections()) {
            sections.put(section.y(), section);
            top = Math.max(top, section.y() * Section.SIDE + Section.SIDE - 1);
        }
        Map<Integer, int[]> decoded = new HashMap<>();
        for (int localZ = 0; localZ < Section.SIDE; localZ++) {
            int row = (column.chunkZ() << 4) + localZ - originZ;
            if (row < 0 || row >= height) {
                continue;
            }
            for (int localX = 0; localX < Section.SIDE; localX++) {
                int col = (column.chunkX() << 4) + localX - originX;
                if (col < 0 || col >= width) {
                    continue;
                }
                int y = start(column, localX, localZ, top);
                BlockStates.StateInfo info = infoAt(sections, decoded, infos, localX, y, localZ);
                while (y > minY && !info.surface()) {
                    y--;
                    info = infoAt(sections, decoded, infos, localX, y, localZ);
                }
                int index = row * width + col;
                blocks[index] = info.material();
                heights[index] = (short) y;
                luminance[index] = (byte) info.luminance();
            }
        }
    }

    private int start(ChunkColumn column, int localX, int localZ, int top) {
        if (column.motionBlocking().isEmpty()) {
            return Math.max(minY, top);
        }
        long[] heightmap = column.motionBlocking().get();
        int i = localZ * Section.SIDE + localX;
        if (i / HEIGHTMAP_PER_LONG >= heightmap.length) {
            return Math.max(minY, top);
        }
        long value = (heightmap[i / HEIGHTMAP_PER_LONG] >>> ((i % HEIGHTMAP_PER_LONG) * HEIGHTMAP_BITS))
                & ((1L << HEIGHTMAP_BITS) - 1);
        return Math.max(minY, minY + (int) value - 1);
    }

    private BlockStates.StateInfo infoAt(Map<Integer, Section> sections, Map<Integer, int[]> decoded,
                                         Map<PaletteEntry, BlockStates.StateInfo> infos, int x, int y, int z) {
        int sectionY = Math.floorDiv(y, Section.SIDE);
        Section section = sections.get(sectionY);
        PaletteEntry entry;
        if (section == null) {
            entry = PaletteEntry.AIR;
        } else {
            List<PaletteEntry> palette = section.palette();
            int[] indices = palette.size() == 1 ? null : decoded.computeIfAbsent(sectionY, key -> section.indices());
            entry = palette.get(indices == null ? 0 : indices[Section.index(x, Math.floorMod(y, Section.SIDE), z)]);
        }
        return infos.computeIfAbsent(entry, states::info);
    }
}
