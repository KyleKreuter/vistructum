package de.kylekreuter.vistructum.core.tracking;

import de.kylekreuter.vistructum.core.scene.BlockPos;
import de.kylekreuter.vistructum.core.store.Database;
import de.kylekreuter.vistructum.core.store.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockChangeStoreTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final long T = 1_000_000;
    private static final ClusterSettings SETTINGS =
            new ClusterSettings(Duration.ofMillis(1000), 3, Duration.ofMillis(100), 2, 1000);

    @TempDir
    Path directory;
    private Database database;
    private BlockChangeStore store;

    @BeforeEach
    void open() {
        database = TestDatabase.open(directory);
        store = new BlockChangeStore(database);
    }

    @AfterEach
    void close() {
        database.close();
    }

    private void record(BlockPos pos, ChangeKind kind, String blockData, long changedAt) {
        store.record(new TrackedChange("world", pos, PLAYER, "Builder", kind, blockData, "minecraft:air", changedAt));
    }

    @Test
    void everyChangeIsKeptInTheLogWhileTheNewestDecides() throws Exception {
        record(new BlockPos(0, 0, 0), ChangeKind.PLACE, "minecraft:oak_stairs[facing=north]", T);
        record(new BlockPos(0, 0, 0), ChangeKind.BREAK, "minecraft:oak_stairs[facing=north]", T + 10);
        record(new BlockPos(0, 0, 0), ChangeKind.PLACE, "minecraft:stone", T + 20);
        record(new BlockPos(1, 0, 0), ChangeKind.PLACE, "minecraft:stone", T + 20);

        assertEquals(2, store.count().get());
        assertEquals(4L, database.transaction(connection -> {
            try (var rows = connection.createStatement().executeQuery("SELECT count(*) FROM block_events")) {
                return rows.getLong(1);
            }
        }).get());
        Cluster cluster = store.takeReady(SETTINGS, T + 200).get().getFirst();
        assertEquals(Set.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0)), cluster.placed());
        assertEquals("minecraft:stone", cluster.blocks().get(new BlockPos(0, 0, 0)).material());
    }

    @Test
    void readyClusterIsTakenOnceUntilItChangesAgain() throws Exception {
        record(new BlockPos(0, 0, 0), ChangeKind.PLACE, "minecraft:stone", T);
        record(new BlockPos(2, 0, 0), ChangeKind.PLACE, "minecraft:stone", T);

        assertTrue(store.takeReady(SETTINGS, T + 50).get().isEmpty());
        List<Cluster> first = store.takeReady(SETTINGS, T + 100).get();
        assertEquals(1, first.size());
        assertTrue(store.takeReady(SETTINGS, T + 200).get().isEmpty());

        record(new BlockPos(4, 0, 0), ChangeKind.PLACE, "minecraft:stone", T + 250);
        assertTrue(store.takeReady(SETTINGS, T + 300).get().isEmpty());
        List<Cluster> again = store.takeReady(SETTINGS, T + 350).get();
        assertEquals(1, again.size());
        assertEquals(3, again.getFirst().positions().size());
    }

    @Test
    void theNewestChangeOfAPlayerDecidesTheKind() throws Exception {
        record(new BlockPos(0, 0, 0), ChangeKind.PLACE, "minecraft:oak_planks", T);
        record(new BlockPos(0, 0, 0), ChangeKind.BREAK, "minecraft:oak_planks", T + 10);
        record(new BlockPos(1, 0, 0), ChangeKind.PLACE, "minecraft:oak_planks", T + 10);

        Cluster cluster = store.takeReady(SETTINGS, T + 200).get().getFirst();

        assertEquals(Map.of(new BlockPos(0, 0, 0), "minecraft:oak_planks"), cluster.broken());
        assertEquals(Set.of(new BlockPos(1, 0, 0)), cluster.placed());
    }

    @Test
    void expiredChangesAreDeleted() throws Exception {
        record(new BlockPos(0, 0, 0), ChangeKind.PLACE, "minecraft:stone", T);
        record(new BlockPos(1, 0, 0), ChangeKind.PLACE, "minecraft:stone", T + 900);
        assertEquals(2, store.count().get());

        store.takeReady(SETTINGS, T + 1500).get();
        assertEquals(1, store.count().get());
    }

    @Test
    void recordingTheSamePositionAgainRefreshesIt() throws Exception {
        record(new BlockPos(0, 0, 0), ChangeKind.PLACE, "minecraft:stone", T);
        record(new BlockPos(0, 0, 0), ChangeKind.PLACE, "minecraft:stone", T + 900);
        store.takeReady(SETTINGS, T + 1500).get();
        assertEquals(1, store.count().get());
    }

    @Test
    void stateSurvivesReopening() throws Exception {
        record(new BlockPos(0, 0, 0), ChangeKind.PLACE, "minecraft:stone", T);
        record(new BlockPos(2, 0, 0), ChangeKind.PLACE, "minecraft:stone", T);
        store.count().get();
        database.close();

        database = TestDatabase.open(directory);
        store = new BlockChangeStore(database);
        assertEquals(1, store.takeReady(SETTINGS, T + 100).get().size());
    }
}
