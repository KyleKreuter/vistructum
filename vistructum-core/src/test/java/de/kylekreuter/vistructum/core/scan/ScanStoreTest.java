package de.kylekreuter.vistructum.core.scan;

import de.kylekreuter.vistructum.api.ScanCause;
import de.kylekreuter.vistructum.api.ScanJob;
import de.kylekreuter.vistructum.api.ScanStatus;
import de.kylekreuter.vistructum.core.store.Database;
import de.kylekreuter.vistructum.core.store.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanStoreTest {

    private static final Instant NOW = Instant.parse("2026-09-24T04:00:00Z");

    @TempDir
    Path directory;
    private Database database;
    private ScanStore store;

    @BeforeEach
    void open() {
        database = TestDatabase.open(directory);
        store = new ScanStore(database);
    }

    @AfterEach
    void close() {
        database.close();
    }

    @Test
    void oneActiveJobPerWorld() throws Exception {
        assertTrue(store.enqueue("world", ScanCause.MANUAL, NOW).get().isPresent());
        assertTrue(store.enqueue("world", ScanCause.MANUAL, NOW).get().isEmpty());
        assertTrue(store.enqueue("nether", ScanCause.MANUAL, NOW).get().isPresent());
        assertEquals(2, store.active().get().size());
    }

    @Test
    void tilesAreWorkedOffAndProgressSurvivesReopening() throws Exception {
        ScanJob job = store.enqueue("world", ScanCause.MANUAL, NOW).get().orElseThrow();
        ScanJob planned = store.plan(job.id(), List.of(new ScanPlan.Tile(0, 0), new ScanPlan.Tile(192, 0))).get();
        assertEquals(ScanStatus.RUNNING, planned.status());
        assertEquals(2, planned.tilesTotal());

        ScanPlan.Tile first = store.nextTile(job.id()).get().orElseThrow();
        store.completeTile(job.id(), first, 2, false).get();
        store.completeTile(job.id(), first, 2, false).get();
        database.close();

        database = TestDatabase.open(directory);
        store = new ScanStore(database);
        ScanJob resumed = store.next().get().orElseThrow();
        assertEquals(1, resumed.tilesDone());
        assertEquals(2, resumed.findings());
        ScanPlan.Tile second = store.nextTile(job.id()).get().orElseThrow();
        assertEquals(new ScanPlan.Tile(192, 0), second);
        store.completeTile(job.id(), second, 0, true).get();
        assertEquals(Optional.empty(), store.nextTile(job.id()).get());

        ScanJob done = store.finish(job.id(), ScanStatus.DONE, NOW.plusSeconds(600)).get();
        assertEquals(ScanStatus.DONE, done.status());
        assertEquals(1, done.failures());
        assertTrue(store.next().get().isEmpty());
    }

    @Test
    void cancelEndsEveryActiveJob() throws Exception {
        store.enqueue("world", ScanCause.MANUAL, NOW).get();
        store.enqueue("nether", ScanCause.DAILY, NOW).get();
        assertEquals(2, store.cancelAll(NOW).get());
        assertTrue(store.active().get().isEmpty());
    }

    @Test
    void dailyScanRunsOncePerDay() throws Exception {
        LocalDate day = LocalDate.of(2026, 9, 24);
        assertEquals(2, store.enqueueDaily(day, List.of("world", "nether"), NOW).get().orElseThrow().size());
        assertTrue(store.enqueueDaily(day, List.of("world", "nether"), NOW).get().isEmpty());
        store.cancelAll(NOW).get();
        assertTrue(store.enqueueDaily(day, List.of("world"), NOW).get().isEmpty());
        assertEquals(1, store.enqueueDaily(day.plusDays(1), List.of("world"), NOW).get().orElseThrow().size());
    }
}
