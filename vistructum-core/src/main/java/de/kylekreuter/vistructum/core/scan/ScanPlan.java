package de.kylekreuter.vistructum.core.scan;

import de.kylekreuter.vistructum.api.ModelContract;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * Splits the generated part of a world into square tiles for the fullscan model.
 *
 * <p>The sidecar puts its 64-block windows every 24 blocks from a tile's corner. Tiles of 256 blocks every 192
 * blocks (8 strides) therefore continue one world-wide 24-block window grid: each window position belongs to
 * exactly one tile, apart from the shared edge row, so a symbol that fits a window is never cut by a tile border.
 */
public final class ScanPlan {

    public static final int TILE_SIZE = 256;
    public static final int TILE_STEP = TILE_SIZE - ModelContract.GRID_SIZE;

    static {
        if (TILE_STEP % ModelContract.WINDOW_STRIDE != 0) {
            throw new IllegalStateException("tile step " + TILE_STEP + " breaks the window grid");
        }
    }

    private ScanPlan() {
    }

    /** a tile's minimum x and z in blocks; it spans {@link #TILE_SIZE} blocks on both axes */
    public record Tile(int originX, int originZ) {
    }

    /** chunk coordinates (x, z) of generated chunks -> the tiles whose windows cover every block of them */
    public static List<Tile> tiles(Collection<int[]> chunks) {
        var tiles = new TreeSet<>(Comparator.comparingInt(Tile::originX).thenComparingInt(Tile::originZ));
        for (int[] chunk : chunks) {
            int minX = chunk[0] << 4;
            int minZ = chunk[1] << 4;
            for (int tx = firstTile(minX); tx <= lastTile(minX + 15); tx++) {
                for (int tz = firstTile(minZ); tz <= lastTile(minZ + 15); tz++) {
                    tiles.add(new Tile(tx * TILE_STEP, tz * TILE_STEP));
                }
            }
        }
        return List.copyOf(tiles);
    }

    // a window starting at o covers blocks o .. o + GRID - 1; the tile owning window origin o is floor(o / STEP)
    private static int firstTile(int minBlock) {
        return Math.floorDiv(minBlock - ModelContract.GRID_SIZE + 1, TILE_STEP);
    }

    private static int lastTile(int maxBlock) {
        return Math.floorDiv(maxBlock, TILE_STEP);
    }
}
