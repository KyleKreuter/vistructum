package de.kylekreuter.vistructum.core.alert;

import de.kylekreuter.vistructum.api.BlockBox;
import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.Source;
import de.kylekreuter.vistructum.api.Verdict;
import de.kylekreuter.vistructum.core.store.Database;
import de.kylekreuter.vistructum.core.store.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FindingStoreTest {

    private static final Instant NOW = Instant.parse("2026-09-24T12:00:00Z");
    private static final Duration DEDUPE = Duration.ofDays(14);

    @TempDir
    Path directory;
    private Database database;
    private FindingStore store;

    @BeforeEach
    void open() {
        database = TestDatabase.open(directory);
        store = new FindingStore(database);
    }

    @AfterEach
    void close() {
        database.close();
    }

    private static FindingDraft draft(String world, BlockBox box) {
        return new FindingDraft(Source.MASK, world, box, 0.97, 2, Set.of(UUID.randomUUID()), "Achse Y", "bf-mask-1",
                new Preview(2, 1, new byte[]{40, (byte) 235}));
    }

    @Test
    void storedFindingRoundTrips() throws Exception {
        FindingDraft draft = draft("world", new BlockBox(0, 60, 0, 10, 62, 10));
        Finding stored = store.insertUnlessDuplicate(draft, NOW, DEDUPE).get().orElseThrow();

        assertEquals(draft.box(), stored.box());
        assertEquals(draft.players(), stored.players());
        assertEquals(NOW, stored.createdAt());
        assertTrue(stored.open());
        assertEquals(stored, store.find(stored.id()).get().orElseThrow());
        assertArrayEquals(draft.preview().pixels(), store.preview(stored.id()).get().orElseThrow().pixels());
    }

    @Test
    void overlappingFindingWithinDedupeWindowIsDropped() throws Exception {
        store.insertUnlessDuplicate(draft("world", new BlockBox(0, 60, 0, 10, 62, 10)), NOW, DEDUPE).get();

        assertTrue(store.insertUnlessDuplicate(draft("world", new BlockBox(5, 61, 5, 15, 61, 15)), NOW, DEDUPE).get()
                .isEmpty());
        assertTrue(store.insertUnlessDuplicate(draft("other", new BlockBox(5, 61, 5, 15, 61, 15)), NOW, DEDUPE).get()
                .isPresent());
        assertTrue(store.insertUnlessDuplicate(draft("world", new BlockBox(5, 61, 5, 15, 61, 15)),
                NOW.plus(DEDUPE).plusSeconds(1), DEDUPE).get().isPresent());
    }

    @Test
    void reviewClosesAFinding() throws Exception {
        Finding stored = store.insertUnlessDuplicate(draft("world", new BlockBox(0, 60, 0, 10, 62, 10)), NOW, DEDUPE)
                .get().orElseThrow();
        assertEquals(1, store.countOpen().get());

        Finding reviewed = store.review(stored.id(), Verdict.FALSE_ALARM, "Staff", NOW.plusSeconds(60)).get()
                .orElseThrow();
        assertFalse(reviewed.open());
        assertEquals(Verdict.FALSE_ALARM, reviewed.review().orElseThrow().verdict());
        assertEquals("Staff", reviewed.review().orElseThrow().reviewer());
        assertEquals(0, store.countOpen().get());
        assertTrue(store.open(10).get().isEmpty());
        assertEquals(1, store.since(NOW, 10).get().size());
    }

    @Test
    void unknownIdYieldsNothing() throws Exception {
        assertEquals(Optional.empty(), store.find(42).get());
        assertEquals(Optional.empty(), store.review(42, Verdict.CONFIRMED, "Staff", NOW).get());
        assertEquals(Optional.empty(), store.preview(42).get());
    }
}
