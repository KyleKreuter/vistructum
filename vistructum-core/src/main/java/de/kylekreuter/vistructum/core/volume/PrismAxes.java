package de.kylekreuter.vistructum.core.volume;

import de.kylekreuter.vistructum.core.scene.Axis;
import de.kylekreuter.vistructum.core.scene.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PrismAxes {

    private final double minFill;
    private final int minSide;

    public PrismAxes(double minFill, int minSide) {
        if (minFill <= 0 || minFill > 1) {
            throw new IllegalArgumentException("minFill must be in (0, 1], got " + minFill);
        }
        if (minSide < 1) {
            throw new IllegalArgumentException("minSide must be >= 1, got " + minSide);
        }
        this.minFill = minFill;
        this.minSide = minSide;
    }

    public List<Axis> of(List<BlockPos> cluster) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : cluster) {
            minX = Math.min(minX, pos.x());
            minY = Math.min(minY, pos.y());
            minZ = Math.min(minZ, pos.z());
            maxX = Math.max(maxX, pos.x());
            maxY = Math.max(maxY, pos.y());
            maxZ = Math.max(maxZ, pos.z());
        }
        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;
        List<Axis> axes = new ArrayList<>(Axis.values().length);
        for (Axis axis : Axis.values()) {
            int depth = switch (axis) {
                case X -> sizeX;
                case Y -> sizeY;
                case Z -> sizeZ;
            };
            int width = axis == Axis.X ? sizeZ : sizeX;
            int height = axis == Axis.Y ? sizeZ : sizeY;
            if (Math.min(width, height) < minSide) {
                continue;
            }
            if (cluster.size() >= minFill * footprint(cluster, axis) * depth) {
                axes.add(axis);
            }
        }
        return axes;
    }

    private static int footprint(List<BlockPos> cluster, Axis axis) {
        Set<Long> cells = new HashSet<>(cluster.size() * 2);
        for (BlockPos pos : cluster) {
            long a = axis == Axis.X ? pos.z() : pos.x();
            long b = axis == Axis.Y ? pos.z() : pos.y();
            cells.add(a << 32 | (b & 0xFFFFFFFFL));
        }
        return cells.size();
    }
}
