package de.kylekreuter.vistructum.core.face;

import de.kylekreuter.vistructum.api.BlockBox;
import de.kylekreuter.vistructum.api.FindingCandidate;
import de.kylekreuter.vistructum.api.PlayerFace;
import de.kylekreuter.vistructum.api.Preview;
import de.kylekreuter.vistructum.api.Source;
import de.kylekreuter.vistructum.core.alert.FindingStore;
import de.kylekreuter.vistructum.core.store.Database;
import de.kylekreuter.vistructum.core.store.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaceStoreTest {

    private static final Instant NOW = Instant.parse("2026-09-25T18:00:00Z");
    private static final UUID BUILDER = UUID.fromString("e3e04125-846a-3ff7-9f1f-aa61ff8eb6b7");
    private static final UUID HELPER = UUID.fromString("0f5b2c1e-3a4d-4e6f-8a9b-1c2d3e4f5a6b");
    private static final UUID BYSTANDER = UUID.fromString("7d8e9f0a-1b2c-4d3e-9f4a-5b6c7d8e9f0a");

    @TempDir
    Path directory;
    private Database database;
    private FaceStore store;

    @BeforeEach
    void open() {
        database = TestDatabase.open(directory);
        store = new FaceStore(database);
    }

    @AfterEach
    void close() {
        database.close();
    }

    @Test
    void deleteUnreferencedKeepsFacesOfPlayersInFindings() throws Exception {
        new FindingStore(database).insertUnlessDuplicate(new FindingCandidate(Source.MASK, "world",
                new BlockBox(0, 60, 0, 10, 62, 10), 0.97, 2, Set.of(BUILDER, HELPER), "Achse Y", "bf-mask-2",
                new Preview(1, 1, new byte[]{40})), NOW, Duration.ofDays(14)).get();
        for (UUID player : List.of(BUILDER, HELPER, BYSTANDER)) {
            store.save(new PlayerFace(player, Optional.empty(), List.of()), NOW).get();
        }

        assertEquals(1, store.deleteUnreferenced().get());
        assertTrue(store.find(BUILDER).get().isPresent());
        assertTrue(store.find(HELPER).get().isPresent());
        assertTrue(store.find(BYSTANDER).get().isEmpty());
    }
}
