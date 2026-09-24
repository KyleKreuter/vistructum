package de.kylekreuter.vistructum.core.alert;

import java.util.Objects;

public record Preview(int width, int height, byte[] pixels) {

    public static final int EMPTY = 235;
    public static final int BUILT = 40;

    public Preview {
        Objects.requireNonNull(pixels, "pixels");
        if (width < 1 || height < 1 || pixels.length != width * height) {
            throw new IllegalArgumentException("preview " + width + "x" + height + " with " + pixels.length + " pixels");
        }
    }

    public static Preview ofLuminance(byte[] luminance, int sceneWidth, int sceneHeight, int top, int left, int bottom,
                                      int right) {
        return crop(luminance, sceneWidth, sceneHeight, top, left, bottom, right, false);
    }

    public static Preview ofMask(byte[] modified, int sceneWidth, int sceneHeight, int top, int left, int bottom,
                                 int right) {
        return crop(modified, sceneWidth, sceneHeight, top, left, bottom, right, true);
    }

    public int grey(int row, int col) {
        return pixels[row * width + col] & 0xff;
    }

    private static Preview crop(byte[] source, int sceneWidth, int sceneHeight, int top, int left, int bottom, int right,
                                boolean mask) {
        int t = Math.clamp(top, 0, sceneHeight - 1);
        int l = Math.clamp(left, 0, sceneWidth - 1);
        int b = Math.clamp(bottom, t + 1, sceneHeight);
        int r = Math.clamp(right, l + 1, sceneWidth);
        int width = r - l;
        int height = b - t;
        byte[] pixels = new byte[width * height];
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                byte value = source[(t + row) * sceneWidth + l + col];
                pixels[row * width + col] = mask ? (byte) (value != 0 ? BUILT : EMPTY) : value;
            }
        }
        return new Preview(width, height, pixels);
    }
}
