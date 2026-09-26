package de.kylekreuter.vistructum.core.volume;

import de.kylekreuter.vistructum.core.scene.BlockPos;

import java.util.ArrayList;
import java.util.List;

public final class PositionClusters {

    private PositionClusters() {
    }

    public static List<List<BlockPos>> of(List<BlockPos> positions, int linkDistance) {
        if (linkDistance < 1 || linkDistance > 31) {
            throw new IllegalArgumentException("linkDistance must be in [1, 31], got " + linkDistance);
        }
        if (positions.isEmpty()) {
            return List.of();
        }
        Grid grid = new Grid(positions);
        for (BlockPos pos : positions) {
            grid.set(grid.index(pos));
        }
        int[] queue = new int[positions.size()];
        List<List<BlockPos>> clusters = new ArrayList<>();
        for (BlockPos seed : positions) {
            int start = grid.index(seed);
            if (!grid.test(start)) {
                continue;
            }
            grid.clear(start);
            queue[0] = start;
            int head = 0;
            int tail = 1;
            while (head < tail) {
                tail = grid.takeNeighbors(queue[head++], linkDistance, queue, tail);
            }
            List<BlockPos> cluster = new ArrayList<>(tail);
            for (int i = 0; i < tail; i++) {
                cluster.add(grid.position(queue[i]));
            }
            clusters.add(cluster);
        }
        return clusters;
    }

    private static final class Grid {

        private final int minX;
        private final int minY;
        private final int minZ;
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final long[] bits;

        Grid(List<BlockPos> positions) {
            int lowX = Integer.MAX_VALUE, lowY = Integer.MAX_VALUE, lowZ = Integer.MAX_VALUE;
            int highX = Integer.MIN_VALUE, highY = Integer.MIN_VALUE, highZ = Integer.MIN_VALUE;
            for (BlockPos pos : positions) {
                lowX = Math.min(lowX, pos.x());
                lowY = Math.min(lowY, pos.y());
                lowZ = Math.min(lowZ, pos.z());
                highX = Math.max(highX, pos.x());
                highY = Math.max(highY, pos.y());
                highZ = Math.max(highZ, pos.z());
            }
            minX = lowX;
            minY = lowY;
            minZ = lowZ;
            sizeX = highX - lowX + 1;
            sizeY = highY - lowY + 1;
            sizeZ = highZ - lowZ + 1;
            long volume = (long) sizeX * sizeY * sizeZ;
            if (volume > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("positions span " + volume + " blocks");
            }
            bits = new long[(int) ((volume + 63) >>> 6)];
        }

        int index(BlockPos pos) {
            return ((pos.y() - minY) * sizeZ + (pos.z() - minZ)) * sizeX + (pos.x() - minX);
        }

        BlockPos position(int index) {
            int x = index % sizeX;
            int rest = index / sizeX;
            return new BlockPos(minX + x, minY + rest / sizeZ, minZ + rest % sizeZ);
        }

        void set(int index) {
            bits[index >>> 6] |= 1L << index;
        }

        boolean test(int index) {
            return (bits[index >>> 6] & (1L << index)) != 0;
        }

        void clear(int index) {
            bits[index >>> 6] &= ~(1L << index);
        }

        int takeNeighbors(int index, int distance, int[] queue, int tail) {
            int x = index % sizeX;
            int rest = index / sizeX;
            int y = rest / sizeZ;
            int z = rest % sizeZ;
            int fromX = Math.max(0, x - distance);
            int width = Math.min(sizeX - 1, x + distance) - fromX + 1;
            long window = (1L << width) - 1;
            for (int ny = Math.max(0, y - distance); ny <= Math.min(sizeY - 1, y + distance); ny++) {
                for (int nz = Math.max(0, z - distance); nz <= Math.min(sizeZ - 1, z + distance); nz++) {
                    int start = (ny * sizeZ + nz) * sizeX + fromX;
                    int word = start >>> 6;
                    int offset = start & 63;
                    long found = bits[word] >>> offset;
                    if (offset + width > 64) {
                        found |= bits[word + 1] << (64 - offset);
                    }
                    found &= window;
                    while (found != 0) {
                        int neighbor = start + Long.numberOfTrailingZeros(found);
                        found &= found - 1;
                        clear(neighbor);
                        queue[tail++] = neighbor;
                    }
                }
            }
            return tail;
        }
    }
}
