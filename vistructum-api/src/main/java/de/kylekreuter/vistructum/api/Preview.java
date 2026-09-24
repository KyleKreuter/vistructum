package de.kylekreuter.vistructum.api;

import java.util.Objects;

public record Preview(int width, int height, byte[] pixels) {

    public Preview {
        Objects.requireNonNull(pixels, "pixels");
        if (width < 1 || height < 1 || pixels.length != width * height) {
            throw new IllegalArgumentException("preview " + width + "x" + height + " with " + pixels.length + " pixels");
        }
        pixels = pixels.clone();
    }

    @Override
    public byte[] pixels() {
        return pixels.clone();
    }

    public int grey(int row, int col) {
        return pixels[row * width + col] & 0xff;
    }
}
