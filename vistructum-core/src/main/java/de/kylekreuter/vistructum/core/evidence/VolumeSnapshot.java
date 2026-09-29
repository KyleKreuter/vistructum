package de.kylekreuter.vistructum.core.evidence;

import de.kylekreuter.vistructum.api.BlockBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record VolumeSnapshot(BlockBox region, List<String> palette, int[] cells) {

    public VolumeSnapshot {
        Objects.requireNonNull(region, "region");
        palette = List.copyOf(palette);
        Objects.requireNonNull(cells, "cells");
        if (cells.length != volume(region)) {
            throw new IllegalArgumentException("snapshot of " + region + " with " + cells.length + " cells");
        }
    }

    public static long volume(BlockBox region) {
        return (long) (region.maxX() - region.minX() + 1) * (region.maxY() - region.minY() + 1)
                * (region.maxZ() - region.minZ() + 1);
    }

    public int sizeX() {
        return region.maxX() - region.minX() + 1;
    }

    public int sizeY() {
        return region.maxY() - region.minY() + 1;
    }

    public int sizeZ() {
        return region.maxZ() - region.minZ() + 1;
    }

    public int index(int x, int y, int z) {
        return ((y - region.minY()) * sizeZ() + z - region.minZ()) * sizeX() + x - region.minX();
    }

    public boolean contains(int x, int y, int z) {
        return x >= region.minX() && x <= region.maxX() && y >= region.minY() && y <= region.maxY()
                && z >= region.minZ() && z <= region.maxZ();
    }

    public static final class Builder {

        private final BlockBox region;
        private final List<String> palette = new ArrayList<>();
        private final Map<String, Integer> indices = new HashMap<>();
        private final int[] cells;

        public Builder(BlockBox region) {
            this.region = Objects.requireNonNull(region, "region");
            this.cells = new int[Math.toIntExact(volume(region))];
        }

        public Builder set(int index, String blockData) {
            cells[index] = indices.computeIfAbsent(blockData, added -> {
                palette.add(added);
                return palette.size() - 1;
            });
            return this;
        }

        public VolumeSnapshot build() {
            return new VolumeSnapshot(region, palette, cells);
        }
    }
}
