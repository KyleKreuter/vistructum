package de.kylekreuter.vistructum.api;

/**
 * Axis-aligned box of block positions in world coordinates.
 *
 * <p>All bounds are inclusive. A box therefore contains at least one block, and a box whose minimum equals its
 * maximum on every axis denotes a single block.
 *
 * @param minX smallest block x coordinate, inclusive
 * @param minY smallest block y coordinate, inclusive
 * @param minZ smallest block z coordinate, inclusive
 * @param maxX largest block x coordinate, inclusive
 * @param maxY largest block y coordinate, inclusive
 * @param maxZ largest block z coordinate, inclusive
 */
public record BlockBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    /**
     * Validates the bounds.
     *
     * @throws IllegalArgumentException if a minimum exceeds the corresponding maximum
     */
    public BlockBox {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("inverted box " + minX + "," + minY + "," + minZ + " .. "
                    + maxX + "," + maxY + "," + maxZ);
        }
    }

    /**
     * Returns the block x coordinate at the horizontal centre of the box.
     *
     * @return the midpoint of {@link #minX()} and {@link #maxX()}, rounded towards negative infinity
     */
    public int centerX() {
        return Math.floorDiv(minX + maxX, 2);
    }

    /**
     * Returns the block z coordinate at the horizontal centre of the box.
     *
     * @return the midpoint of {@link #minZ()} and {@link #maxZ()}, rounded towards negative infinity
     */
    public int centerZ() {
        return Math.floorDiv(minZ + maxZ, 2);
    }

    /**
     * Tests whether this box and another box share at least one block position.
     *
     * @param other box to test against
     * @return {@code true} if the boxes intersect on all three axes, including boxes that touch at a boundary
     *         block
     * @throws NullPointerException if {@code other} is {@code null}
     */
    public boolean overlaps(BlockBox other) {
        return minX <= other.maxX && other.minX <= maxX
                && minY <= other.maxY && other.minY <= maxY
                && minZ <= other.maxZ && other.minZ <= maxZ;
    }
}
