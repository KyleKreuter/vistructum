package de.kylekreuter.vistructum.core.volume;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class ShapeFilter {

    static final double MIRROR_LIMIT = 0.8;
    static final double LINE_LIMIT = 0.2;
    static final int COMPONENT_MIN = 5;

    private ShapeFilter() {
    }

    public static boolean implausible(byte[] mask, int width, int height, int top, int left, int bottom, int right) {
        boolean[][] grid = trim(mask, width, height, top, left, bottom, right);
        if (grid.length == 0) {
            return true;
        }
        return mirrored(grid) >= MIRROR_LIMIT || (linear(cells(grid)) && components(grid).stream()
                .filter(component -> component.size() >= COMPONENT_MIN)
                .allMatch(ShapeFilter::linear));
    }

    static double mirrored(boolean[][] grid) {
        int rows = grid.length;
        int cols = grid[0].length;
        double best = Math.max(overlap(grid, (row, col) -> grid[row][cols - 1 - col]),
                overlap(grid, (row, col) -> grid[rows - 1 - row][col]));
        if (rows == cols) {
            best = Math.max(best, overlap(grid, (row, col) -> grid[col][row]));
            best = Math.max(best, overlap(grid, (row, col) -> grid[cols - 1 - col][rows - 1 - row]));
        }
        return best;
    }

    static boolean linear(List<int[]> cells) {
        if (cells.size() < 3) {
            return true;
        }
        double meanRow = 0;
        double meanCol = 0;
        for (int[] cell : cells) {
            meanRow += cell[0];
            meanCol += cell[1];
        }
        meanRow /= cells.size();
        meanCol /= cells.size();
        double rowRow = 0;
        double colCol = 0;
        double rowCol = 0;
        for (int[] cell : cells) {
            double dr = cell[0] - meanRow;
            double dc = cell[1] - meanCol;
            rowRow += dr * dr;
            colCol += dc * dc;
            rowCol += dr * dc;
        }
        double half = (rowRow + colCol) / 2;
        double spread = Math.sqrt((rowRow - colCol) * (rowRow - colCol) / 4 + rowCol * rowCol);
        double major = half + spread;
        double minor = half - spread;
        return major <= 0 || minor / major < LINE_LIMIT;
    }

    private interface Mirror {

        boolean at(int row, int col);
    }

    private static double overlap(boolean[][] grid, Mirror mirror) {
        int both = 0;
        int either = 0;
        for (int row = 0; row < grid.length; row++) {
            for (int col = 0; col < grid[row].length; col++) {
                boolean original = grid[row][col];
                boolean flipped = mirror.at(row, col);
                if (original && flipped) {
                    both++;
                }
                if (original || flipped) {
                    either++;
                }
            }
        }
        return either == 0 ? 0 : (double) both / either;
    }

    private static List<int[]> cells(boolean[][] grid) {
        List<int[]> cells = new ArrayList<>();
        for (int row = 0; row < grid.length; row++) {
            for (int col = 0; col < grid[row].length; col++) {
                if (grid[row][col]) {
                    cells.add(new int[]{row, col});
                }
            }
        }
        return cells;
    }

    private static List<List<int[]>> components(boolean[][] grid) {
        boolean[][] seen = new boolean[grid.length][grid[0].length];
        List<List<int[]>> components = new ArrayList<>();
        for (int row = 0; row < grid.length; row++) {
            for (int col = 0; col < grid[row].length; col++) {
                if (grid[row][col] && !seen[row][col]) {
                    components.add(component(grid, seen, row, col));
                }
            }
        }
        return components;
    }

    private static List<int[]> component(boolean[][] grid, boolean[][] seen, int startRow, int startCol) {
        List<int[]> component = new ArrayList<>();
        Deque<int[]> open = new ArrayDeque<>();
        seen[startRow][startCol] = true;
        open.push(new int[]{startRow, startCol});
        while (!open.isEmpty()) {
            int[] cell = open.pop();
            component.add(cell);
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    int row = cell[0] + dr;
                    int col = cell[1] + dc;
                    if (row >= 0 && row < grid.length && col >= 0 && col < grid[row].length && grid[row][col]
                            && !seen[row][col]) {
                        seen[row][col] = true;
                        open.push(new int[]{row, col});
                    }
                }
            }
        }
        return component;
    }

    private static boolean[][] trim(byte[] mask, int width, int height, int top, int left, int bottom, int right) {
        int t = Math.clamp(top, 0, height);
        int l = Math.clamp(left, 0, width);
        int b = Math.clamp(bottom, t, height);
        int r = Math.clamp(right, l, width);
        int minRow = Integer.MAX_VALUE;
        int maxRow = -1;
        int minCol = Integer.MAX_VALUE;
        int maxCol = -1;
        for (int row = t; row < b; row++) {
            for (int col = l; col < r; col++) {
                if (mask[row * width + col] != 0) {
                    minRow = Math.min(minRow, row);
                    maxRow = Math.max(maxRow, row);
                    minCol = Math.min(minCol, col);
                    maxCol = Math.max(maxCol, col);
                }
            }
        }
        if (maxRow < 0) {
            return new boolean[0][0];
        }
        boolean[][] grid = new boolean[maxRow - minRow + 1][maxCol - minCol + 1];
        for (int row = minRow; row <= maxRow; row++) {
            for (int col = minCol; col <= maxCol; col++) {
                grid[row - minRow][col - minCol] = mask[row * width + col] != 0;
            }
        }
        return grid;
    }
}
