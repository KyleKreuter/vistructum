package de.kylekreuter.vistructum.core.scene;

/**
 * one {@link Axis} view of a block cluster: a modification-only {@link SurfaceScene} plus what is needed to map a
 * detection back from raster coordinates into the world.
 *
 * @param bounds the cluster's real, unpadded world bounding box (inclusive)
 * @param margin the empty border, in blocks, that {@code scene} carries around {@code bounds} on that axis' view
 */
public record Projection(Axis axis, SurfaceScene scene, WorldBox bounds, int margin) {

    /**
     * maps a detection box in raster coordinates ({@code bottom}/{@code right} exclusive, as the sidecar returns
     * them) back to an inclusive world bounding box. The collapsed axis spans the cluster's full extent. The box is
     * clipped to the raster, and margin cells that map outside the cluster are clamped back onto it.
     */
    public WorldBox toWorld(int top, int left, int bottom, int right) {
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
                yield new WorldBox(minX, bounds.minY(), minZ, maxX, bounds.maxY(), maxZ);
            }
            case Z -> {
                int maxY = clamp(bounds.maxY() + margin - rowLo, bounds.minY(), bounds.maxY());
                int minY = clamp(bounds.maxY() + margin - rowHi, bounds.minY(), bounds.maxY());
                int minX = clamp(bounds.minX() - margin + colLo, bounds.minX(), bounds.maxX());
                int maxX = clamp(bounds.minX() - margin + colHi, bounds.minX(), bounds.maxX());
                yield new WorldBox(minX, minY, bounds.minZ(), maxX, maxY, bounds.maxZ());
            }
            case X -> {
                int maxY = clamp(bounds.maxY() + margin - rowLo, bounds.minY(), bounds.maxY());
                int minY = clamp(bounds.maxY() + margin - rowHi, bounds.minY(), bounds.maxY());
                int minZ = clamp(bounds.minZ() - margin + colLo, bounds.minZ(), bounds.maxZ());
                int maxZ = clamp(bounds.minZ() - margin + colHi, bounds.minZ(), bounds.maxZ());
                yield new WorldBox(bounds.minX(), minY, minZ, bounds.maxX(), maxY, maxZ);
            }
        };
    }

    private static int clamp(int value, int lo, int hi) {
        return Math.max(lo, Math.min(hi, value));
    }
}
