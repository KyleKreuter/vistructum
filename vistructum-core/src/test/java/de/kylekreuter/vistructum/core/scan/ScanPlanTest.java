package de.kylekreuter.vistructum.core.scan;

import de.kylekreuter.vistructum.api.ModelContract;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanPlanTest {

    @Test
    void everyWindowOverAChunkBelongsToAPlannedTile() {
        List<int[]> chunks = List.of(new int[]{0, 0}, new int[]{-1, 3}, new int[]{11, -12}, new int[]{12, 12});
        List<ScanPlan.Tile> tiles = ScanPlan.tiles(chunks);
        int grid = ModelContract.GRID_SIZE;
        int stride = ModelContract.WINDOW_STRIDE;
        for (int[] chunk : chunks) {
            for (int bx = chunk[0] << 4; bx < (chunk[0] << 4) + 16; bx++) {
                // every window origin on the global grid that covers this block must be a window of some tile
                for (int ox = Math.floorDiv(bx - grid + 1 + stride - 1, stride) * stride; ox <= bx; ox += stride) {
                    int origin = ox;
                    assertTrue(tiles.stream().anyMatch(t -> origin >= t.originX()
                                    && origin <= t.originX() + ScanPlan.TILE_SIZE - grid),
                            "window at x=" + origin + " over block " + bx + " has no tile");
                }
            }
        }
    }

    @Test
    void tilesFollowTheWorldWideWindowGrid() {
        for (ScanPlan.Tile tile : ScanPlan.tiles(List.of(new int[]{-40, 7}, new int[]{3, -2}))) {
            assertEquals(0, Math.floorMod(tile.originX(), ModelContract.WINDOW_STRIDE));
            assertEquals(0, Math.floorMod(tile.originZ(), ModelContract.WINDOW_STRIDE));
            assertEquals(0, Math.floorMod(tile.originX(), ScanPlan.TILE_STEP));
        }
    }

    @Test
    void aChunkInsideOneTileNeedsFewTilesAndTilesAreDeduplicated() {
        List<int[]> chunks = new ArrayList<>();
        for (int x = 4; x < 8; x++) {
            for (int z = 4; z < 8; z++) {
                chunks.add(new int[]{x, z});
            }
        }
        // blocks 64..127 on both axes: windows starting 24..120 -> tile 0 only
        assertEquals(List.of(new ScanPlan.Tile(0, 0)), ScanPlan.tiles(chunks));
    }
}
