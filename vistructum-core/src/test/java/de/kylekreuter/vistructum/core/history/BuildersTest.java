package de.kylekreuter.vistructum.core.history;

import de.kylekreuter.vistructum.api.BlockAction;
import de.kylekreuter.vistructum.api.BlockBox;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildersTest {

    private static final BlockBox BOX = new BlockBox(0, 60, 0, 4, 60, 4);
    private static final UUID ALICE = UUID.fromString("e3e04125-846a-3ff7-9f1f-aa61ff8eb6b7");
    private static final UUID BOB = UUID.fromString("0f5b2c1e-3a4d-4e6f-8a9b-1c2d3e4f5a6b");
    private static final UUID CAROL = UUID.fromString("7d8e9f0a-1b2c-4d3e-9f4a-5b6c7d8e9f0a");

    @Test
    void thePlayerWhoPlacedTheStandingBlockIsTheBuilder() {
        List<HistoryEntry> entries = List.of(
                entry(1, ALICE, BlockAction.PLACE, 100),
                entry(1, ALICE, BlockAction.BREAK, 200),
                entry(1, BOB, BlockAction.PLACE, 300));

        assertEquals(Set.of(BOB), Builders.of(entries, BOX));
    }

    @Test
    void brokenBlocksAndBlocksOutsideTheBoxDoNotCount() {
        List<HistoryEntry> entries = List.of(
                entry(1, ALICE, BlockAction.PLACE, 100),
                entry(1, BOB, BlockAction.BREAK, 200),
                entry(9, CAROL, BlockAction.PLACE, 100));

        assertEquals(Set.of(), Builders.of(entries, BOX));
    }

    @Test
    void entriesAreOrderedByTimeBeforeTheyAreReplayed() {
        List<HistoryEntry> entries = List.of(
                entry(2, BOB, BlockAction.PLACE, 300),
                entry(2, ALICE, BlockAction.PLACE, 100),
                entry(3, CAROL, BlockAction.PLACE, 100));

        assertEquals(Set.of(BOB, CAROL), Builders.of(entries, BOX));
    }

    @Test
    void playersWithoutAKnownIdAreSkipped() {
        HistoryEntry unknown = new HistoryEntry(1, 60, 1, "Stranger", Optional.empty(), BlockAction.PLACE,
                "minecraft:stone", 100);

        assertEquals(Set.of(ALICE), Builders.of(List.of(unknown, entry(2, ALICE, BlockAction.PLACE, 100)), BOX));
    }

    private static HistoryEntry entry(int x, UUID player, BlockAction action, long changedAt) {
        return new HistoryEntry(x, 60, 1, player.toString().substring(0, 8), Optional.of(player), action,
                "minecraft:stone", changedAt);
    }
}
