package de.kylekreuter.vistructum.api;

import java.util.Arrays;
import java.util.Objects;

/**
 * Colour image of the surroundings of a finding, rendered from the world for display to reviewers.
 *
 * <p>Pixels are stored row by row, starting at the top-left corner. Each pixel is an RGB colour in the form
 * {@code 0xRRGGBB}; bits above the lowest 24 are cleared on construction. The array is copied on construction and on
 * every access, so a thumbnail is immutable.
 *
 * @param width number of columns, at least {@code 1}
 * @param height number of rows, at least {@code 1}
 * @param pixels colours in row-major order; its length equals {@code width * height}
 */
public record Thumbnail(int width, int height, int[] pixels) {

    /**
     * Validates the dimensions and copies the pixel array.
     *
     * @throws NullPointerException if {@code pixels} is {@code null}
     * @throws IllegalArgumentException if a dimension is smaller than {@code 1} or the array length does not
     *                                  equal {@code width * height}
     */
    public Thumbnail {
        Objects.requireNonNull(pixels, "pixels");
        if (width < 1 || height < 1 || pixels.length != width * height) {
            throw new IllegalArgumentException("thumbnail " + width + "x" + height + " with " + pixels.length
                    + " pixels");
        }
        pixels = pixels.clone();
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] &= 0xFFFFFF;
        }
    }

    /**
     * Returns a copy of the pixel array.
     *
     * @return a new array in row-major order that the caller may modify freely
     */
    @Override
    public int[] pixels() {
        return pixels.clone();
    }

    /**
     * Returns the colour of one pixel.
     *
     * @param row zero-based row, counted from the top
     * @param col zero-based column, counted from the left
     * @return the colour in the form {@code 0xRRGGBB}
     * @throws IndexOutOfBoundsException if {@code row} is outside {@code 0} to {@code height - 1} or {@code col}
     *                                   is outside {@code 0} to {@code width - 1}
     */
    public int rgb(int row, int col) {
        return pixels[Objects.checkIndex(row, height) * width + Objects.checkIndex(col, width)];
    }

    /**
     * Compares the dimensions and all pixels.
     *
     * @param other object to compare with
     * @return {@code true} if {@code other} is a thumbnail of the same size with the same colours
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof Thumbnail that && width == that.width && height == that.height
                && Arrays.equals(pixels, that.pixels);
    }

    /**
     * Derives a hash code from the dimensions and all pixels.
     *
     * @return a hash code consistent with {@link #equals(Object)}
     */
    @Override
    public int hashCode() {
        return 31 * (31 * width + height) + Arrays.hashCode(pixels);
    }

    /**
     * Describes the dimensions without listing the pixels.
     *
     * @return a short description of the thumbnail
     */
    @Override
    public String toString() {
        return "Thumbnail[" + width + "x" + height + "]";
    }
}
