package de.kylekreuter.vistructum.core.scene;

import java.util.Objects;

/**
 * A rectangle of the world as the sidecar sees it, row-major with {@code index = row * width + col}.
 *
 * <p>For a top-down view a row is a z coordinate and a column an x coordinate, with row 0 at the smallest z and
 * column 0 at the smallest x. Side projections of the modification mask use the same layout with rows running
 * from the highest y down.
 *
 * <ul>
 *   <li>{@code blocks}: any id that is equal exactly when two blocks are the same material, {@link #UNKNOWN} where
 *       the column is not loaded or not sampled; only equality matters to the models</li>
 *   <li>{@code heights}: y of the surface block (the MOTION_BLOCKING surface: leaves and fluids count, grass,
 *       flowers and snow layers do not)</li>
 *   <li>{@code luminance}: {@code rint(0.299 r + 0.587 g + 0.114 b)} of the surface block's base map colour, 0..255</li>
 *   <li>{@code modified}: 1 where a player changed a block recently, else 0</li>
 * </ul>
 *
 * The sidecar accepts 1..512 per side. Arrays are owned by the scene; callers must not modify them afterwards.
 */
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

    /** a scene that only carries a modification mask, as the mask model needs */
    public static SurfaceScene maskOnly(int width, int height, byte[] modified) {
        int count = width * height;
        short[] unknown = new short[count];
        java.util.Arrays.fill(unknown, UNKNOWN);
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
