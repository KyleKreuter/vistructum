package de.kylekreuter.vistructum.ui.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

final class PixelText {

    private static final TextColor HEADING = TextColor.color(0x2B2B2B);
    private static final TextColor MUTED = TextColor.color(0x6B6B6B);
    private static final int TITLE_ORIGIN = 8;

    private PixelText() {
    }

    static Component detail(String label, Picture map, Picture face, String builder, List<Field> fields) {
        int mapPixel = Layout.MAP_PIXEL;
        Cursor cursor = new Cursor(TITLE_ORIGIN);
        cursor.glyph(Layout.SCENE_LEFT, Glyphs.SCENE_PANEL, Layout.SIDE_WIDTH);
        cursor.glyph(0, Glyphs.DETAIL_BACKGROUND, Layout.GUI_WIDTH);
        cursor.glyph(Layout.INFO_LEFT, Glyphs.INFO_PANEL, Layout.SIDE_WIDTH);
        cursor.picture(map, Layout.mapLeft(mapPixel), mapPixel,
                row -> Glyphs.pixel(mapPixel, Layout.mapTop(mapPixel) + row * mapPixel));
        cursor.picture(face, Layout.FACE_LEFT, Layout.FACE_PIXEL,
                row -> Glyphs.pixel(Layout.FACE_PIXEL, Layout.FACE_TOP + row * Layout.FACE_PIXEL));
        cursor.glyph(Layout.LOGO_LEFT, Glyphs.LOGO, Layout.LOGO_WIDTH);
        int sceneText = Layout.SCENE_LEFT + Layout.PADDING;
        cursor.text(sceneText, Layout.SCENE_LABEL_TOP, label, HEADING);
        cursor.text(Layout.NAME_LEFT, Layout.NAME_TOP, builder, HEADING);
        int infoText = Layout.INFO_LEFT + Layout.PADDING;
        for (int index = 0; index < fields.size(); index++) {
            Field field = fields.get(index);
            int top = Layout.fieldTop(fields.size()) + index * Layout.fieldStep(fields.size());
            cursor.text(infoText, top, field.name(), MUTED);
            cursor.text(infoText, top + Layout.VALUE_OFFSET, field.value(), HEADING);
        }
        return cursor.build();
    }

    static Component list(String page) {
        Cursor cursor = new Cursor(TITLE_ORIGIN);
        cursor.glyph(0, Glyphs.LIST_BACKGROUND, Layout.GUI_WIDTH);
        cursor.glyph(Layout.LOGO_LEFT, Glyphs.LOGO, Layout.LOGO_WIDTH);
        cursor.text((Layout.GUI_WIDTH - Glyphs.textWidth(page)) / 2, Layout.PAGE_TOP, page, MUTED);
        return cursor.build();
    }

    private static final class Cursor {

        private final List<Component> parts = new ArrayList<>();
        private int x;

        Cursor(int origin) {
            this.x = origin;
        }

        void moveTo(int target) {
            if (target != x) {
                parts.add(Component.text(Glyphs.shift(target - x)).font(Glyphs.FONT));
                x = target;
            }
        }

        void glyph(int left, char glyph, int width) {
            moveTo(left);
            parts.add(Component.text(String.valueOf(glyph), NamedTextColor.WHITE).font(Glyphs.FONT));
            x += width + 1;
        }

        void text(int left, int top, String text, TextColor color) {
            moveTo(left);
            parts.add(Component.text(text, color).font(Glyphs.line(top)));
            x += Glyphs.textWidth(text);
        }

        void picture(Picture image, int left, int pixelSize, IntFunction<Character> rowGlyph) {
            for (int row = 0; row < image.height(); row++) {
                moveTo(left);
                String pixel = String.valueOf(rowGlyph.apply(row)) + Glyphs.NEGATIVE_ONE;
                int col = 0;
                while (col < image.width()) {
                    int colour = image.at(row, col);
                    int run = 1;
                    while (col + run < image.width() && image.at(row, col + run) == colour) {
                        run++;
                    }
                    parts.add(Component.text(pixel.repeat(run), TextColor.color(colour)).font(Glyphs.FONT));
                    x += run * pixelSize;
                    col += run;
                }
            }
        }

        Component build() {
            return Component.text().append(parts).build();
        }
    }
}
