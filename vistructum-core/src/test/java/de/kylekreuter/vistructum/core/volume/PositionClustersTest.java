package de.kylekreuter.vistructum.core.volume;

import de.kylekreuter.vistructum.core.scene.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PositionClustersTest {

    @Test
    void joinsWithinChebyshevDistanceAndSplitsBeyond() {
        List<BlockPos> positions = List.of(new BlockPos(0, 0, 0), new BlockPos(3, 3, 3), new BlockPos(7, 3, 3));
        assertEquals(2, PositionClusters.of(positions, 3).size());
        assertEquals(1, PositionClusters.of(positions, 4).size());
    }

    @Test
    void keepsEveryPositionExactlyOnce() {
        List<BlockPos> positions = List.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), new BlockPos(10, 0, 0));
        assertEquals(3, PositionClusters.of(positions, 1).stream().mapToInt(List::size).sum());
    }
}
