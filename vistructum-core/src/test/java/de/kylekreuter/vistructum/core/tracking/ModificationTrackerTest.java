package de.kylekreuter.vistructum.core.tracking;

import de.kylekreuter.vistructum.core.scene.BlockPos;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModificationTrackerTest {

    private static final UUID WORLD = UUID.randomUUID();
    private static final UUID PLAYER = UUID.randomUUID();

    private static TrackerSettings settings(Duration ttl, int linkDistance, Duration quietPeriod,
                                             int minBlocks, int maxExtent) {
        return new TrackerSettings(ttl, linkDistance, quietPeriod, minBlocks, maxExtent);
    }

    @Test
    void linkDistanceJoinsAtBoundaryAndSplitsBeyondIt() {
        ModificationTracker joined = new ModificationTracker(
                settings(Duration.ofHours(1), 3, Duration.ZERO, 1, 1000));
        joined.record(WORLD, 0, 0, 0, PLAYER, 0);
        joined.record(WORLD, 3, 0, 0, PLAYER, 0);
        List<Cluster> readyJoined = joined.pollReady(0);
        assertEquals(1, readyJoined.size());
        assertEquals(2, readyJoined.get(0).positions().size());

        ModificationTracker split = new ModificationTracker(
                settings(Duration.ofHours(1), 3, Duration.ZERO, 1, 1000));
        split.record(WORLD, 0, 0, 0, PLAYER, 0);
        split.record(WORLD, 4, 0, 0, PLAYER, 0);
        List<Cluster> readySplit = split.pollReady(0);
        assertEquals(2, readySplit.size());
        assertEquals(1, readySplit.get(0).positions().size());
        assertEquals(1, readySplit.get(1).positions().size());
    }

    @Test
    void linkDistanceUsesChebyshevNotEuclidean() {
        ModificationTracker joined = new ModificationTracker(
                settings(Duration.ofHours(1), 3, Duration.ZERO, 1, 1000));
        joined.record(WORLD, 0, 0, 0, PLAYER, 0);
        joined.record(WORLD, 3, 3, 3, PLAYER, 0); // chebyshev distance 3, would be > 3 euclidean-normalized
        assertEquals(1, joined.pollReady(0).size());

        ModificationTracker split = new ModificationTracker(
                settings(Duration.ofHours(1), 3, Duration.ZERO, 1, 1000));
        split.record(WORLD, 0, 0, 0, PLAYER, 0);
        split.record(WORLD, 4, 3, 3, PLAYER, 0); // chebyshev distance 4
        assertEquals(2, split.pollReady(0).size());
    }

    @Test
    void minBlocksFiltersSmallClusters() {
        ModificationTracker tracker = new ModificationTracker(
                settings(Duration.ofHours(1), 3, Duration.ZERO, 4, 1000));
        for (int i = 0; i < 3; i++) {
            tracker.record(WORLD, i, 0, 0, PLAYER, 0);
        }
        assertTrue(tracker.pollReady(0).isEmpty());

        tracker.record(WORLD, 3, 0, 0, PLAYER, 0);
        assertEquals(1, tracker.pollReady(0).size());
    }

    @Test
    void clusterOnlyReadyAfterQuietPeriodElapses() {
        ModificationTracker tracker = new ModificationTracker(
                settings(Duration.ofHours(1), 3, Duration.ofMillis(1000), 1, 1000));
        tracker.record(WORLD, 0, 0, 0, PLAYER, 0);

        assertTrue(tracker.pollReady(500).isEmpty());
        assertTrue(tracker.pollReady(999).isEmpty());
        assertEquals(1, tracker.pollReady(1000).size());
    }

    @Test
    void expiredChangesAreEvictedByTtl() {
        ModificationTracker tracker = new ModificationTracker(
                settings(Duration.ofMillis(1000), 3, Duration.ZERO, 1, 1000));
        tracker.record(WORLD, 0, 0, 0, PLAYER, 0);
        assertEquals(1, tracker.trackedPositionCount());

        tracker.pollReady(2000); // 2000 - 0 > ttl(1000)
        assertEquals(0, tracker.trackedPositionCount());
        assertTrue(tracker.pollReady(2000).isEmpty());
    }

    @Test
    void reRecordingBeforeExpiryRefreshesTimeAndSurvives() {
        ModificationTracker tracker = new ModificationTracker(
                settings(Duration.ofMillis(1000), 3, Duration.ZERO, 1, 1000));
        tracker.record(WORLD, 0, 0, 0, PLAYER, 0);
        tracker.record(WORLD, 0, 0, 0, PLAYER, 900); // refresh before the original would expire

        assertEquals(1, tracker.trackedPositionCount());
        tracker.pollReady(1500); // 1500 - 900 = 600 <= ttl, still alive
        assertEquals(1, tracker.trackedPositionCount());
    }

    @Test
    void clusterIsReReportedOnlyAfterANewChange() {
        ModificationTracker tracker = new ModificationTracker(
                settings(Duration.ofHours(1), 3, Duration.ofMillis(100), 2, 1000));
        tracker.record(WORLD, 0, 0, 0, PLAYER, 0);
        tracker.record(WORLD, 2, 0, 0, PLAYER, 0);

        assertEquals(1, tracker.pollReady(100).size());
        assertTrue(tracker.pollReady(200).isEmpty(), "unchanged cluster must not be reported twice");

        tracker.record(WORLD, 4, 0, 0, PLAYER, 250); // extends the cluster with a new change
        assertTrue(tracker.pollReady(300).isEmpty(), "still within the fresh quiet period");
        List<Cluster> reReported = tracker.pollReady(350);
        assertEquals(1, reReported.size());
        assertEquals(3, reReported.get(0).positions().size());
    }

    @Test
    void differentWorldsNeverMergeEvenAtSameCoordinates() {
        UUID worldA = UUID.randomUUID();
        UUID worldB = UUID.randomUUID();
        ModificationTracker tracker = new ModificationTracker(
                settings(Duration.ofHours(1), 3, Duration.ZERO, 1, 1000));
        tracker.record(worldA, 0, 0, 0, PLAYER, 0);
        tracker.record(worldB, 0, 0, 0, PLAYER, 0);
        tracker.record(worldA, 1, 0, 0, PLAYER, 0);
        tracker.record(worldB, 1, 0, 0, PLAYER, 0);

        List<Cluster> ready = tracker.pollReady(0);
        assertEquals(2, ready.size());
        assertTrue(ready.stream().allMatch(c -> c.positions().size() == 2));
        assertTrue(ready.stream().map(Cluster::world).distinct().count() == 2);
    }

    @Test
    void oversizedFlagsWithoutSuppressingTheReport() {
        ModificationTracker tracker = new ModificationTracker(
                settings(Duration.ofHours(1), 3, Duration.ZERO, 1, 5));
        for (int x = 0; x <= 9; x += 3) {
            tracker.record(WORLD, x, 0, 0, PLAYER, 0);
        }
        List<Cluster> ready = tracker.pollReady(0);
        assertEquals(1, ready.size());
        Cluster cluster = ready.get(0);
        assertTrue(cluster.oversized());
        assertEquals(new BlockPos(0, 0, 0), cluster.min());
        assertEquals(new BlockPos(9, 0, 0), cluster.max());
    }

    @Test
    void notOversizedWhenWithinMaxExtent() {
        ModificationTracker tracker = new ModificationTracker(
                settings(Duration.ofHours(1), 3, Duration.ZERO, 1, 50));
        tracker.record(WORLD, 0, 0, 0, PLAYER, 0);
        tracker.record(WORLD, 3, 0, 0, PLAYER, 0);
        assertFalse(tracker.pollReady(0).get(0).oversized());
    }

    @Test
    void handlesFiftyThousandPositionsInLargeBuildsWithFastPoll() {
        ModificationTracker tracker = new ModificationTracker(
                settings(Duration.ofHours(1), 3, Duration.ZERO, 1, 1000));
        int buildsCount = 5;
        int side = 100; // 100x100 = 10_000 blocks per build, densely adjacent
        long now = 0;
        for (int b = 0; b < buildsCount; b++) {
            int originX = b * 100_000; // far enough apart that builds never link
            for (int i = 0; i < side; i++) {
                for (int j = 0; j < side; j++) {
                    tracker.record(WORLD, originX + i, 64, j, PLAYER, now);
                }
            }
        }
        assertEquals(buildsCount * side * side, tracker.trackedPositionCount());

        long start = System.nanoTime();
        List<Cluster> ready = tracker.pollReady(now);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertEquals(buildsCount, ready.size());
        for (Cluster cluster : ready) {
            assertEquals(side * side, cluster.positions().size());
        }
        assertTrue(elapsedMillis < 5000, "pollReady over 50k positions took " + elapsedMillis + "ms");
        System.out.println("pollReady over " + (buildsCount * side * side) + " positions took " + elapsedMillis + "ms");
    }
}
