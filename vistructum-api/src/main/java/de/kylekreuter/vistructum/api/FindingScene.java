package de.kylekreuter.vistructum.api;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Raster of the surroundings of a finding as the detection model saw it.
 *
 * <p>Cells are stored row by row, starting at the top-left corner. For findings of the full scan a column
 * corresponds to the world x axis and a row to the world z axis; for findings of the live check the raster is the
 * projection of the box onto the plane in which the symbol was detected. Arrays are copied on construction and on
 * every access, so a scene is immutable.
 *
 * @param source detection path that produced the finding
 * @param width number of columns, at least {@code 1}
 * @param height number of rows, at least {@code 1}
 * @param windowTop first row of the model window that scored highest
 * @param windowLeft first column of the model window that scored highest
 * @param windowBottom last row of the model window that scored highest, inclusive
 * @param windowRight last column of the model window that scored highest, inclusive
 * @param palette namespaced material keys referenced by {@code blocks}; the list is unmodifiable
 * @param blocks palette index of the top block of every cell, or {@code -1} for a cell without a block
 * @param heights height of the top block of every cell relative to the lowest cell of the scene
 * @param luminance brightness of the top block of every cell from {@code 0} to {@code 255}
 */
public record FindingScene(Source source, int width, int height, int windowTop, int windowLeft, int windowBottom,
                           int windowRight, List<String> palette, int[] blocks, int[] heights, int[] luminance) {

    /**
     * Validates the components and copies the palette and the arrays.
     *
     * @throws NullPointerException if a component or a palette entry is {@code null}
     * @throws IllegalArgumentException if a dimension is smaller than {@code 1}, an array length does not equal
     *                                  {@code width * height} or a block is neither {@code -1} nor a palette index
     */
    public FindingScene {
        Objects.requireNonNull(source, "source");
        palette = List.copyOf(palette);
        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(heights, "heights");
        Objects.requireNonNull(luminance, "luminance");
        int cells = width * height;
        if (width < 1 || height < 1 || blocks.length != cells || heights.length != cells
                || luminance.length != cells) {
            throw new IllegalArgumentException("scene " + width + "x" + height + " with arrays of "
                    + blocks.length + ", " + heights.length + " and " + luminance.length + " cells");
        }
        blocks = blocks.clone();
        heights = heights.clone();
        luminance = luminance.clone();
        for (int block : blocks) {
            if (block != -1) {
                Objects.checkIndex(block, palette.size());
            }
        }
    }

    /**
     * Returns a copy of the block array.
     *
     * @return a new array in row-major order that the caller may modify freely
     */
    @Override
    public int[] blocks() {
        return blocks.clone();
    }

    /**
     * Returns a copy of the height array.
     *
     * @return a new array in row-major order that the caller may modify freely
     */
    @Override
    public int[] heights() {
        return heights.clone();
    }

    /**
     * Returns a copy of the luminance array.
     *
     * @return a new array in row-major order that the caller may modify freely
     */
    @Override
    public int[] luminance() {
        return luminance.clone();
    }

    /**
     * Compares the scene with another object by value, including the array contents.
     *
     * @param other object to compare with
     * @return {@code true} if {@code other} is a scene with equal components and equal arrays
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof FindingScene that && source == that.source && width == that.width
                && height == that.height && windowTop == that.windowTop && windowLeft == that.windowLeft
                && windowBottom == that.windowBottom && windowRight == that.windowRight
                && palette.equals(that.palette) && Arrays.equals(blocks, that.blocks)
                && Arrays.equals(heights, that.heights) && Arrays.equals(luminance, that.luminance);
    }

    /**
     * Computes a hash code from the components and the array contents.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        int hash = Objects.hash(source, width, height, windowTop, windowLeft, windowBottom, windowRight, palette);
        hash = hash * 31 + Arrays.hashCode(blocks);
        hash = hash * 31 + Arrays.hashCode(heights);
        return hash * 31 + Arrays.hashCode(luminance);
    }

    /**
     * Describes the scene without listing its cells.
     *
     * @return a short description
     */
    @Override
    public String toString() {
        return "FindingScene[" + source + " " + width + "x" + height + ", window " + windowTop + "," + windowLeft
                + ".." + windowBottom + "," + windowRight + "]";
    }
}
