package de.kylekreuter.vistructum.core.volume;

import de.kylekreuter.vistructum.core.region.ChunkColumn;
import de.kylekreuter.vistructum.core.scene.Axis;
import de.kylekreuter.vistructum.core.scene.BlockPos;
import de.kylekreuter.vistructum.core.scene.MaskProjector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class VolumeShapes {

    private final VolumeSettings settings;
    private final Candidates candidates;
    private final PrismAxes prism;
    private final MaskProjector projector = new MaskProjector();

    public VolumeShapes(VolumeSettings settings, Predicate<String> solid) {
        this.settings = settings;
        this.candidates = new Candidates(settings.fillerShare(), solid);
        this.prism = new PrismAxes(settings.prismFill(), settings.minSide());
    }

    public Map<String, List<BlockPos>> candidates(Collection<ChunkColumn> columns) {
        Map<String, List<BlockPos>> byMaterial = new HashMap<>();
        for (ChunkColumn column : columns) {
            candidates.addTo(byMaterial, column);
        }
        return byMaterial;
    }

    public List<VolumeShape> shapes(String material, List<BlockPos> positions) {
        List<VolumeShape> shapes = new ArrayList<>();
        for (List<BlockPos> cluster : PositionClusters.of(positions, settings.linkDistance())) {
            if (cluster.size() < settings.minBlocks()) {
                continue;
            }
            BlockPos min = min(cluster);
            if (exceeds(cluster, min)) {
                continue;
            }
            for (Axis axis : prism.of(cluster)) {
                projector.project(axis, cluster)
                        .ifPresent(projection -> shapes.add(new VolumeShape(material, cluster.size(), min, projection)));
            }
        }
        return shapes;
    }

    private boolean exceeds(List<BlockPos> cluster, BlockPos min) {
        int limit = settings.maxExtent();
        for (BlockPos pos : cluster) {
            if (pos.x() - min.x() >= limit || pos.y() - min.y() >= limit || pos.z() - min.z() >= limit) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos min(List<BlockPos> cluster) {
        int x = Integer.MAX_VALUE;
        int y = Integer.MAX_VALUE;
        int z = Integer.MAX_VALUE;
        for (BlockPos pos : cluster) {
            x = Math.min(x, pos.x());
            y = Math.min(y, pos.y());
            z = Math.min(z, pos.z());
        }
        return new BlockPos(x, y, z);
    }
}
