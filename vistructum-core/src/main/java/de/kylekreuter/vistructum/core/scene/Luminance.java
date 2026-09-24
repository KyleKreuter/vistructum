package de.kylekreuter.vistructum.core.scene;

import org.bukkit.Color;

/** the greyscale value the training data uses for a block's base map colour. */
public final class Luminance {

    private Luminance() {
    }

    /**
     * {@code rint(0.299 r + 0.587 g + 0.114 b)} clamped to 0..255. {@link Math#rint} rounds half to even, matching
     * Python's {@code round()} used when the training data was generated.
     */
    public static int of(int r, int g, int b) {
        int value = (int) Math.rint(0.299 * r + 0.587 * g + 0.114 * b);
        return Math.max(0, Math.min(255, value));
    }

    public static int of(Color color) {
        return of(color.getRed(), color.getGreen(), color.getBlue());
    }
}
