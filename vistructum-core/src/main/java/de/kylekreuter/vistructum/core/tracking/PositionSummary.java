package de.kylekreuter.vistructum.core.tracking;

import de.kylekreuter.vistructum.core.scene.BlockPos;

import java.util.Objects;

public record PositionSummary(BlockPos pos, long newest, boolean unreported) {

    public PositionSummary {
        Objects.requireNonNull(pos, "pos");
    }
}
