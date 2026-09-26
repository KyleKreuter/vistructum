package de.kylekreuter.vistructum.core.volume;

import de.kylekreuter.vistructum.core.region.ChunkColumn;
import de.kylekreuter.vistructum.core.region.Section;
import de.kylekreuter.vistructum.core.scene.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class Candidates {

    private static final Set<String> AIR = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");

    private final double fillerShare;
    private final Predicate<String> accepted;

    public Candidates(double fillerShare, Predicate<String> accepted) {
        if (fillerShare <= 0 || fillerShare > 1) {
            throw new IllegalArgumentException("fillerShare must be in (0, 1], got " + fillerShare);
        }
        this.fillerShare = fillerShare;
        this.accepted = accepted;
    }

    public Map<String, List<BlockPos>> of(ChunkColumn column) {
        Map<String, List<BlockPos>> byMaterial = new HashMap<>();
        int fillerLimit = (int) Math.floor(fillerShare * Section.VOLUME);
        int baseX = column.chunkX() << 4;
        int baseZ = column.chunkZ() << 4;
        for (Section section : column.sections()) {
            List<String> palette = section.palette();
            boolean[] kept = new boolean[palette.size()];
            boolean any = false;
            for (int i = 0; i < palette.size(); i++) {
                String name = palette.get(i);
                kept[i] = !AIR.contains(name) && accepted.test(name);
                any |= kept[i];
            }
            if (!any || palette.size() == 1) {
                continue;
            }
            int[] indices = section.indices();
            int[] counts = new int[palette.size()];
            for (int index : indices) {
                counts[index]++;
            }
            for (int i = 0; i < palette.size(); i++) {
                kept[i] &= counts[i] > 0 && counts[i] <= fillerLimit;
            }
            int baseY = section.y() << 4;
            for (int i = 0; i < Section.VOLUME; i++) {
                int paletteIndex = indices[i];
                if (kept[paletteIndex]) {
                    byMaterial.computeIfAbsent(palette.get(paletteIndex), name -> new ArrayList<>())
                            .add(new BlockPos(baseX + (i & 15), baseY + (i >> 8), baseZ + ((i >> 4) & 15)));
                }
            }
        }
        return byMaterial;
    }
}
