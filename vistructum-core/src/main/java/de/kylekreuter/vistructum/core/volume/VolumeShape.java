package de.kylekreuter.vistructum.core.volume;

import de.kylekreuter.vistructum.core.scene.BlockPos;
import de.kylekreuter.vistructum.core.scene.Projection;

public record VolumeShape(String material, int blocks, BlockPos min, Projection projection) {
}
