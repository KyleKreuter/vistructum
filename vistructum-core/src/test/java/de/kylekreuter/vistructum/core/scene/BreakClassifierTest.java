package de.kylekreuter.vistructum.core.scene;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BreakClassifierTest {

    private static final int SIZE = 9;
    private static final int WALL_Z = 5;

    @Test
    void symbolCarvedIntoAStoneWallCounts() {
        Map<BlockPos, String> broken = symbolAt(WALL_Z, "STONE");
        Function<BlockPos, BreakClassifier.Sample> world = wall(broken.keySet(), "STONE");

        assertEquals(broken.keySet(), BreakClassifier.carved(Axis.Z, broken, broken.keySet(), world));
    }

    @Test
    void removedFreeStandingSymbolDoesNotCount() {
        Map<BlockPos, String> broken = symbolAt(WALL_Z, "OAK_PLANKS");
        Function<BlockPos, BreakClassifier.Sample> world = pos -> pos.y() < 0
                ? new BreakClassifier.Sample(true, true, "GRASS_BLOCK")
                : new BreakClassifier.Sample(true, false, "AIR");

        assertTrue(BreakClassifier.carved(Axis.Z, broken, broken.keySet(), world).isEmpty());
    }

    @Test
    void removedInlaidSymbolDoesNotCount() {
        Map<BlockPos, String> broken = symbolAt(WALL_Z, "RED_WOOL");
        Function<BlockPos, BreakClassifier.Sample> world = wall(broken.keySet(), "STONE");

        assertTrue(BreakClassifier.carved(Axis.Z, broken, broken.keySet(), world).isEmpty());
    }

    @Test
    void carvingIntoMixedTerrainStillCounts() {
        Map<BlockPos, String> broken = symbolAt(WALL_Z, "DIRT");
        Function<BlockPos, BreakClassifier.Sample> world = pos -> broken.containsKey(pos)
                ? new BreakClassifier.Sample(true, false, "AIR")
                : new BreakClassifier.Sample(true, true, pos.y() == SIZE - 1 ? "GRASS_BLOCK"
                : (pos.x() + pos.y()) % 4 == 0 ? "STONE" : "DIRT");

        assertEquals(broken.keySet(), BreakClassifier.carved(Axis.Z, broken, broken.keySet(), world));
    }

    @Test
    void unloadedNeighboursDecideNothing() {
        Map<BlockPos, String> broken = symbolAt(WALL_Z, "STONE");

        assertTrue(BreakClassifier.carved(Axis.Z, broken, broken.keySet(), pos -> BreakClassifier.Sample.UNLOADED)
                .isEmpty());
    }

    @Test
    void neighboursStayInTheProjectionPlane() {
        BlockPos pos = new BlockPos(10, 20, 30);
        for (Axis axis : Axis.values()) {
            Set<BlockPos> neighbours = Set.of(BreakClassifier.inPlaneNeighbours(axis, pos));
            assertEquals(8, neighbours.size());
            for (BlockPos neighbour : neighbours) {
                int depth = switch (axis) {
                    case Y -> neighbour.y() - pos.y();
                    case Z -> neighbour.z() - pos.z();
                    case X -> neighbour.x() - pos.x();
                };
                assertEquals(0, depth);
            }
        }
    }

    private static Function<BlockPos, BreakClassifier.Sample> wall(Set<BlockPos> holes, String material) {
        return pos -> holes.contains(pos) || pos.z() != WALL_Z
                ? new BreakClassifier.Sample(true, false, "AIR")
                : new BreakClassifier.Sample(true, true, material);
    }

    private static Map<BlockPos, String> symbolAt(int z, String material) {
        Set<BlockPos> cells = new HashSet<>();
        int c = SIZE / 2;
        for (int i = 0; i < SIZE; i++) {
            cells.add(new BlockPos(c, i, z));
            cells.add(new BlockPos(i, c, z));
        }
        for (int i = 0; i <= c; i++) {
            cells.add(new BlockPos(c + i, SIZE - 1, z));
            cells.add(new BlockPos(SIZE - 1, c - i, z));
            cells.add(new BlockPos(c - i, 0, z));
            cells.add(new BlockPos(0, c + i, z));
        }
        Map<BlockPos, String> broken = new HashMap<>();
        cells.forEach(pos -> broken.put(pos, material));
        return broken;
    }
}
