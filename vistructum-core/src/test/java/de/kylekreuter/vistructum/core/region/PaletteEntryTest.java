package de.kylekreuter.vistructum.core.region;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaletteEntryTest {

    @Test
    void sortsPropertiesIntoOneCanonicalState() {
        PaletteEntry entry = PaletteEntry.of("minecraft:oak_log", Map.of("axis", "y", "a", "b"));
        assertEquals("minecraft:oak_log[a=b,axis=y]", entry.state());
    }

    @Test
    void recognisesAllAirVariants() {
        assertTrue(PaletteEntry.AIR.air());
        assertTrue(new PaletteEntry("minecraft:cave_air", "").air());
        assertTrue(new PaletteEntry("minecraft:void_air", "").air());
        assertFalse(new PaletteEntry("minecraft:end_stone", "").air());
    }

    @Test
    void parsesStatesWithAndWithoutProperties() {
        assertEquals(new PaletteEntry("minecraft:stone", ""), PaletteEntry.parse("minecraft:stone"));
        assertEquals(new PaletteEntry("minecraft:water", "[level=0]"), PaletteEntry.parse("minecraft:water[level=0]"));
    }
}
