package de.kylekreuter.vistructum.core.alert;

import de.kylekreuter.vistructum.api.BlockBox;
import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.FindingCandidate;
import de.kylekreuter.vistructum.api.FindingQuery;
import de.kylekreuter.vistructum.api.Preview;
import de.kylekreuter.vistructum.api.ReviewState;
import de.kylekreuter.vistructum.api.Source;
import de.kylekreuter.vistructum.api.SourcePrecision;
import de.kylekreuter.vistructum.api.Verdict;
import de.kylekreuter.vistructum.core.store.Database;
import de.kylekreuter.vistructum.core.store.TestDatabase;
import de.kylekreuter.vistructum.inference.ModelKind;
import de.kylekreuter.vistructum.inference.SurfaceScene;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.ResultSet;
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

    private static DetectedCandidate detected(String world, BlockBox box) {
        return detected(candidate(world, box));
    }

    private static DetectedCandidate detected(FindingCandidate candidate) {
        return new DetectedCandidate(candidate, new ModelInput(ModelKind.MASK,
                SurfaceScene.maskOnly(2, 1, new byte[]{1, 0}), 0, 0, 64, 64));
    }

    @Test
    void storedFindingRoundTrips() throws Exception {
        FindingCandidate candidate = candidate("world", new BlockBox(0, 60, 0, 10, 62, 10));
        Finding stored = store.insertUnlessDuplicate(detected(candidate), NOW, DEDUPE).get().orElseThrow();

        assertEquals(candidate.box(), stored.box());
        assertEquals(candidate.players(), stored.players());
        assertEquals(NOW, stored.createdAt());
        assertTrue(stored.open());
        assertEquals(stored, store.find(stored.id()).get().orElseThrow());
        assertArrayEquals(candidate.preview().pixels(), store.preview(stored.id()).get().orElseThrow().pixels());
    }

    @Test
    void overlappingFindingWithinDedupeWindowIsDropped() throws Exception {
        store.insertUnlessDuplicate(detected("world", new BlockBox(0, 60, 0, 10, 62, 10)), NOW, DEDUPE).get();

        assertTrue(store.insertUnlessDuplicate(detected("world", new BlockBox(5, 61, 5, 15, 61, 15)), NOW, DEDUPE).get()
                .isEmpty());
        assertTrue(store.insertUnlessDuplicate(detected("other", new BlockBox(5, 61, 5, 15, 61, 15)), NOW, DEDUPE).get()
                .isPresent());
        assertTrue(store.insertUnlessDuplicate(detected("world", new BlockBox(5, 61, 5, 15, 61, 15)),
                NOW.plus(DEDUPE).plusSeconds(1), DEDUPE).get().isPresent());
    }

    @Test
    void deleteReviewedBeforeKeepsOpenAndRecentlyReviewedFindings() throws Exception {
        Finding old = store.insertUnlessDuplicate(detected("a", new BlockBox(0, 60, 0, 10, 62, 10)), NOW, DEDUPE).get()
                .orElseThrow();
        Finding recent = store.insertUnlessDuplicate(detected("b", new BlockBox(0, 60, 0, 10, 62, 10)), NOW, DEDUPE).get()
                .orElseThrow();
        Finding open = store.insertUnlessDuplicate(detected("c", new BlockBox(0, 60, 0, 10, 62, 10)), NOW, DEDUPE).get()
                .orElseThrow();
        store.review(old.id(), Verdict.CONFIRMED, "Staff", NOW).get();
        store.review(recent.id(), Verdict.FALSE_ALARM, "Staff", NOW.plus(Duration.ofDays(30))).get();

        assertEquals(1, store.deleteReviewedBefore(NOW.plus(Duration.ofDays(10))).get());
        assertTrue(store.find(old.id()).get().isEmpty());
        assertTrue(store.find(recent.id()).get().isPresent());
        assertTrue(store.find(open.id()).get().isPresent());
    }

    @Test
    void reviewClosesAFinding() throws Exception {
        Finding stored = store.insertUnlessDuplicate(detected("world", new BlockBox(0, 60, 0, 10, 62, 10)), NOW, DEDUPE)
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
        store.insertUnlessDuplicate(detected(first), NOW, DEDUPE).get();
        assertTrue(store.isDuplicate(candidate("world", new BlockBox(5, 61, 5, 15, 61, 15)), NOW, DEDUPE).get());
    }

    @Test
    void queryPagesNewestFirstWithoutGaps() throws Exception {
        for (int i = 0; i < 5; i++) {
            store.insertUnlessDuplicate(detected("world", new BlockBox(i * 100, 60, 0, i * 100 + 10, 62, 10)), NOW,
                    DEDUPE).get();
        }
        store.insertUnlessDuplicate(detected("other", new BlockBox(0, 60, 0, 10, 62, 10)), NOW, DEDUPE).get();

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

    @Test
    void reviewedScenesReturnTheStoredModelInputInIdOrder() throws Exception {
        SurfaceScene scene = new SurfaceScene(3, 2, new short[]{1, 2, SurfaceScene.UNKNOWN, 4, 5, 6},
                new short[]{-64, 70, 0, 319, 64, 65}, new byte[]{0, 12, (byte) 128, (byte) 255, 7, 9},
                new byte[]{0, 1, 0, 1, 1, 0});
        FindingCandidate surface = new FindingCandidate(Source.FULLSCAN, "world", new BlockBox(0, 60, 0, 2, 64, 1),
                0.8, 3, Set.of(), "Kachel 0,0", "bf-scan-2", new Preview(1, 1, new byte[]{12}));
        Finding first = store.insertUnlessDuplicate(new DetectedCandidate(surface,
                new ModelInput(ModelKind.FULLSCAN, scene, -2, 1, 62, 65)), NOW, DEDUPE).get().orElseThrow();
        Finding open = store.insertUnlessDuplicate(detected("world", new BlockBox(100, 60, 0, 110, 62, 10)), NOW, DEDUPE)
                .get().orElseThrow();
        Finding second = store.insertUnlessDuplicate(detected("world", new BlockBox(200, 60, 0, 210, 62, 10)), NOW,
                DEDUPE).get().orElseThrow();
        store.review(second.id(), Verdict.FALSE_ALARM, "Staff", NOW).get();
        store.review(first.id(), Verdict.CONFIRMED, "Staff", NOW).get();

        List<ReviewedScene> all = store.reviewedScenes(0, 10).get();
        assertEquals(List.of(first.id(), second.id()), all.stream().map(ReviewedScene::findingId).toList());
        ReviewedScene stored = all.getFirst();
        assertEquals(Source.FULLSCAN, stored.source());
        assertEquals(Verdict.CONFIRMED, stored.verdict());
        assertEquals("bf-scan-2", stored.modelVersion());
        assertEquals(ModelKind.FULLSCAN, stored.input().kind());
        assertEquals(List.of(-2, 1, 62, 65), List.of(stored.input().top(), stored.input().left(),
                stored.input().bottom(), stored.input().right()));
        assertSceneEquals(scene, stored.input().scene());
        assertEquals(Verdict.FALSE_ALARM, all.get(1).verdict());
        assertEquals(List.of(second.id()), store.reviewedScenes(first.id(), 10).get().stream()
                .map(ReviewedScene::findingId).toList());
        assertEquals(1, store.reviewedScenes(0, 1).get().size());
        assertTrue(store.reviewedScenes(second.id(), 10).get().isEmpty());
        assertTrue(open.open());
    }

    @Test
    void precisionCountsVerdictsPerSource() throws Exception {
        for (int i = 0; i < 4; i++) {
            Finding finding = store.insertUnlessDuplicate(detected("world", new BlockBox(i * 100, 60, 0, i * 100 + 10,
                    62, 10)), NOW, DEDUPE).get().orElseThrow();
            if (i < 3) {
                store.review(finding.id(), i == 0 ? Verdict.FALSE_ALARM : Verdict.CONFIRMED, "Staff", NOW).get();
            }
        }

        List<SourcePrecision> precision = store.precision().get();
        assertEquals(List.of(new SourcePrecision(Source.MASK, 2, 1), new SourcePrecision(Source.FULLSCAN, 0, 0)),
                precision);
        assertEquals(2.0 / 3, precision.getFirst().precision().orElseThrow(), 1e-9);
        assertTrue(precision.get(1).precision().isEmpty());
    }

    @Test
    void reviewedFindingsWithoutSceneAreCountedAndSkipped() throws Exception {
        Finding withScene = store.insertUnlessDuplicate(detected("world", new BlockBox(0, 60, 0, 10, 62, 10)), NOW,
                DEDUPE).get().orElseThrow();
        Finding withoutScene = store.insertUnlessDuplicate(detected("world", new BlockBox(100, 60, 0, 110, 62, 10)),
                NOW, DEDUPE).get().orElseThrow();
        database.transaction(connection -> connection.createStatement()
                .executeUpdate("DELETE FROM finding_scenes WHERE finding_id = " + withoutScene.id())).get();
        store.review(withScene.id(), Verdict.CONFIRMED, "Staff", NOW).get();
        store.review(withoutScene.id(), Verdict.CONFIRMED, "Staff", NOW).get();

        assertEquals(1L, store.countReviewedWithoutScene().get());
        assertEquals(List.of(withScene.id()), store.reviewedScenes(0, 10).get().stream()
                .map(ReviewedScene::findingId).toList());
    }

    @Test
    void retentionDeletesTheStoredScene() throws Exception {
        Finding finding = store.insertUnlessDuplicate(detected("world", new BlockBox(0, 60, 0, 10, 62, 10)), NOW, DEDUPE)
                .get().orElseThrow();
        store.review(finding.id(), Verdict.CONFIRMED, "Staff", NOW).get();

        assertEquals(1, store.deleteReviewedBefore(NOW.plusSeconds(1)).get());
        long scenes = database.transaction(connection -> {
            try (ResultSet rows = connection.createStatement().executeQuery("SELECT count(*) FROM finding_scenes")) {
                return rows.getLong(1);
            }
        }).get();
        assertEquals(0L, scenes);
    }

    static void assertSceneEquals(SurfaceScene expected, SurfaceScene actual) {
        assertEquals(expected.width(), actual.width());
        assertEquals(expected.height(), actual.height());
        assertArrayEquals(expected.blocks(), actual.blocks());
        assertArrayEquals(expected.heights(), actual.heights());
        assertArrayEquals(expected.luminance(), actual.luminance());
        assertArrayEquals(expected.modified(), actual.modified());
    }
}
