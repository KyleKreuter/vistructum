package de.kylekreuter.vistructum.core.tracking;

import de.kylekreuter.vistructum.core.scene.BlockPos;

import java.util.Objects;

public record WorldPosition(String world, BlockPos pos) {

    public WorldPosition {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(pos, "pos");
    }
}
