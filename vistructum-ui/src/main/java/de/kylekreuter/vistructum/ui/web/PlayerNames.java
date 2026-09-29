package de.kylekreuter.vistructum.ui.web;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PlayerNames {

    private final GameServer game;
    private final Handoff handoff;

    public PlayerNames(GameServer game, Handoff handoff) {
        this.game = Objects.requireNonNull(game, "game");
        this.handoff = Objects.requireNonNull(handoff, "handoff");
    }

    public CompletableFuture<Map<UUID, Optional<String>>> of(Collection<UUID> players) {
        if (players.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }
        return handoff.onMain(() -> {
            Map<UUID, Optional<String>> names = new HashMap<>();
            players.forEach(player -> names.put(player, game.playerName(player)));
            return names;
        });
    }
}
