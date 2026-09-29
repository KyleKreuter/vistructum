package de.kylekreuter.vistructum.api;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Block states of a cuboid region of a world at one point in time.
 *
 * <p>Each cell holds an index into {@link #palette()}. Cells are ordered with x varying fastest, then z, then y:
 * the cell at offset {@code (dx, dy, dz)} from the minimum corner is stored at
 * {@code (dy * sizeZ + dz) * sizeX + dx}. The array is copied on construction and on every access, so a volume is
 * immutable.
 *
 * @param minX world x coordinate of the minimum corner
 * @param minY world y coordinate of the minimum corner
 * @param minZ world z coordinate of the minimum corner
 * @param sizeX number of blocks along x, at least {@code 1}
 * @param sizeY number of blocks along y, at least {@code 1}
 * @param sizeZ number of blocks along z, at least {@code 1}
 * @param palette distinct block states in the string form of {@code BlockData#getAsString()}; the list is
 *                unmodifiable
 * @param cells palette indices in the order described above; its length equals {@code sizeX * sizeY * sizeZ}
 */
public record BlockVolume(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ, List<String> palette,
                          int[] cells) {

    /**
     * Validates the components and copies the palette and the cells.
     *
     * @throws NullPointerException if {@code palette}, one of its entries or {@code cells} is {@code null}
     * @throws IllegalArgumentException if a size is smaller than {@code 1}, the cell count does not match the sizes
     *                                  or a cell is not a valid palette index
     */
    public BlockVolume {
        palette = List.copyOf(palette);
        Objects.requireNonNull(cells, "cells");
        if (sizeX < 1 || sizeY < 1 || sizeZ < 1 || (long) sizeX * sizeY * sizeZ != cells.length) {
            throw new IllegalArgumentException("volume " + sizeX + "x" + sizeY + "x" + sizeZ + " with "
                    + cells.length + " cells");
        }
        cells = cells.clone();
        for (int cell : cells) {
            Objects.checkIndex(cell, palette.size());
        }
    }

    /**
     * Returns a copy of the cell array.
     *
     * @return a new array of palette indices that the caller may modify freely
     */
    @Override
    public int[] cells() {
        return cells.clone();
    }

    /**
     * Returns the block state at a world position inside the volume.
     *
     * @param x world x coordinate
     * @param y world y coordinate
     * @param z world z coordinate
     * @return the block state in the string form of {@code BlockData#getAsString()}
     * @throws IndexOutOfBoundsException if the position lies outside the volume
     */
    public String blockAt(int x, int y, int z) {
        int dx = Objects.checkIndex(x - minX, sizeX);
        int dy = Objects.checkIndex(y - minY, sizeY);
        int dz = Objects.checkIndex(z - minZ, sizeZ);
        return palette.get(cells[(dy * sizeZ + dz) * sizeX + dx]);
    }

    /**
     * Compares the volume with another object by value, including the cell contents.
     *
     * @param other object to compare with
     * @return {@code true} if {@code other} is a volume with equal components and equal cells
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof BlockVolume that && minX == that.minX && minY == that.minY && minZ == that.minZ
                && sizeX == that.sizeX && sizeY == that.sizeY && sizeZ == that.sizeZ
                && palette.equals(that.palette) && Arrays.equals(cells, that.cells);
    }

    /**
     * Computes a hash code from the components and the cell contents.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(minX, minY, minZ, sizeX, sizeY, sizeZ, palette) * 31 + Arrays.hashCode(cells);
    }

    /**
     * Describes the position and size of the volume without listing its cells.
     *
     * @return a short description
     */
    @Override
    public String toString() {
        return "BlockVolume[" + minX + "," + minY + "," + minZ + " " + sizeX + "x" + sizeY + "x" + sizeZ + ", "
                + palette.size() + " states]";
    }
}
