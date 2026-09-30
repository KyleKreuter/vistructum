package de.kylekreuter.vistructum.core.history;

import de.kylekreuter.vistructum.api.BlockAction;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record HistoryEntry(int x, int y, int z, String playerName, Optional<UUID> player, BlockAction action,
                           String blockData, long changedAt) {

    public HistoryEntry {
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(blockData, "blockData");
    }
}
