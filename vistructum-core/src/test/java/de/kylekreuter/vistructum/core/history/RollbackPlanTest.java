package de.kylekreuter.vistructum.core.history;

import de.kylekreuter.vistructum.api.BlockAction;
import de.kylekreuter.vistructum.api.BlockBox;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RollbackPlanTest {

    private static final BlockBox BOX = new BlockBox(0, 60, 0, 4, 60, 4);
    private static final UUID ALICE = UUID.fromString("e3e04125-846a-3ff7-9f1f-aa61ff8eb6b7");
    private static final UUID BOB = UUID.fromString("0f5b2c1e-3a4d-4e6f-8a9b-1c2d3e4f5a6b");
    private static final String WOOL = "minecraft:red_wool";
    private static final String GRASS = "minecraft:grass_block[snowy=false]";

    @Test
    void placedBlocksOfThePlayersTurnBackIntoAir() {
        RollbackPlan plan = RollbackPlan.of(List.of(entry(1, ALICE, BlockAction.PLACE, WOOL, 100)), BOX, Set.of(ALICE));

        assertEquals(List.of(new BlockRestore(1, 60, 1, WOOL, RollbackPlan.AIR)), plan.restores());
        assertEquals(0, plan.skipped());
    }

    @Test
    void brokenBlocksReturnInTheirStateBeforeTheFirstChangeOfThePlayers() {
        RollbackPlan plan = RollbackPlan.of(List.of(
                entry(1, BOB, BlockAction.PLACE, GRASS, 50),
                entry(1, ALICE, BlockAction.BREAK, GRASS, 100),
                entry(1, ALICE, BlockAction.PLACE, WOOL, 110)), BOX, Set.of(ALICE));

        assertEquals(List.of(new BlockRestore(1, 60, 1, WOOL, GRASS)), plan.restores());
    }

    @Test
    void blocksChangedByOthersAfterThePlayersAreSkipped() {
        RollbackPlan plan = RollbackPlan.of(List.of(
                entry(1, ALICE, BlockAction.PLACE, WOOL, 100),
                entry(1, BOB, BlockAction.BREAK, WOOL, 200),
                entry(2, BOB, BlockAction.PLACE, WOOL, 100)), BOX, Set.of(ALICE));

        assertEquals(List.of(), plan.restores());
        assertEquals(1, plan.skipped());
    }

    @Test
    void blocksOutsideTheBoxAndUnchangedBlocksAreLeftAlone() {
        RollbackPlan plan = RollbackPlan.of(List.of(
                entry(9, ALICE, BlockAction.PLACE, WOOL, 100),
                entry(2, ALICE, BlockAction.PLACE, WOOL, 100),
                entry(2, ALICE, BlockAction.BREAK, WOOL, 120),
                new HistoryEntry(3, 60, 1, "Stranger", Optional.empty(), BlockAction.PLACE, WOOL, 100)),
                BOX, Set.of(ALICE));

        assertEquals(List.of(), plan.restores());
        assertEquals(0, plan.skipped());
    }

    @Test
    void restoreComparesTheLiveMaterial() {
        BlockRestore restore = new BlockRestore(1, 60, 1, "minecraft:oak_stairs[facing=north]", GRASS);

        assertEquals(BlockRestore.Outcome.RESTORE, restore.against("minecraft:oak_stairs"));
        assertEquals(BlockRestore.Outcome.ALREADY_RESTORED, restore.against("minecraft:grass_block"));
        assertEquals(BlockRestore.Outcome.SKIP, restore.against("minecraft:stone"));
    }

    private static HistoryEntry entry(int x, UUID player, BlockAction action, String blockData, long changedAt) {
        return new HistoryEntry(x, 60, 1, player.toString().substring(0, 8), Optional.of(player), action, blockData,
                changedAt);
    }
}
