package de.kylekreuter.vistructum.ui.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class ProgressBar {

    static final int FRAME_WIDTH = 182;
    static final int FRAME_HEIGHT = 7;
    static final int FRAME_ASCENT = -1;
    static final int FILL_WIDTH = 180;
    static final int FILL_HEIGHT = 5;
    static final int FILL_ASCENT = -2;
    static final int FILL_STEPS = 8;
    static final int LABEL_LIFT = 3;
    static final int LABEL_ASCENT = 7 + LABEL_LIFT;

    private ProgressBar() {
    }

    public static Component title(Component label, double completion) {
        int labelWidth = Glyphs.textWidth(PlainTextComponentSerializer.plainText().serialize(label));
        return Component.text()
                .append(Component.text(glyphs(filledPixels(completion), labelWidth))
                        .font(Glyphs.FONT)
                        .color(NamedTextColor.WHITE)
                        .shadowColor(ShadowColor.none()))
                .append(label.font(Glyphs.BAR_LABEL))
                .append(Component.text(Glyphs.shift(labelWidth / 2 - labelWidth)).font(Glyphs.FONT))
                .build();
    }

    static int filledPixels(double completion) {
        return (int) Math.round(Math.clamp(completion, 0.0, 1.0) * FILL_WIDTH);
    }

    static String glyphs(int filled, int labelWidth) {
        StringBuilder out = new StringBuilder()
                .append(Glyphs.shift(-FRAME_WIDTH / 2))
                .append(Glyphs.BAR_FRAME)
                .append(Glyphs.NEGATIVE_ONE)
                .append(Glyphs.shift(-FILL_WIDTH - 1));
        for (int step = FILL_STEPS - 1; step >= 0; step--) {
            if ((filled & (1 << step)) != 0) {
                out.append(Glyphs.barFill(step)).append(Glyphs.NEGATIVE_ONE);
            }
        }
        return out.append(Glyphs.shift(FILL_WIDTH / 2 - filled - labelWidth / 2)).toString();
    }
}
