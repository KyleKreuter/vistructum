package de.kylekreuter.vistructum.inference;

import java.util.Arrays;
import java.util.Objects;

public record SurfaceScene(int width, int height, short[] blocks, short[] heights, byte[] luminance, byte[] modified) {

    public static final short UNKNOWN = -1;
    public static final int MAX_SIDE = 512;

    public SurfaceScene {
        if (width < 1 || height < 1 || width > MAX_SIDE || height > MAX_SIDE) {
            throw new IllegalArgumentException("scene " + width + "x" + height + " outside 1.." + MAX_SIDE);
        }
        int count = width * height;
        check("blocks", Objects.requireNonNull(blocks).length, count);
        check("heights", Objects.requireNonNull(heights).length, count);
        check("luminance", Objects.requireNonNull(luminance).length, count);
        check("modified", Objects.requireNonNull(modified).length, count);
    }

    public static SurfaceScene maskOnly(int width, int height, byte[] modified) {
        int count = width * height;
        short[] unknown = new short[count];
        Arrays.fill(unknown, UNKNOWN);
        return new SurfaceScene(width, height, unknown, new short[count], new byte[count], modified);
    }

    public int index(int row, int col) {
        return row * width + col;
    }

    private static void check(String name, int actual, int expected) {
        if (actual != expected) {
            throw new IllegalArgumentException(name + " has " + actual + " values, expected " + expected);
        }
    }
}
