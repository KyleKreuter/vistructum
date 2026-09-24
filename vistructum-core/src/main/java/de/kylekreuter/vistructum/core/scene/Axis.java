package de.kylekreuter.vistructum.core.scene;

/**
 * a 2D view of a 3D block cluster that {@link MaskProjector} rasterises.
 *
 * <p>Side views ({@link #Z}, {@link #X}) run rows from the highest y down, so a wall reads top to bottom like a
 * picture instead of upside down.
 */
public enum Axis {

    /** top-down: row = z - minZ, col = x - minX; y is collapsed. */
    Y,

    /** a wall in the x-y plane, viewed along z: row = maxY - y, col = x - minX; z is collapsed. */
    Z,

    /** a wall in the z-y plane, viewed along x: row = maxY - y, col = z - minZ; x is collapsed. */
    X
}
