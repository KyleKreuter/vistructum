package de.kylekreuter.vistructum.ui.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressBarTest {

    @Test
    void completionMapsToFilledPixels() {
        assertEquals(0, ProgressBar.filledPixels(0.0));
        assertEquals(90, ProgressBar.filledPixels(0.5));
        assertEquals(ProgressBar.FILL_WIDTH, ProgressBar.filledPixels(1.0));
        assertEquals(0, ProgressBar.filledPixels(-0.2));
        assertEquals(ProgressBar.FILL_WIDTH, ProgressBar.filledPixels(1.7));
    }

    @Test
    void fillGlyphsCoverExactlyTheFilledPixels() {
        for (int filled = 0; filled <= ProgressBar.FILL_WIDTH; filled++) {
            int covered = 0;
            for (char glyph : ProgressBar.glyphs(filled, 0).toCharArray()) {
                for (int step = 0; step < ProgressBar.FILL_STEPS; step++) {
                    if (glyph == Glyphs.barFill(step)) {
                        covered += 1 << step;
                    }
                }
            }
            assertEquals(filled, covered);
        }
    }

    @Test
    void barEndsWhereTheCenteredLabelStarts() {
        for (int labelWidth : new int[]{0, 1, 57, 120, 181}) {
            for (int filled : new int[]{0, 1, 90, ProgressBar.FILL_WIDTH}) {
                assertEquals(-(labelWidth / 2), advance(ProgressBar.glyphs(filled, labelWidth)));
            }
        }
    }

    @Test
    void fillStartsInsideTheFrame() {
        String glyphs = ProgressBar.glyphs(ProgressBar.FILL_WIDTH, 0);
        int firstFill = glyphs.indexOf(Glyphs.barFill(ProgressBar.FILL_STEPS - 1));
        assertEquals(-ProgressBar.FILL_WIDTH / 2, advance(glyphs.substring(0, firstFill)));
    }

    @Test
    void titleKeepsTheLabelText() {
        Component title = ProgressBar.title(Component.text("Fullscan of world: 42%, 3 findings"), 0.42);
        assertTrue(PlainTextComponentSerializer.plainText().serialize(title).contains("Fullscan of world: 42%, 3 findings"));
    }

    @Test
    void labelUsesTheLiftedFont() {
        Component title = ProgressBar.title(Component.text("Fullscan"), 0.5);
        assertEquals(Glyphs.BAR_LABEL, title.children().get(1).font());
    }

    @Test
    void packShipsTheBarTexturesAndGlyphs() throws Exception {
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(ResourcePack.build().zip()))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                files.put(entry.getName(), zip.readAllBytes());
            }
        }
        assertTrue(files.containsKey("assets/vistructum/textures/font/bar_frame.png"));
        String font = new String(files.get("assets/vistructum/font/gui.json"), StandardCharsets.UTF_8);
        assertTrue(font.contains(String.format("\\u%04X", (int) Glyphs.BAR_FRAME)));
        for (int step = 0; step < ProgressBar.FILL_STEPS; step++) {
            assertTrue(files.containsKey("assets/vistructum/textures/font/bar_fill_" + (1 << step) + ".png"));
            assertTrue(font.contains(String.format("\\u%04X", (int) Glyphs.barFill(step))));
        }
        String label = new String(files.get("assets/vistructum/font/bar_label.json"), StandardCharsets.UTF_8);
        assertTrue(label.contains("\"ascent\": " + ProgressBar.LABEL_ASCENT));
    }

    private static int advance(String glyphs) {
        int total = 0;
        for (char glyph : glyphs.toCharArray()) {
            total += advance(glyph);
        }
        return total;
    }

    private static int advance(char glyph) {
        if (glyph == Glyphs.BAR_FRAME) {
            return ProgressBar.FRAME_WIDTH + 1;
        }
        for (int step = 0; step < Glyphs.SHIFT_STEPS; step++) {
            if (glyph == Glyphs.shiftChar(1 << step)) {
                return 1 << step;
            }
            if (glyph == Glyphs.shiftChar(-(1 << step))) {
                return -(1 << step);
            }
        }
        for (int step = 0; step < ProgressBar.FILL_STEPS; step++) {
            if (glyph == Glyphs.barFill(step)) {
                return (1 << step) + 1;
            }
        }
        throw new AssertionError("unexpected glyph " + Integer.toHexString(glyph));
    }
}
