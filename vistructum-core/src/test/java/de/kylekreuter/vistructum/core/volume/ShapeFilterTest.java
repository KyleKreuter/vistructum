package de.kylekreuter.vistructum.core.volume;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapeFilterTest {

    private static final int MARGIN = 2;

    @Test
    void keepsSolidSwastikaInBothHandednesses() {
        String right = """
                ###.#
                ..#.#
                #####
                #.#..
                #.###
                """;
        String left = """
                #.###
                #.#..
                #####
                ..#.#
                ###.#
                """;
        assertFalse(implausible(right));
        assertFalse(implausible(left));
    }

    @Test
    void keepsSparsePinwheel() {
        assertFalse(implausible("""
                ...#...
                ..#....
                ..#.##.
                #..#..#
                .##.#..
                ....#..
                ...#...
                """));
    }

    @Test
    void keepsTwoSwastikasStackedInOneBox() {
        assertFalse(implausible("""
                ..###.#
                ....#.#
                ..#####
                ..#.#..
                ..#.###
                .......
                .......
                ###.#..
                ..#.#..
                #####..
                #.#....
                #.###..
                """));
    }

    @Test
    void keepsPinwheelWithStrayBlocks() {
        assertFalse(implausible("""
                ...#........
                ....#.......
                .##.#.......
                #..#..#.....
                ..#.##......
                ..#......#.#
                ...#.....#.#
                """));
    }

    @Test
    void rejectsMirrorSymmetricShapes() {
        String cross = """
                #.....#
                .#...#.
                ..#.#..
                ...#...
                ..#.#..
                .#...#.
                #.....#
                """;
        String diamond = """
                ...#...
                ..###..
                .#.#.#.
                ###.###
                .#.#.#.
                ..###..
                ...#...
                """;
        String checkerboard = """
                #.#.#
                .#.#.
                #.#.#
                .#.#.
                #.#.#
                """;
        String chevron = """
                ...#...
                ..#.#..
                .#...#.
                #.....#
                """;
        assertTrue(implausible(cross));
        assertTrue(implausible(diamond));
        assertTrue(implausible(checkerboard));
        assertTrue(implausible(chevron));
    }

    @Test
    void rejectsLines() {
        String staircase = """
                .......#..
                ......#...
                .....#...#
                ....#...#.
                ...#...#..
                ..#...#...
                .#...#....
                #...#.....
                ...#......
                ..#.......
                """;
        String slope = """
                ##........
                ..##......
                ....##....
                ......##..
                ........##
                """;
        assertTrue(implausible(staircase));
        assertTrue(implausible(slope));
    }

    @Test
    void rejectsEmptyDetection() {
        assertTrue(ShapeFilter.implausible(new byte[16], 4, 4, 0, 0, 4, 4));
    }

    private static boolean implausible(String rows) {
        String[] lines = rows.strip().split("\n");
        int height = lines.length + 2 * MARGIN;
        int width = lines[0].length() + 2 * MARGIN;
        byte[] mask = new byte[width * height];
        for (int row = 0; row < lines.length; row++) {
            for (int col = 0; col < lines[row].length(); col++) {
                if (lines[row].charAt(col) == '#') {
                    mask[(row + MARGIN) * width + col + MARGIN] = 1;
                }
            }
        }
        return ShapeFilter.implausible(mask, width, height, 0, 0, height, width);
    }
}
