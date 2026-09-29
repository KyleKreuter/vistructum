package de.kylekreuter.vistructum.ui.web.view;

import de.kylekreuter.vistructum.api.BlockVolume;
import de.kylekreuter.vistructum.api.FindingTerrain;

public record TerrainView(EvidenceView.VolumeView blocks, OriginView sceneOrigin) {

    public static TerrainView of(FindingTerrain terrain) {
        BlockVolume blocks = terrain.blocks();
        return new TerrainView(new EvidenceView.VolumeView(blocks.minX(), blocks.minY(), blocks.minZ(), blocks.sizeX(),
                blocks.sizeY(), blocks.sizeZ(), blocks.palette(), blocks.cells()),
                terrain.sceneOrigin().map(origin -> new OriginView(origin.x(), origin.z())).orElse(null));
    }

    public record OriginView(long x, long z) {
    }
}
