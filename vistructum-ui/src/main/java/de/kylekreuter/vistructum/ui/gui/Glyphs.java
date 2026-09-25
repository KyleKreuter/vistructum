package de.kylekreuter.vistructum.ui.gui;

import net.kyori.adventure.key.Key;

import java.util.List;

final class Glyphs {

    static final Key FONT = Key.key("vistructum", "gui");
    static final Key BAR_LABEL = Key.key("vistructum", "bar_label");
    static final char LIST_BACKGROUND = '\uE201';
    static final char DETAIL_BACKGROUND = '\uE202';
    static final char SCENE_PANEL = '\uE203';
    static final char INFO_PANEL = '\uE204';
    static final char LOGO = '\uE205';
    static final char BAR_FRAME = '\uE206';
    static final int BACKGROUND_ASCENT = 13;
    static final List<Integer> PIXEL_SIZES = List.of(2, 4);
    static final int PIXEL_TEXTURE_HEIGHT = 16;
    static final int FIRST_LINE = 5;
    static final int SHIFT_STEPS = 9;
    static final char NEGATIVE_ONE = shiftChar(-1);

    private Glyphs() {
    }

    static char barFill(int step) {
        return (char) (0xE210 + step);
    }

    static char pixel(int pixelSize, int top) {
        return (char) (0xE400 + PIXEL_SIZES.indexOf(pixelSize) * 0x100 + top);
    }

    static int ascentAt(int top) {
        return BACKGROUND_ASCENT - top;
    }

    static Key line(int top) {
        return Key.key("vistructum", "line_" + top);
    }

    static int textWidth(String text) {
        int width = 0;
        for (char glyph : text.toCharArray()) {
            width += advance(glyph);
        }
        return width;
    }

    private static int advance(char glyph) {
        return switch (glyph) {
            case '!', '\'', ',', '.', ':', ';', '|', 'i' -> 2;
            case 'l', '`' -> 3;
            case ' ', 'I', 't', '[', ']' -> 4;
            case 'f', 'k', '<', '>' -> 5;
            case '@', '~' -> 7;
            default -> 6;
        };
    }

    static char shiftChar(int advance) {
        int step = Integer.numberOfTrailingZeros(Math.abs(advance));
        return (char) ((advance < 0 ? 0xF000 : 0xF100) + step);
    }

    static String shift(int distance) {
        StringBuilder out = new StringBuilder();
        int remaining = Math.abs(distance);
        for (int step = SHIFT_STEPS - 1; step >= 0; step--) {
            int amount = 1 << step;
            while (remaining >= amount) {
                out.append(shiftChar(distance < 0 ? -amount : amount));
                remaining -= amount;
            }
        }
        return out.toString();
    }
}
