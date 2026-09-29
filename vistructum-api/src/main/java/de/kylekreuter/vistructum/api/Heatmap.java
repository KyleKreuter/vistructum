package de.kylekreuter.vistructum.api;

import java.util.Arrays;
import java.util.Objects;

/**
 * Map of the cells of a {@link FindingScene} that the detection model relied on.
 *
 * <p>The heatmap covers the scene cell by cell in the same row-major order. A value is the drop of the model score
 * when the cell is hidden, scaled from {@code 0} (no influence) to {@code 255} (the score falls from its maximum to
 * zero). Cells outside the model window are {@code 0}. The array is copied on construction and on every access.
 *
 * @param width number of columns, equal to the width of the scene
 * @param height number of rows, equal to the height of the scene
 * @param values influence of every cell from {@code 0} to {@code 255}; its length equals {@code width * height}
 */
public record Heatmap(int width, int height, int[] values) {

    /**
     * Validates the dimensions and copies the values.
     *
     * @throws NullPointerException if {@code values} is {@code null}
     * @throws IllegalArgumentException if a dimension is smaller than {@code 1}, the array length does not equal
     *                                  {@code width * height} or a value lies outside {@code 0} to {@code 255}
     */
    public Heatmap {
        Objects.requireNonNull(values, "values");
        if (width < 1 || height < 1 || values.length != width * height) {
            throw new IllegalArgumentException("heatmap " + width + "x" + height + " with " + values.length
                    + " values");
        }
        values = values.clone();
        for (int value : values) {
            if (value < 0 || value > 255) {
                throw new IllegalArgumentException("heatmap value " + value);
            }
        }
    }

    /**
     * Returns a copy of the values.
     *
     * @return a new array in row-major order that the caller may modify freely
     */
    @Override
    public int[] values() {
        return values.clone();
    }

    /**
     * Compares the heatmap with another object by value, including the array contents.
     *
     * @param other object to compare with
     * @return {@code true} if {@code other} is a heatmap with equal dimensions and equal values
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof Heatmap that && width == that.width && height == that.height
                && Arrays.equals(values, that.values);
    }

    /**
     * Computes a hash code from the dimensions and the values.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return (width * 31 + height) * 31 + Arrays.hashCode(values);
    }

    /**
     * Describes the dimensions without listing the values.
     *
     * @return a short description
     */
    @Override
    public String toString() {
        return "Heatmap[" + width + "x" + height + "]";
    }
}
