package de.kylekreuter.vistructum.core.volume;

import de.kylekreuter.vistructum.core.scene.BlockPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PositionClusters {

    private static final int HORIZONTAL_OFFSET = 1 << 25;
    private static final int VERTICAL_OFFSET = 1 << 11;

    private PositionClusters() {
    }

    public static List<List<BlockPos>> of(List<BlockPos> positions, int linkDistance) {
        int count = positions.size();
        PositionIndex index = new PositionIndex(count);
        for (int i = 0; i < count; i++) {
            index.put(key(positions.get(i)), i);
        }
        int[] parent = new int[count];
        int[] size = new int[count];
        for (int i = 0; i < count; i++) {
            parent[i] = i;
            size[i] = 1;
        }
        for (int i = 0; i < count; i++) {
            BlockPos pos = positions.get(i);
            for (int dx = 0; dx <= linkDistance; dx++) {
                for (int dy = dx == 0 ? 0 : -linkDistance; dy <= linkDistance; dy++) {
                    for (int dz = dx == 0 && dy == 0 ? 1 : -linkDistance; dz <= linkDistance; dz++) {
                        int neighbor = index.get(key(pos.x() + dx, pos.y() + dy, pos.z() + dz));
                        if (neighbor >= 0) {
                            union(parent, size, i, neighbor);
                        }
                    }
                }
            }
        }
        int[] clusterOfRoot = new int[count];
        Arrays.fill(clusterOfRoot, -1);
        List<List<BlockPos>> clusters = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int root = root(parent, i);
            if (clusterOfRoot[root] < 0) {
                clusterOfRoot[root] = clusters.size();
                clusters.add(new ArrayList<>(size[root]));
            }
            clusters.get(clusterOfRoot[root]).add(positions.get(i));
        }
        return clusters;
    }

    private static long key(BlockPos pos) {
        return key(pos.x(), pos.y(), pos.z());
    }

    private static long key(int x, int y, int z) {
        return (long) (x + HORIZONTAL_OFFSET) << 38 | (long) (z + HORIZONTAL_OFFSET) << 12 | (y + VERTICAL_OFFSET);
    }

    private static int root(int[] parent, int node) {
        while (parent[node] != node) {
            parent[node] = parent[parent[node]];
            node = parent[node];
        }
        return node;
    }

    private static void union(int[] parent, int[] size, int a, int b) {
        int rootA = root(parent, a);
        int rootB = root(parent, b);
        if (rootA == rootB) {
            return;
        }
        if (size[rootA] < size[rootB]) {
            int swap = rootA;
            rootA = rootB;
            rootB = swap;
        }
        parent[rootB] = rootA;
        size[rootA] += size[rootB];
    }

    private static final class PositionIndex {

        private final long[] keys;
        private final int[] values;
        private final int mask;

        PositionIndex(int expected) {
            int capacity = Integer.highestOneBit(Math.max(4, expected * 2 - 1)) << 1;
            keys = new long[capacity];
            values = new int[capacity];
            Arrays.fill(values, -1);
            mask = capacity - 1;
        }

        void put(long key, int value) {
            int slot = slot(key);
            while (values[slot] >= 0 && keys[slot] != key) {
                slot = (slot + 1) & mask;
            }
            keys[slot] = key;
            values[slot] = value;
        }

        int get(long key) {
            int slot = slot(key);
            while (values[slot] >= 0) {
                if (keys[slot] == key) {
                    return values[slot];
                }
                slot = (slot + 1) & mask;
            }
            return -1;
        }

        private int slot(long key) {
            long mixed = key * 0x9E3779B97F4A7C15L;
            return (int) (mixed ^ (mixed >>> 32)) & mask;
        }
    }
}
