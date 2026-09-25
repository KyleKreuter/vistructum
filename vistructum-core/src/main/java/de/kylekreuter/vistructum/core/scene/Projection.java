package de.kylekreuter.vistructum.core.scene;

import de.kylekreuter.vistructum.api.BlockBox;
import de.kylekreuter.vistructum.inference.SurfaceScene;

public record Projection(Axis axis, SurfaceScene scene, BlockBox bounds, int margin) {

    public BlockBox toWorld(int top, int left, int bottom, int right) {
        int t = clamp(top, 0, scene.height());
        int l = clamp(left, 0, scene.width());
        int b = clamp(bottom, 0, scene.height());
        int r = clamp(right, 0, scene.width());
        if (b <= t || r <= l) {
            throw new IllegalArgumentException("empty raster box [" + top + "," + left + "," + bottom + "," + right + "]");
        }
        int rowLo = t;
        int rowHi = b - 1;
        int colLo = l;
        int colHi = r - 1;

        return switch (axis) {
            case Y -> {
                int minZ = clamp(bounds.minZ() - margin + rowLo, bounds.minZ(), bounds.maxZ());
                int maxZ = clamp(bounds.minZ() - margin + rowHi, bounds.minZ(), bounds.maxZ());
                int minX = clamp(bounds.minX() - margin + colLo, bounds.minX(), bounds.maxX());
                int maxX = clamp(bounds.minX() - margin + colHi, bounds.minX(), bounds.maxX());
                yield new BlockBox(minX, bounds.minY(), minZ, maxX, bounds.maxY(), maxZ);
            }
            case Z -> {
                int maxY = clamp(bounds.maxY() + margin - rowLo, bounds.minY(), bounds.maxY());
                int minY = clamp(bounds.maxY() + margin - rowHi, bounds.minY(), bounds.maxY());
                int minX = clamp(bounds.minX() - margin + colLo, bounds.minX(), bounds.maxX());
                int maxX = clamp(bounds.minX() - margin + colHi, bounds.minX(), bounds.maxX());
                yield new BlockBox(minX, minY, bounds.minZ(), maxX, maxY, bounds.maxZ());
            }
            case X -> {
                int maxY = clamp(bounds.maxY() + margin - rowLo, bounds.minY(), bounds.maxY());
                int minY = clamp(bounds.maxY() + margin - rowHi, bounds.minY(), bounds.maxY());
                int minZ = clamp(bounds.minZ() - margin + colLo, bounds.minZ(), bounds.maxZ());
                int maxZ = clamp(bounds.minZ() - margin + colHi, bounds.minZ(), bounds.maxZ());
                yield new BlockBox(bounds.minX(), minY, minZ, bounds.maxX(), maxY, maxZ);
            }
        };
    }

    private static int clamp(int value, int lo, int hi) {
        return Math.max(lo, Math.min(hi, value));
    }
}
