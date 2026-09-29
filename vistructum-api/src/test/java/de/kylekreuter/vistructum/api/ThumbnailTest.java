package de.kylekreuter.vistructum.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ThumbnailTest {

    @Test
    void pixelsCannotBeChangedFromOutside() {
        int[] source = {0x102030, 0x405060};
        Thumbnail thumbnail = new Thumbnail(2, 1, source);
        source[0] = 0;
        thumbnail.pixels()[1] = 0;
        assertEquals(0x102030, thumbnail.rgb(0, 0));
        assertEquals(0x405060, thumbnail.rgb(0, 1));
    }

    @Test
    void alphaBitsAreCleared() {
        assertEquals(0xABCDEF, new Thumbnail(1, 1, new int[]{0xFFABCDEF}).rgb(0, 0));
    }

    @Test
    void thumbnailsWithEqualPixelsAreEqual() {
        assertEquals(new Thumbnail(1, 2, new int[]{1, 2}), new Thumbnail(1, 2, new int[]{1, 2}));
    }

    @Test
    void sizeMustMatchPixels() {
        assertThrows(IllegalArgumentException.class, () -> new Thumbnail(2, 2, new int[3]));
        assertThrows(IllegalArgumentException.class, () -> new Thumbnail(0, 1, new int[0]));
    }
}
