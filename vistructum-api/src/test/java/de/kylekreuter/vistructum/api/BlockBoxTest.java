package de.kylekreuter.vistructum.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockBoxTest {

    @Test
    void centerRoundsTowardsNegativeInfinity() {
        BlockBox box = new BlockBox(-3, 0, -6, 0, 5, -1);
        assertEquals(-2, box.centerX());
        assertEquals(-4, box.centerZ());
    }

    @Test
    void touchingBoxesOverlapAndSeparatedBoxesDoNot() {
        BlockBox box = new BlockBox(0, 0, 0, 10, 10, 10);
        assertTrue(box.overlaps(new BlockBox(10, 10, 10, 20, 20, 20)));
        assertFalse(box.overlaps(new BlockBox(11, 0, 0, 20, 10, 10)));
        assertFalse(box.overlaps(new BlockBox(0, 11, 0, 10, 20, 10)));
        assertFalse(box.overlaps(new BlockBox(0, 0, 11, 10, 10, 20)));
    }

    @Test
    void invertedBoxIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new BlockBox(1, 0, 0, 0, 0, 0));
    }
}
