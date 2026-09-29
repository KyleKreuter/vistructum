package de.kylekreuter.vistructum.core.store;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PackedTest {

    @Test
    void cellsRoundTrip() {
        int[] cells = {0, 1, 1, 300, 0, 70_000};
        assertEquals(List.of(0, 1, 1, 300, 0, 70_000),
                Arrays.stream(Packed.unpackCells(Packed.packCells(cells), cells.length)).boxed().toList());
    }
}
