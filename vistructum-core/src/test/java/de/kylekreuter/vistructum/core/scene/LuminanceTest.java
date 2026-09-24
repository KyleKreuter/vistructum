package de.kylekreuter.vistructum.core.scene;

import org.bukkit.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LuminanceTest {

    @ParameterizedTest
    @CsvSource({
            "127, 178, 56, 149",
            "247, 233, 163, 229",
            "64, 64, 255, 86",
            "112, 112, 112, 112",
            "153, 51, 51, 81",
            "25, 25, 25, 25",
            "255, 255, 255, 255",
    })
    void matchesPythonTrainingReferenceValues(int r, int g, int b, int expected) {
        assertEquals(expected, Luminance.of(r, g, b));
    }

    @Test
    void colorOverloadDelegatesToRgb() {
        assertEquals(Luminance.of(127, 178, 56), Luminance.of(Color.fromRGB(127, 178, 56)));
    }

    @Test
    void roundsHalfToEven() {
        // 0.299*0 + 0.587*12 + 0.114*4 == 7.5 exactly in double arithmetic; the nearest even integer is 8.
        assertEquals(8, Luminance.of(0, 12, 4));
    }

    @Test
    void clampsToByteRange() {
        assertEquals(255, Luminance.of(255, 255, 255));
        assertEquals(0, Luminance.of(0, 0, 0));
    }
}
