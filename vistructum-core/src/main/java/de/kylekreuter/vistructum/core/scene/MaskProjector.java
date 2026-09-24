package de.kylekreuter.vistructum.core.scene;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * turns a cluster of a player's recently modified block positions into up to three 2D binary masks, one per
 * {@link Axis}, so a swastika built flat on the ground and one built on a wall are both visible to the mask model.
 */
public final class MaskProjector {

    private final int margin;

    public MaskProjector() {
        this(2);
    }

    /** @param margin empty cells of border added around the cluster's bounding box on each side of every raster */
    public MaskProjector(int margin) {
        if (margin < 0) {
            throw new IllegalArgumentException("margin must be >= 0, got " + margin);
        }
        this.margin = margin;
    }

    /** one {@link Projection} per axis whose raster fits within {@link SurfaceScene#MAX_SIDE}; empty if none do. */
    public List<Projection> project(Collection<BlockPos> positions) {
        if (positions.isEmpty()) {
            return List.of();
        }
        WorldBox bounds = boundsOf(positions);
        List<Projection> projections = new ArrayList<>(Axis.values().length);
        for (Axis axis : Axis.values()) {
            Projection projection = project(axis, positions, bounds);
            if (projection != null) {
                projections.add(projection);
            }
        }
        return projections;
    }

    private Projection project(Axis axis, Collection<BlockPos> positions, WorldBox bounds) {
        int width = colSpan(axis, bounds) + 2 * margin;
        int height = rowSpan(axis, bounds) + 2 * margin;
        if (width > SurfaceScene.MAX_SIDE || height > SurfaceScene.MAX_SIDE) {
            return null;
        }
        byte[] modified = new byte[width * height];
        for (BlockPos pos : positions) {
            int row = row(axis, pos, bounds) + margin;
            int col = col(axis, pos, bounds) + margin;
            modified[row * width + col] = 1;
        }
        SurfaceScene scene = SurfaceScene.maskOnly(width, height, modified);
        return new Projection(axis, scene, bounds, margin);
    }

    private static int row(Axis axis, BlockPos pos, WorldBox bounds) {
        return switch (axis) {
            case Y -> pos.z() - bounds.minZ();
            case Z, X -> bounds.maxY() - pos.y();
        };
    }

    private static int col(Axis axis, BlockPos pos, WorldBox bounds) {
        return switch (axis) {
            case Y, Z -> pos.x() - bounds.minX();
            case X -> pos.z() - bounds.minZ();
        };
    }

    private static int rowSpan(Axis axis, WorldBox bounds) {
        return switch (axis) {
            case Y -> bounds.maxZ() - bounds.minZ() + 1;
            case Z, X -> bounds.maxY() - bounds.minY() + 1;
        };
    }

    private static int colSpan(Axis axis, WorldBox bounds) {
        return switch (axis) {
            case Y, Z -> bounds.maxX() - bounds.minX() + 1;
            case X -> bounds.maxZ() - bounds.minZ() + 1;
        };
    }

    private static WorldBox boundsOf(Collection<BlockPos> positions) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : positions) {
            minX = Math.min(minX, pos.x());
            minY = Math.min(minY, pos.y());
            minZ = Math.min(minZ, pos.z());
            maxX = Math.max(maxX, pos.x());
            maxY = Math.max(maxY, pos.y());
            maxZ = Math.max(maxZ, pos.z());
        }
        return new WorldBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
