package de.kylekreuter.vistructum.core.scan;

import de.kylekreuter.vistructum.api.BlockBox;
import de.kylekreuter.vistructum.api.BlockVolume;
import de.kylekreuter.vistructum.core.region.ChunkColumn;
import de.kylekreuter.vistructum.core.region.PaletteEntry;
import de.kylekreuter.vistructum.core.region.Section;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainCutTest {

    private static final PaletteEntry STONE = new PaletteEntry("minecraft:stone", "");
    private static final PaletteEntry LOG = PaletteEntry.of("minecraft:oak_log", Map.of("axis", "y"));
    private static final PaletteEntry LEAVES = PaletteEntry.of("minecraft:oak_leaves",
            Map.of("distance", "1", "persistent", "false"));
    private static final PaletteEntry FLOWER = new PaletteEntry("minecraft:poppy", "");
    private static final BlockStates STATES = entry -> new BlockStates.StateInfo((short) 0,
            entry.equals(STONE) || entry.equals(LOG) || entry.equals(LEAVES), false, 0);
    private static final BlockBox TILE = new BlockBox(0, 0, 0, 255, 0, 255);

    @Test
    void keepsEveryBlockBetweenTheGroundAndTheCanopy() {
        TerrainCut cut = new TerrainCut(-64, 319, STATES);
        BlockVolume volume = cut.cut(Map.of(ChunkKey.of(0, 0), forest()), new BlockBox(4, 3, 4, 6, 9, 6), TILE)
                .orElseThrow();
        assertEquals(0, volume.minX());
        assertEquals(0, volume.minZ());
        assertEquals(23, volume.sizeX());
        assertEquals(23, volume.sizeZ());
        assertEquals(3 - TerrainCut.DEPTH, volume.minY());
        assertEquals(9, volume.minY() + volume.sizeY() - 1);
        assertEquals("minecraft:oak_log[axis=y]", volume.blockAt(5, 6, 5));
        assertEquals("minecraft:oak_leaves[distance=1,persistent=false]", volume.blockAt(5, 9, 5));
        assertEquals("minecraft:poppy", volume.blockAt(1, 4, 1));
        assertEquals("minecraft:stone", volume.blockAt(1, 3, 1));
        assertEquals("minecraft:air", volume.blockAt(20, 3, 20));
    }

    @Test
    void spansAreCappedAroundTheFocusAndClippedToTheTile() {
        assertArrayEquals(new int[]{0, 26}, TerrainCut.span(4, 10, 0, 255));
        assertArrayEquals(new int[]{137, 264}, TerrainCut.span(0, 400, -1000, 1000));
    }

    @Test
    void emptyChunksYieldNothing() {
        Optional<BlockVolume> none = new TerrainCut(0, 255, STATES).cut(Map.of(), new BlockBox(0, 0, 0, 3, 3, 3), TILE);
        assertTrue(none.isEmpty());
    }

    private static ChunkColumn forest() {
        int[] indices = new int[Section.VOLUME];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 4; y++) {
                    indices[Section.index(x, y, z)] = 1;
                }
            }
        }
        indices[Section.index(1, 4, 1)] = 4;
        for (int y = 4; y < 9; y++) {
            indices[Section.index(5, y, 5)] = 2;
        }
        for (int x = 4; x <= 6; x++) {
            for (int z = 4; z <= 6; z++) {
                indices[Section.index(x, 9, z)] = 3;
            }
        }
        Section section = Section.pack(0, List.of(PaletteEntry.AIR, STONE, LOG, LEAVES, FLOWER), indices);
        return new ChunkColumn(0, 0, 4189, ChunkColumn.FULL, List.of(section), Optional.empty());
    }
}
