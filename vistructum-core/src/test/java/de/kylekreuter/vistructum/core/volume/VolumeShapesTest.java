package de.kylekreuter.vistructum.core.volume;

import de.kylekreuter.vistructum.core.region.ChunkColumn;
import de.kylekreuter.vistructum.core.region.PaletteEntry;
import de.kylekreuter.vistructum.core.region.Section;
import de.kylekreuter.vistructum.core.scene.Axis;
import de.kylekreuter.vistructum.core.scene.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VolumeShapesTest {

    private static final int[][] SYMBOL = {{0, 4}, {1, 4}, {2, 4}, {3, 4}, {4, 4}, {5, 4}, {6, 4}, {7, 4}, {8, 4},
            {4, 0}, {4, 1}, {4, 2}, {4, 3}, {4, 5}, {4, 6}, {4, 7}, {4, 8},
            {0, 0}, {1, 0}, {2, 0}, {3, 0}, {8, 1}, {8, 2}, {8, 3}, {8, 0},
            {5, 8}, {6, 8}, {7, 8}, {8, 8}, {0, 5}, {0, 6}, {0, 7}, {0, 8}};

    private final VolumeShapes shapes = new VolumeShapes(new VolumeSettings(0.10, 3, 12, 256, 0.9, 5), name -> true);

    @Test
    void findsFlatSymbolInsideFillerAndIgnoresScatteredOre() {
        List<PaletteEntry> palette = List.of(new PaletteEntry("minecraft:stone", ""),
                new PaletteEntry("minecraft:red_wool", ""), new PaletteEntry("minecraft:coal_ore", ""));
        int[] indices = new int[Section.VOLUME];
        for (int[] cell : SYMBOL) {
            indices[Section.index(2 + cell[0], 5, 3 + cell[1])] = 1;
        }
        for (int i = 0; i < 40; i++) {
            indices[Section.index(i % 4, 10 + (i * 7) % 5, 11 + (i * 3) % 4)] = 2;
        }
        ChunkColumn column = new ChunkColumn(1, 0, 4189, ChunkColumn.FULL, List.of(Section.pack(0, palette, indices)),
                Optional.empty());
        Map<String, List<BlockPos>> candidates = shapes.candidates(List.of(column));
        assertEquals(33, candidates.get("minecraft:red_wool").size());
        List<VolumeShape> found = shapes.shapes("minecraft:red_wool", candidates.get("minecraft:red_wool"));
        assertEquals(1, found.size());
        assertEquals(Axis.Y, found.getFirst().projection().axis());
        assertEquals(new BlockPos(18, 5, 3), found.getFirst().min());
        assertEquals(List.of(), shapes.shapes("minecraft:coal_ore", candidates.get("minecraft:coal_ore")));
    }

    @Test
    void dropsClustersBelowMinimumSize() {
        List<BlockPos> tiny = List.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), new BlockPos(2, 0, 0));
        assertEquals(List.of(), shapes.shapes("minecraft:red_wool", tiny));
    }
}
