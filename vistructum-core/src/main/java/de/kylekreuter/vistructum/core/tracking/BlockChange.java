package de.kylekreuter.vistructum.core.tracking;

import de.kylekreuter.vistructum.core.scene.BlockPos;

import java.util.Objects;
import java.util.UUID;

public record BlockChange(String world, BlockPos pos, UUID player, long changedAt, long reportedAt) {

    public BlockChange {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(player, "player");
    }

    public boolean unreported() {
        return changedAt > reportedAt;
    }
}
