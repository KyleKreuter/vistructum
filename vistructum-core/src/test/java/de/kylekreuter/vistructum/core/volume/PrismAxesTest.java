package de.kylekreuter.vistructum.core.volume;

import de.kylekreuter.vistructum.core.scene.Axis;
import de.kylekreuter.vistructum.core.scene.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrismAxesTest {

    private static final PrismAxes PRISM = new PrismAxes(0.9, 5);

    @Test
    void flatSymbolPassesOnlyAlongItsNormal() {
        assertEquals(List.of(Axis.Y), PRISM.of(extrude(1)));
    }

    @Test
    void extrudedSymbolPassesAlongItsNormal() {
        assertEquals(List.of(Axis.Y), PRISM.of(extrude(4)));
    }

    @Test
    void irregularBlobIsRejected() {
        List<BlockPos> blob = new ArrayList<>();
        for (int x = 0; x < 6; x++) {
            for (int y = 0; y < 6; y++) {
                for (int z = 0; z < 6; z++) {
                    if ((x * 7 + y * 13 + z * 5) % 3 == 0) {
                        blob.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        assertEquals(List.of(), PRISM.of(blob));
    }

    @Test
    void footprintBelowMinimumSideIsRejected() {
        List<BlockPos> bar = new ArrayList<>();
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 4; z++) {
                bar.add(new BlockPos(x, 0, z));
            }
        }
        assertEquals(List.of(), PRISM.of(bar));
    }

    private static List<BlockPos> extrude(int layers) {
        int[][] cells = {{0, 4}, {1, 4}, {2, 4}, {3, 4}, {4, 4}, {5, 4}, {6, 4}, {7, 4}, {8, 4},
                {4, 0}, {4, 1}, {4, 2}, {4, 3}, {4, 5}, {4, 6}, {4, 7}, {4, 8},
                {0, 0}, {1, 0}, {2, 0}, {3, 0}, {8, 1}, {8, 2}, {8, 3}, {8, 0},
                {5, 8}, {6, 8}, {7, 8}, {8, 8}, {0, 5}, {0, 6}, {0, 7}, {0, 8}};
        List<BlockPos> positions = new ArrayList<>();
        for (int y = 0; y < layers; y++) {
            for (int[] cell : cells) {
                positions.add(new BlockPos(cell[0], 10 + y, cell[1]));
            }
        }
        return positions;
    }
}
