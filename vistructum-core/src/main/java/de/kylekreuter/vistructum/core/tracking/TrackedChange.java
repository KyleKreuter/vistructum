package de.kylekreuter.vistructum.core.tracking;

import de.kylekreuter.vistructum.core.scene.BlockPos;

import java.util.Objects;
import java.util.UUID;

public record TrackedChange(String world, BlockPos pos, UUID player, String playerName, ChangeKind kind,
                            String blockData, String previousData, long changedAt) {

    public TrackedChange {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(blockData, "blockData");
        Objects.requireNonNull(previousData, "previousData");
    }

    public static String material(String blockData) {
        int states = blockData.indexOf('[');
        return states < 0 ? blockData : blockData.substring(0, states);
    }
}
