package de.kylekreuter.vistructum.core.volume;

import de.kylekreuter.vistructum.core.region.ChunkColumn;
import de.kylekreuter.vistructum.core.region.PaletteEntry;
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
        addTo(byMaterial, column);
        return byMaterial;
    }

    public void addTo(Map<String, List<BlockPos>> byMaterial, ChunkColumn column) {
        int fillerLimit = (int) Math.floor(fillerShare * Section.VOLUME);
        int baseX = column.chunkX() << 4;
        int baseZ = column.chunkZ() << 4;
        for (Section section : column.sections()) {
            List<PaletteEntry> palette = section.palette();
            if (palette.size() == 1) {
                continue;
            }
            String[] names = new String[palette.size()];
            boolean any = false;
            for (int i = 0; i < palette.size(); i++) {
                String name = palette.get(i).name();
                if (!AIR.contains(name) && accepted.test(name)) {
                    names[i] = name;
                    any = true;
                }
            }
            if (!any) {
                continue;
            }
            int[] indices = section.indices();
            int[] perEntry = new int[names.length];
            for (int index : indices) {
                perEntry[index]++;
            }
            Map<String, Integer> counts = new HashMap<>();
            for (int i = 0; i < names.length; i++) {
                if (names[i] != null) {
                    counts.merge(names[i], perEntry[i], Integer::sum);
                }
            }
            for (int i = 0; i < names.length; i++) {
                if (names[i] != null && counts.getOrDefault(names[i], 0) > fillerLimit) {
                    names[i] = null;
                }
            }
            int baseY = section.y() << 4;
            for (int i = 0; i < Section.VOLUME; i++) {
                String name = names[indices[i]];
                if (name != null) {
                    byMaterial.computeIfAbsent(name, key -> new ArrayList<>())
                            .add(new BlockPos(baseX + (i & 15), baseY + (i >> 8), baseZ + ((i >> 4) & 15)));
                }
            }
        }
    }
}
