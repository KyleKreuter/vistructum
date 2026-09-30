package de.kylekreuter.vistructum.core.mask;

import de.kylekreuter.vistructum.core.scene.Axis;
import de.kylekreuter.vistructum.core.scene.BlockPos;
import de.kylekreuter.vistructum.core.tracking.Cluster;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

record MaskLayer(Optional<String> material, Set<BlockPos> placed, Set<BlockPos> carved) {

    MaskLayer {
        placed = Set.copyOf(placed);
        carved = Set.copyOf(carved);
    }

    Set<BlockPos> positions() {
        Set<BlockPos> positions = new HashSet<>(placed);
        positions.addAll(carved);
        return positions;
    }

    String detail(Axis axis) {
        return material.map(name -> "Material " + name.replace("minecraft:", "") + ", Achse " + axis)
                .orElse("Achse " + axis);
    }

    static List<MaskLayer> of(Cluster cluster, Set<BlockPos> placed, Set<BlockPos> carved, int minBlocks) {
        MaskLayer all = new MaskLayer(Optional.empty(), placed, carved);
        int total = all.positions().size();
        if (total < minBlocks) {
            return List.of();
        }
        Map<String, Set<BlockPos>> placedPerMaterial = perMaterial(cluster, placed);
        Map<String, Set<BlockPos>> carvedPerMaterial = perMaterial(cluster, carved);
        Set<String> materials = new TreeSet<>(placedPerMaterial.keySet());
        materials.addAll(carvedPerMaterial.keySet());
        List<MaskLayer> layers = new ArrayList<>(List.of(all));
        for (String material : materials) {
            MaskLayer layer = new MaskLayer(Optional.of(material), placedPerMaterial.getOrDefault(material, Set.of()),
                    carvedPerMaterial.getOrDefault(material, Set.of()));
            int size = layer.positions().size();
            if (size >= minBlocks && size < total) {
                layers.add(layer);
            }
        }
        return layers;
    }

    private static Map<String, Set<BlockPos>> perMaterial(Cluster cluster, Set<BlockPos> positions) {
        Map<String, Set<BlockPos>> perMaterial = new HashMap<>();
        for (BlockPos pos : positions) {
            perMaterial.computeIfAbsent(cluster.blocks().get(pos).material(), material -> new HashSet<>()).add(pos);
        }
        return perMaterial;
    }
}
