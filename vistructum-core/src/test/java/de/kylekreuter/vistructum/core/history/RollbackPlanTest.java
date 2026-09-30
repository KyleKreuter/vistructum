package de.kylekreuter.vistructum.core.history;

import de.kylekreuter.vistructum.api.BlockAction;
import de.kylekreuter.vistructum.api.BlockBox;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RollbackPlanTest {

    private static final BlockBox BOX = new BlockBox(0, 60, 0, 4, 60, 4);
    private static final UUID ALICE = UUID.fromString("e3e04125-846a-3ff7-9f1f-aa61ff8eb6b7");
    private static final UUID BOB = UUID.fromString("0f5b2c1e-3a4d-4e6f-8a9b-1c2d3e4f5a6b");
    private static final Instant NOW = Instant.ofEpochMilli(100_000);

    @Test
    void planCoversTheFindingPlayersSinceTheirFirstChangeInTheBox() {
        List<HistoryEntry> entries = List.of(
                entry(1, ALICE, "Alice", 40_000),
                entry(2, ALICE, "Alice", 70_000),
                entry(3, BOB, "Bob", 10_000),
                entry(9, ALICE, "Alice", 5_000));

        RollbackPlan plan = RollbackPlan.of(entries, BOX, Set.of(ALICE), NOW).orElseThrow();

        assertEquals(Set.of("Alice"), plan.playerNames());
        assertEquals(Duration.ofSeconds(61), plan.since());
    }

    @Test
    void noPlanWithoutChangesOfTheFindingPlayers() {
        List<HistoryEntry> entries = List.of(entry(1, BOB, "Bob", 10_000),
                new HistoryEntry(2, 60, 1, "Stranger", Optional.empty(), BlockAction.PLACE, "minecraft:stone", 20_000));

        assertEquals(Optional.empty(), RollbackPlan.of(entries, BOX, Set.of(ALICE), NOW));
    }

    @Test
    void changesFromTheFutureStillReachBack() {
        RollbackPlan plan = RollbackPlan.of(List.of(entry(1, ALICE, "Alice", 200_000)), BOX, Set.of(ALICE), NOW)
                .orElseThrow();

        assertEquals(Duration.ofSeconds(1), plan.since());
    }

    private static HistoryEntry entry(int x, UUID player, String name, long changedAt) {
        return new HistoryEntry(x, 60, 1, name, Optional.of(player), BlockAction.PLACE, "minecraft:stone", changedAt);
    }
}
