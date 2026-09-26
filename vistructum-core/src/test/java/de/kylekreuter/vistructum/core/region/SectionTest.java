package de.kylekreuter.vistructum.core.region;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SectionTest {

    @Test
    void decodesPackedIndicesForSeveralPaletteSizes() {
        for (int paletteSize : new int[]{2, 16, 17, 33, 300}) {
            int[] indices = new int[Section.VOLUME];
            for (int i = 0; i < indices.length; i++) {
                indices[i] = (i * 7) % paletteSize;
            }
            List<PaletteEntry> palette = IntStream.range(0, paletteSize).mapToObj(i -> new PaletteEntry("b" + i, "")).toList();
            Section section = new Section(0, palette, NbtWriter.pack(indices, paletteSize));
            assertArrayEquals(indices, section.indices(), "palette size " + paletteSize);
        }
    }

    @Test
    void packRoundTripsIndices() {
        int[] indices = new int[Section.VOLUME];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = i % 3;
        }
        List<PaletteEntry> palette = List.of(PaletteEntry.AIR, new PaletteEntry("a", ""), new PaletteEntry("b", ""));
        assertArrayEquals(indices, Section.pack(0, palette, indices).indices());
    }

    @Test
    void singleEntryPaletteNeedsNoData() {
        assertArrayEquals(new int[Section.VOLUME], new Section(0, List.of(PaletteEntry.AIR), new long[0]).indices());
    }

    @Test
    void indexOrderIsYThenZThenX() {
        assertEquals(0, Section.index(0, 0, 0));
        assertEquals(1, Section.index(1, 0, 0));
        assertEquals(16, Section.index(0, 0, 1));
        assertEquals(256, Section.index(0, 1, 0));
    }

    @Test
    void rejectsTooShortData() {
        assertThrows(IllegalArgumentException.class, () -> new Section(0, List.of(PaletteEntry.AIR, new PaletteEntry("b", "")), new long[3]));
    }
}
