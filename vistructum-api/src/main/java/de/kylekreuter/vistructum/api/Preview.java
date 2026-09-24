package de.kylekreuter.vistructum.api;

import java.util.Objects;

/**
 * Greyscale raster image of the region around a detection.
 *
 * <p>Pixels are stored row by row, starting at the top-left corner. Each pixel is an unsigned 8-bit intensity
 * from {@code 0} (black) to {@code 255} (white). The array is copied on construction and on every access, so a
 * preview is immutable.
 *
 * @param width number of columns, at least {@code 1}
 * @param height number of rows, at least {@code 1}
 * @param pixels intensities in row-major order; its length equals {@code width * height}
 */
public record Preview(int width, int height, byte[] pixels) {

    /**
     * Validates the dimensions and copies the pixel array.
     *
     * @throws NullPointerException if {@code pixels} is {@code null}
     * @throws IllegalArgumentException if a dimension is smaller than {@code 1} or the array length does not
     *                                  equal {@code width * height}
     */
    public Preview {
        Objects.requireNonNull(pixels, "pixels");
        if (width < 1 || height < 1 || pixels.length != width * height) {
            throw new IllegalArgumentException("preview " + width + "x" + height + " with " + pixels.length + " pixels");
        }
        pixels = pixels.clone();
    }

    /**
     * Returns a copy of the pixel array.
     *
     * @return a new array in row-major order that the caller may modify freely
     */
    @Override
    public byte[] pixels() {
        return pixels.clone();
    }

    /**
     * Returns the intensity of one pixel.
     *
     * @param row zero-based row, counted from the top
     * @param col zero-based column, counted from the left
     * @return the intensity in the range {@code 0} to {@code 255}
     * @throws IndexOutOfBoundsException if {@code row} is outside {@code 0} to {@code height - 1} or {@code col}
     *                                   is outside {@code 0} to {@code width - 1}
     */
    public int grey(int row, int col) {
        return pixels[Objects.checkIndex(row, height) * width + Objects.checkIndex(col, width)] & 0xff;
    }
}
