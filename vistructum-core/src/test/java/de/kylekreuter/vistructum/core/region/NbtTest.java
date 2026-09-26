package de.kylekreuter.vistructum.core.region;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NbtTest {

    @Test
    void readsNestedValues() throws IOException {
        byte[] data = NbtWriter.root(Map.of("n", 7, "s", "stone", "l", List.of(1L, 2L), "a", new long[]{3, 4},
                "c", Map.of("b", (byte) 5)));
        Map<String, Object> root = Nbt.read(data, Set.of());
        assertEquals(7, root.get("n"));
        assertEquals("stone", root.get("s"));
        assertEquals(List.of(1L, 2L), root.get("l"));
        assertArrayEquals(new long[]{3, 4}, (long[]) root.get("a"));
        assertEquals(Map.of("b", (byte) 5), root.get("c"));
    }

    @Test
    void skipsNamedTagsAndKeepsReadingAfterThem() throws IOException {
        byte[] data = NbtWriter.root(Map.of("skip", Map.of("deep", List.of(Map.of("x", 1))), "keep", 9));
        Map<String, Object> root = Nbt.read(data, Set.of("skip"));
        assertFalse(root.containsKey("skip"));
        assertEquals(9, root.get("keep"));
    }

    @Test
    void rejectsNonCompoundRoot() {
        assertThrows(IOException.class, () -> Nbt.read(new byte[]{3, 0, 0, 0, 0, 0, 1}, Set.of()));
    }
}
