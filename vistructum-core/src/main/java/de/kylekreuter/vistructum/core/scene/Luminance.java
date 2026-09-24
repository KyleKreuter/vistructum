package de.kylekreuter.vistructum.core.scene;

import org.bukkit.Color;

public final class Luminance {

    private Luminance() {
    }

    public static int of(int r, int g, int b) {
        int value = (int) Math.rint(0.299 * r + 0.587 * g + 0.114 * b);
        return Math.max(0, Math.min(255, value));
    }

    public static int of(Color color) {
        return of(color.getRed(), color.getGreen(), color.getBlue());
    }
}
