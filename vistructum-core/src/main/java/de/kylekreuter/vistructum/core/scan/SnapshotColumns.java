package de.kylekreuter.vistructum.core.scan;

import de.kylekreuter.vistructum.core.region.ChunkColumn;
import de.kylekreuter.vistructum.core.region.PaletteEntry;
import de.kylekreuter.vistructum.core.region.Section;
import org.bukkit.ChunkSnapshot;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SnapshotColumns {

    private SnapshotColumns() {
    }

    public static ChunkColumn of(ChunkSnapshot snapshot, int minY, int maxY, int dataVersion) {
        List<Section> sections = new ArrayList<>();
        for (int sectionY = Math.floorDiv(minY, Section.SIDE); sectionY <= Math.floorDiv(maxY - 1, Section.SIDE);
             sectionY++) {
            if (snapshot.isSectionEmpty(sectionY - Math.floorDiv(minY, Section.SIDE))) {
                continue;
            }
            sections.add(section(snapshot, sectionY));
        }
        return new ChunkColumn(snapshot.getX(), snapshot.getZ(), dataVersion, ChunkColumn.FULL, sections,
                Optional.empty());
    }

    private static Section section(ChunkSnapshot snapshot, int sectionY) {
        Map<BlockData, Integer> ids = new HashMap<>();
        List<PaletteEntry> palette = new ArrayList<>();
        int[] indices = new int[Section.VOLUME];
        int baseY = sectionY * Section.SIDE;
        for (int y = 0; y < Section.SIDE; y++) {
            for (int z = 0; z < Section.SIDE; z++) {
                for (int x = 0; x < Section.SIDE; x++) {
                    BlockData data = snapshot.getBlockData(x, baseY + y, z);
                    indices[Section.index(x, y, z)] = ids.computeIfAbsent(data, key -> {
                        palette.add(PaletteEntry.parse(key.getAsString()));
                        return palette.size() - 1;
                    });
                }
            }
        }
        return Section.pack(sectionY, palette, indices);
    }
}
