package de.kylekreuter.vistructum.core.scan;

import de.kylekreuter.vistructum.core.region.ChunkColumn;
import de.kylekreuter.vistructum.core.region.PaletteEntry;
import de.kylekreuter.vistructum.core.region.Section;
import de.kylekreuter.vistructum.inference.SurfaceScene;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColumnSurfaceTest {

    private static final PaletteEntry STONE = new PaletteEntry("minecraft:stone", "");
    private static final PaletteEntry GRASS = new PaletteEntry("minecraft:short_grass", "");
    private static final BlockStates STATES = entry -> {
        if (entry.equals(STONE)) {
            return new BlockStates.StateInfo((short) 1, true, true, 100);
        }
        if (entry.equals(GRASS)) {
            return new BlockStates.StateInfo((short) 2, false, false, 200);
        }
        return new BlockStates.StateInfo((short) 0, false, false, 0);
    };

    @Test
    void startsAtHeightmapAndSkipsNonSurfaceBlocks() {
        SurfaceScene scene = new ColumnSurface(0, STATES).sample(Map.of(ChunkKey.of(0, 0), column(true)), 0, 0, 32, 16);
        int inside = scene.index(3, 5);
        assertEquals(1, scene.blocks()[inside]);
        assertEquals(3, scene.heights()[inside]);
        assertEquals(100, scene.luminance()[inside] & 0xff);
        assertEquals(SurfaceScene.UNKNOWN, scene.blocks()[scene.index(3, 20)]);
    }

    @Test
    void withoutHeightmapStartsAtTopSection() {
        SurfaceScene scene = new ColumnSurface(0, STATES).sample(Map.of(ChunkKey.of(0, 0), column(false)), 0, 0, 16, 16);
        assertEquals(3, scene.heights()[scene.index(7, 7)]);
    }

    @Test
    void tileOffsetMapsWorldToSceneCoordinates() {
        SurfaceScene scene = new ColumnSurface(0, STATES).sample(Map.of(ChunkKey.of(0, 0), column(true)), 8, 4, 16, 16);
        assertEquals(1, scene.blocks()[scene.index(0, 0)]);
        assertEquals(SurfaceScene.UNKNOWN, scene.blocks()[scene.index(0, 8)]);
        assertEquals(SurfaceScene.UNKNOWN, scene.blocks()[scene.index(12, 0)]);
    }

    private static ChunkColumn column(boolean withHeightmap) {
        int[] indices = new int[Section.VOLUME];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 4; y++) {
                    indices[Section.index(x, y, z)] = 1;
                }
                indices[Section.index(x, 4, z)] = 2;
            }
        }
        Section section = Section.pack(0, List.of(PaletteEntry.AIR, STONE, GRASS), indices);
        return new ChunkColumn(0, 0, 4189, ChunkColumn.FULL, List.of(section),
                withHeightmap ? Optional.of(heightmap(5)) : Optional.empty());
    }

    private static long[] heightmap(int value) {
        long[] data = new long[37];
        for (int i = 0; i < 256; i++) {
            data[i / 7] |= (long) value << ((i % 7) * 9);
        }
        return data;
    }
}
