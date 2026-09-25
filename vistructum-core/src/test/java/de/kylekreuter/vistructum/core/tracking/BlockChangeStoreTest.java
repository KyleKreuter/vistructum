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

    @Test
    void readyClusterIsTakenOnceUntilItChangesAgain() throws Exception {
        store.record("world", new BlockPos(0, 0, 0), PLAYER, ChangeKind.PLACE, "STONE", T);
        store.record("world", new BlockPos(2, 0, 0), PLAYER, ChangeKind.PLACE, "STONE", T);

        assertTrue(store.takeReady(SETTINGS, T + 50).get().isEmpty());
        List<Cluster> first = store.takeReady(SETTINGS, T + 100).get();
        assertEquals(1, first.size());
        assertTrue(store.takeReady(SETTINGS, T + 200).get().isEmpty());

        store.record("world", new BlockPos(4, 0, 0), PLAYER, ChangeKind.PLACE, "STONE", T + 250);
        assertTrue(store.takeReady(SETTINGS, T + 300).get().isEmpty());
        List<Cluster> again = store.takeReady(SETTINGS, T + 350).get();
        assertEquals(1, again.size());
        assertEquals(3, again.getFirst().positions().size());
    }

    @Test
    void theNewestChangeOfAPlayerDecidesTheKind() throws Exception {
        store.record("world", new BlockPos(0, 0, 0), PLAYER, ChangeKind.PLACE, "OAK_PLANKS", T);
        store.record("world", new BlockPos(0, 0, 0), PLAYER, ChangeKind.BREAK, "OAK_PLANKS", T + 10);
        store.record("world", new BlockPos(1, 0, 0), PLAYER, ChangeKind.PLACE, "OAK_PLANKS", T + 10);

        Cluster cluster = store.takeReady(SETTINGS, T + 200).get().getFirst();

        assertEquals(Map.of(new BlockPos(0, 0, 0), "OAK_PLANKS"), cluster.broken());
        assertEquals(Set.of(new BlockPos(1, 0, 0)), cluster.placed());
    }

    @Test
    void expiredChangesAreDeleted() throws Exception {
        store.record("world", new BlockPos(0, 0, 0), PLAYER, ChangeKind.PLACE, "STONE", T);
        store.record("world", new BlockPos(1, 0, 0), PLAYER, ChangeKind.PLACE, "STONE", T + 900);
        assertEquals(2, store.count().get());

        store.takeReady(SETTINGS, T + 1500).get();
        assertEquals(1, store.count().get());
    }

    @Test
    void recordingTheSamePositionAgainRefreshesIt() throws Exception {
        store.record("world", new BlockPos(0, 0, 0), PLAYER, ChangeKind.PLACE, "STONE", T);
        store.record("world", new BlockPos(0, 0, 0), PLAYER, ChangeKind.PLACE, "STONE", T + 900);
        store.takeReady(SETTINGS, T + 1500).get();
        assertEquals(1, store.count().get());
    }

    @Test
    void stateSurvivesReopening() throws Exception {
        store.record("world", new BlockPos(0, 0, 0), PLAYER, ChangeKind.PLACE, "STONE", T);
        store.record("world", new BlockPos(2, 0, 0), PLAYER, ChangeKind.PLACE, "STONE", T);
        store.count().get();
        database.close();

        database = TestDatabase.open(directory);
        store = new BlockChangeStore(database);
        assertEquals(1, store.takeReady(SETTINGS, T + 100).get().size());
    }
}
