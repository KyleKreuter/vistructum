package de.kylekreuter.vistructum.core.scan;

import de.kylekreuter.vistructum.api.BlockBox;
import de.kylekreuter.vistructum.api.BlockVolume;
import de.kylekreuter.vistructum.core.region.ChunkColumn;
import de.kylekreuter.vistructum.core.region.PaletteEntry;
import de.kylekreuter.vistructum.core.region.Section;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class TerrainCut {

    static final int MARGIN = 16;
    static final int DEPTH = 8;
    static final int MAX_SIDE = 128;
    static final int MAX_HEIGHT = 128;

    private final int minY;
    private final int maxY;
    private final BlockStates states;

    public TerrainCut(int minY, int maxY, BlockStates states) {
        if (maxY < minY) {
            throw new IllegalArgumentException("height range " + minY + ".." + maxY);
        }
        this.minY = minY;
        this.maxY = maxY;
        this.states = Objects.requireNonNull(states, "states");
    }

    public Optional<BlockVolume> cut(Map<Long, ChunkColumn> columns, BlockBox focus, BlockBox bounds) {
        int[] x = span(focus.minX(), focus.maxX(), bounds.minX(), bounds.maxX());
        int[] z = span(focus.minZ(), focus.maxZ(), bounds.minZ(), bounds.maxZ());
        if (x[0] > x[1] || z[0] > z[1]) {
            return Optional.empty();
        }
        Map<Long, ChunkBlocks> chunks = new HashMap<>();
        int highestBlock = Integer.MIN_VALUE;
        int lowestSurface = Integer.MAX_VALUE;
        for (int blockZ = z[0]; blockZ <= z[1]; blockZ++) {
            for (int blockX = x[0]; blockX <= x[1]; blockX++) {
                ChunkBlocks chunk = chunk(chunks, columns, blockX, blockZ);
                int y = Math.min(maxY, chunk.top());
                while (y >= minY && chunk.at(blockX, y, blockZ).air()) {
                    y--;
                }
                if (y < minY) {
                    continue;
                }
                highestBlock = Math.max(highestBlock, y);
                while (y > minY && !states.info(chunk.at(blockX, y, blockZ)).surface()) {
                    y--;
                }
                lowestSurface = Math.min(lowestSurface, y);
            }
        }
        if (highestBlock == Integer.MIN_VALUE) {
            return Optional.empty();
        }
        int top = Math.min(maxY, Math.max(highestBlock, focus.maxY()));
        int bottom = Math.max(minY, Math.max(Math.min(lowestSurface, focus.minY()) - DEPTH, top - MAX_HEIGHT + 1));
        return Optional.of(volume(chunks, columns, x, bottom, top, z));
    }

    private static BlockVolume volume(Map<Long, ChunkBlocks> chunks, Map<Long, ChunkColumn> columns, int[] x,
                                      int bottom, int top, int[] z) {
        int sizeX = x[1] - x[0] + 1;
        int sizeY = top - bottom + 1;
        int sizeZ = z[1] - z[0] + 1;
        List<String> palette = new ArrayList<>();
        Map<PaletteEntry, Integer> indices = new HashMap<>();
        int[] cells = new int[sizeX * sizeY * sizeZ];
        for (int dz = 0; dz < sizeZ; dz++) {
            for (int dx = 0; dx < sizeX; dx++) {
                ChunkBlocks chunk = chunk(chunks, columns, x[0] + dx, z[0] + dz);
                for (int dy = 0; dy < sizeY; dy++) {
                    PaletteEntry entry = chunk.at(x[0] + dx, bottom + dy, z[0] + dz);
                    cells[(dy * sizeZ + dz) * sizeX + dx] = indices.computeIfAbsent(entry, added -> {
                        palette.add(added.state());
                        return palette.size() - 1;
                    });
                }
            }
        }
        return new BlockVolume(x[0], bottom, z[0], sizeX, sizeY, sizeZ, palette, cells);
    }

    static int[] span(int from, int to, int lowest, int highest) {
        int start = from - MARGIN;
        int end = to + MARGIN;
        if (end - start + 1 > MAX_SIDE) {
            start = Math.floorDiv(start + end, 2) - MAX_SIDE / 2 + 1;
            end = start + MAX_SIDE - 1;
        }
        return new int[]{Math.max(lowest, start), Math.min(highest, end)};
    }

    private static ChunkBlocks chunk(Map<Long, ChunkBlocks> chunks, Map<Long, ChunkColumn> columns, int x, int z) {
        return chunks.computeIfAbsent(ChunkKey.of(x >> 4, z >> 4), key -> new ChunkBlocks(columns.get(key)));
    }

    private static final class ChunkBlocks {

        private final Map<Integer, Section> sections = new HashMap<>();
        private final Map<Integer, int[]> decoded = new HashMap<>();
        private final int top;

        ChunkBlocks(ChunkColumn column) {
            int highest = Integer.MIN_VALUE;
            if (column != null) {
                for (Section section : column.sections()) {
                    sections.put(section.y(), section);
                    if (section.palette().stream().anyMatch(entry -> !entry.air())) {
                        highest = Math.max(highest, section.y() * Section.SIDE + Section.SIDE - 1);
                    }
                }
            }
            top = highest;
        }

        int top() {
            return top;
        }

        PaletteEntry at(int x, int y, int z) {
            int sectionY = Math.floorDiv(y, Section.SIDE);
            Section section = sections.get(sectionY);
            if (section == null) {
                return PaletteEntry.AIR;
            }
            if (section.palette().size() == 1) {
                return section.palette().getFirst();
            }
            int[] indices = decoded.computeIfAbsent(sectionY, key -> section.indices());
            return section.palette().get(indices[Section.index(Math.floorMod(x, Section.SIDE),
                    Math.floorMod(y, Section.SIDE), Math.floorMod(z, Section.SIDE))]);
        }
    }
}
