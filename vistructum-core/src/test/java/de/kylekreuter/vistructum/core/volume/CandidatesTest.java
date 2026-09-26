package de.kylekreuter.vistructum.core.volume;

import de.kylekreuter.vistructum.core.region.ChunkColumn;
import de.kylekreuter.vistructum.core.region.Section;
import de.kylekreuter.vistructum.core.scene.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CandidatesTest {

    private static final List<String> PALETTE = List.of("minecraft:air", "minecraft:stone", "minecraft:red_wool");

    @Test
    void keepsRareMaterialsAndDropsFillerAndAir() {
        int[] indices = new int[Section.VOLUME];
        for (int i = 0; i < 2048; i++) {
            indices[i] = 1;
        }
        indices[Section.index(3, 12, 4)] = 2;
        ChunkColumn column = column(1, 2, new Section(3, PALETTE, pack(indices)));
        Map<String, List<BlockPos>> candidates = new Candidates(0.25, name -> true).of(column);
        assertEquals(Map.of("minecraft:red_wool", List.of(new BlockPos(16 + 3, 48 + 12, 32 + 4))), candidates);
    }

    @Test
    void fillerShareIsPerSection() {
        int[] indices = new int[Section.VOLUME];
        for (int i = 0; i < 100; i++) {
            indices[i] = 2;
        }
        ChunkColumn column = column(0, 0, new Section(0, PALETTE, pack(indices)));
        assertTrue(new Candidates(0.05, name -> true).of(column).containsKey("minecraft:red_wool"));
        assertFalse(new Candidates(0.01, name -> true).of(column).containsKey("minecraft:red_wool"));
    }

    @Test
    void rejectedMaterialsAreNeverCandidates() {
        int[] indices = new int[Section.VOLUME];
        indices[0] = 2;
        ChunkColumn column = column(0, 0, new Section(0, PALETTE, pack(indices)));
        assertTrue(new Candidates(0.25, name -> !name.endsWith("wool")).of(column).isEmpty());
    }

    private static ChunkColumn column(int chunkX, int chunkZ, Section section) {
        return new ChunkColumn(chunkX, chunkZ, 4189, ChunkColumn.FULL, List.of(section), Optional.empty());
    }

    private static long[] pack(int[] indices) {
        long[] data = new long[Section.VOLUME / 16];
        for (int i = 0; i < indices.length; i++) {
            data[i / 16] |= (long) indices[i] << ((i % 16) * 4);
        }
        return data;
    }
}
