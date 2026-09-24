package de.kylekreuter.vistructum.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PreviewTest {

    @Test
    void pixelsCannotBeChangedFromOutside() {
        byte[] source = {10, 20};
        Preview preview = new Preview(2, 1, source);
        source[0] = 99;
        preview.pixels()[1] = 99;
        assertEquals(10, preview.grey(0, 0));
        assertEquals(20, preview.grey(0, 1));
    }

    @Test
    void greyIsUnsigned() {
        assertEquals(235, new Preview(1, 1, new byte[]{(byte) 235}).grey(0, 0));
    }

    @Test
    void sizeMustMatchPixels() {
        assertThrows(IllegalArgumentException.class, () -> new Preview(2, 2, new byte[3]));
        assertThrows(IllegalArgumentException.class, () -> new Preview(0, 1, new byte[0]));
    }
}
