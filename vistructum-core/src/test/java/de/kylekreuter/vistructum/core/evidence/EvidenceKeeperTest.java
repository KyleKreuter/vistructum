package de.kylekreuter.vistructum.core.evidence;

import de.kylekreuter.vistructum.api.BlockBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvidenceKeeperTest {

    @Test
    void regionAddsTheMarginAroundSmallBoxes() {
        BlockBox region = EvidenceKeeper.region(new BlockBox(10, 64, 20, 16, 64, 26), 4, -64, 319);

        assertEquals(new BlockBox(6, 60, 16, 20, 68, 30), region);
    }

    @Test
    void regionIsCutToTheModelGridAroundTheCentreOfLargeBoxes() {
        BlockBox region = EvidenceKeeper.region(new BlockBox(-100, 60, 0, 99, 70, 199), 4, -64, 319);

        assertEquals(EvidenceKeeper.MAX_EXTENT, region.maxX() - region.minX() + 1);
        assertEquals(EvidenceKeeper.MAX_EXTENT, region.maxZ() - region.minZ() + 1);
        assertEquals(new BlockBox(-32, 56, 68, 31, 74, 131), region);
    }

    @Test
    void regionStaysInsideTheWorldHeight() {
        BlockBox region = EvidenceKeeper.region(new BlockBox(0, -64, 0, 6, 200, 6), 4, -64, 319);

        assertEquals(EvidenceKeeper.MAX_EXTENT, region.maxY() - region.minY() + 1);
        assertEquals(new BlockBox(-4, 37, -4, 10, 100, 10), region);
    }
}
