package de.kylekreuter.vistructum.core.history;

import de.kylekreuter.vistructum.api.BlockBox;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public record RollbackPlan(Set<String> playerNames, Duration since) {

    private static final Duration SLACK = Duration.ofSeconds(1);

    public RollbackPlan {
        playerNames = Set.copyOf(playerNames);
        Objects.requireNonNull(since, "since");
    }

    public static Optional<RollbackPlan> of(List<HistoryEntry> entries, BlockBox box, Set<UUID> players, Instant now) {
        Set<String> names = new TreeSet<>();
        long first = Long.MAX_VALUE;
        for (HistoryEntry entry : entries) {
            if (entry.player().filter(players::contains).isPresent() && box.contains(entry.x(), entry.y(), entry.z())) {
                names.add(entry.playerName());
                first = Math.min(first, entry.changedAt());
            }
        }
        if (names.isEmpty()) {
            return Optional.empty();
        }
        Duration since = Duration.between(Instant.ofEpochMilli(first), now).plus(SLACK);
        return Optional.of(new RollbackPlan(names, since.isNegative() ? SLACK : since));
    }
}
