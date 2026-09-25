package de.kylekreuter.vistructum.core.alert;

import de.kylekreuter.vistructum.api.BlockBox;
import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.FindingCandidate;
import de.kylekreuter.vistructum.api.FindingQuery;
import de.kylekreuter.vistructum.api.Preview;
import de.kylekreuter.vistructum.api.ReviewState;
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
import java.util.List;
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

    private static FindingCandidate candidate(String world, BlockBox box) {
        return new FindingCandidate(Source.MASK, world, box, 0.97, 2, Set.of(UUID.randomUUID()), "Achse Y", "bf-mask-2",
                new Preview(2, 1, new byte[]{40, (byte) 235}));
    }

    @Test
    void storedFindingRoundTrips() throws Exception {
        FindingCandidate candidate = candidate("world", new BlockBox(0, 60, 0, 10, 62, 10));
        Finding stored = store.insertUnlessDuplicate(candidate, NOW, DEDUPE).get().orElseThrow();

        assertEquals(candidate.box(), stored.box());
        assertEquals(candidate.players(), stored.players());
        assertEquals(NOW, stored.createdAt());
        assertTrue(stored.open());
        assertEquals(stored, store.find(stored.id()).get().orElseThrow());
        assertArrayEquals(candidate.preview().pixels(), store.preview(stored.id()).get().orElseThrow().pixels());
    }

    @Test
    void overlappingFindingWithinDedupeWindowIsDropped() throws Exception {
        store.insertUnlessDuplicate(candidate("world", new BlockBox(0, 60, 0, 10, 62, 10)), NOW, DEDUPE).get();

        assertTrue(store.insertUnlessDuplicate(candidate("world", new BlockBox(5, 61, 5, 15, 61, 15)), NOW, DEDUPE).get()
                .isEmpty());
        assertTrue(store.insertUnlessDuplicate(candidate("other", new BlockBox(5, 61, 5, 15, 61, 15)), NOW, DEDUPE).get()
                .isPresent());
        assertTrue(store.insertUnlessDuplicate(candidate("world", new BlockBox(5, 61, 5, 15, 61, 15)),
                NOW.plus(DEDUPE).plusSeconds(1), DEDUPE).get().isPresent());
    }

    @Test
    void reviewClosesAFinding() throws Exception {
        Finding stored = store.insertUnlessDuplicate(candidate("world", new BlockBox(0, 60, 0, 10, 62, 10)), NOW, DEDUPE)
                .get().orElseThrow();
        assertEquals(1L, store.count(FindingQuery.open()).get());

        Finding reviewed = store.review(stored.id(), Verdict.FALSE_ALARM, "Staff", NOW.plusSeconds(60)).get()
                .orElseThrow();
        assertFalse(reviewed.open());
        assertEquals(Verdict.FALSE_ALARM, reviewed.review().orElseThrow().verdict());
        assertEquals("Staff", reviewed.review().orElseThrow().reviewer());
        assertEquals(0L, store.count(FindingQuery.open()).get());
        assertEquals(1L, store.count(FindingQuery.all().state(ReviewState.REVIEWED).before(stored.id() + 1).limit(1)).get());
        assertTrue(store.query(FindingQuery.open()).get().findings().isEmpty());
        assertEquals(1, store.query(FindingQuery.all().state(ReviewState.REVIEWED).since(NOW)).get().findings().size());
    }

    @Test
    void duplicateCheckMatchesInsertRule() throws Exception {
        FindingCandidate first = candidate("world", new BlockBox(0, 60, 0, 10, 62, 10));
        assertFalse(store.isDuplicate(first, NOW, DEDUPE).get());
        store.insertUnlessDuplicate(first, NOW, DEDUPE).get();
        assertTrue(store.isDuplicate(candidate("world", new BlockBox(5, 61, 5, 15, 61, 15)), NOW, DEDUPE).get());
    }

    @Test
    void queryPagesNewestFirstWithoutGaps() throws Exception {
        for (int i = 0; i < 5; i++) {
            store.insertUnlessDuplicate(candidate("world", new BlockBox(i * 100, 60, 0, i * 100 + 10, 62, 10)), NOW,
                    DEDUPE).get();
        }
        store.insertUnlessDuplicate(candidate("other", new BlockBox(0, 60, 0, 10, 62, 10)), NOW, DEDUPE).get();

        FindingQuery query = FindingQuery.all().world("world").limit(2);
        FindingSlice first = store.query(query).get();
        FindingSlice second = store.query(query.before(first.findings().getLast().id())).get();
        FindingSlice third = store.query(query.before(second.findings().getLast().id())).get();

        assertEquals(List.of(5L, 4L), ids(first));
        assertTrue(first.more());
        assertEquals(List.of(3L, 2L), ids(second));
        assertEquals(List.of(1L), ids(third));
        assertFalse(third.more());
        assertEquals(List.of(6L), ids(store.query(FindingQuery.all().world("other")).get()));
        assertEquals(6, store.query(FindingQuery.all().source(Source.MASK)).get().findings().size());
        assertTrue(store.query(FindingQuery.all().source(Source.FULLSCAN)).get().findings().isEmpty());
    }

    private static List<Long> ids(FindingSlice slice) {
        return slice.findings().stream().map(Finding::id).toList();
    }

    @Test
    void unknownIdYieldsNothing() throws Exception {
        assertEquals(Optional.empty(), store.find(42).get());
        assertEquals(Optional.empty(), store.review(42, Verdict.CONFIRMED, "Staff", NOW).get());
        assertEquals(Optional.empty(), store.preview(42).get());
    }
}
