package de.kylekreuter.vistructum.core.tracking;

import de.kylekreuter.vistructum.core.scene.BlockPos;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClusteringTest {

    private static final String WORLD = "world";
    private static final UUID PLAYER = UUID.randomUUID();

    private static ClusterSettings settings(Duration ttl, Duration quiet, int minBlocks, int maxExtent) {
        return new ClusterSettings(ttl, 3, quiet, minBlocks, maxExtent);
    }

    private static ClusterSettings loose() {
        return settings(Duration.ofHours(1), Duration.ZERO, 1, 1000);
    }

    private static List<Cluster> ready(List<BlockChange> changes, ClusterSettings settings, long nowMillis) {
        List<WorldPosition> seeds = changes.stream().map(change -> new WorldPosition(change.world(), change.pos()))
                .distinct().toList();
        try {
            return Clustering.ready(seeds, List.of(), new InMemoryCells(changes), settings, nowMillis);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static BlockChange change(String world, int x, int y, int z, long changedAt) {
        return new BlockChange(world, new BlockPos(x, y, z), PLAYER, ChangeKind.PLACE, "STONE", changedAt, 0);
    }

    @Test
    void linkDistanceJoinsAtBoundaryAndSplitsBeyondIt() {
        assertEquals(1, ready(List.of(change(WORLD, 0, 0, 0, 1), change(WORLD, 3, 0, 0, 1)), loose(), 1).size());
        assertEquals(2, ready(List.of(change(WORLD, 0, 0, 0, 1), change(WORLD, 4, 0, 0, 1)), loose(), 1).size());
    }

    @Test
    void linkDistanceUsesChebyshevDistance() {
        assertEquals(1, ready(List.of(change(WORLD, 0, 0, 0, 1), change(WORLD, 3, 3, 3, 1)), loose(), 1).size());
        assertEquals(2, ready(List.of(change(WORLD, 0, 0, 0, 1), change(WORLD, 4, 3, 3, 1)), loose(), 1).size());
    }

    @Test
    void minBlocksFiltersSmallClusters() {
        ClusterSettings settings = settings(Duration.ofHours(1), Duration.ZERO, 4, 1000);
        List<BlockChange> changes = new ArrayList<>();
        for (int x = 0; x < 3; x++) {
            changes.add(change(WORLD, x, 0, 0, 1));
        }
        assertTrue(ready(changes, settings, 1).isEmpty());
        changes.add(change(WORLD, 3, 0, 0, 1));
        assertEquals(1, ready(changes, settings, 1).size());
    }

    @Test
    void clusterIsReadyOnlyAfterTheQuietPeriod() {
        ClusterSettings settings = settings(Duration.ofHours(1), Duration.ofMillis(1000), 1, 1000);
        List<BlockChange> changes = List.of(change(WORLD, 0, 0, 0, 1));
        assertTrue(ready(changes, settings, 1000).isEmpty());
        assertEquals(1, ready(changes, settings, 1001).size());
    }

    @Test
    void reportedClusterWaitsForANewChange() {
        BlockChange reported = new BlockChange(WORLD, new BlockPos(0, 0, 0), PLAYER, ChangeKind.PLACE, "STONE", 10, 20);
        BlockChange reportedNeighbor = new BlockChange(WORLD, new BlockPos(2, 0, 0), PLAYER, ChangeKind.PLACE, "STONE", 10, 20);
        assertTrue(ready(List.of(reported, reportedNeighbor), loose(), 30).isEmpty());

        List<Cluster> grown = ready(List.of(reported, reportedNeighbor, change(WORLD, 4, 0, 0, 25)), loose(), 30);
        assertEquals(1, grown.size());
        assertEquals(3, grown.getFirst().positions().size());
    }

    @Test
    void differentWorldsNeverMerge() {
        List<Cluster> ready = ready(List.of(change("a", 0, 0, 0, 1), change("b", 0, 0, 0, 1),
                change("a", 1, 0, 0, 1), change("b", 1, 0, 0, 1)), loose(), 1);
        assertEquals(2, ready.size());
        assertTrue(ready.stream().allMatch(cluster -> cluster.positions().size() == 2));
        assertEquals(2, ready.stream().map(Cluster::world).distinct().count());
    }

    @Test
    void playersOfAllChangesAreCollected() {
        UUID other = UUID.randomUUID();
        List<Cluster> ready = ready(List.of(change(WORLD, 0, 0, 0, 1),
                new BlockChange(WORLD, new BlockPos(0, 0, 0), other, ChangeKind.BREAK, "STONE", 2, 0)), loose(), 2);
        assertEquals(1, ready.getFirst().positions().size());
        assertEquals(2, ready.getFirst().players().size());
    }

    @Test
    void placersAndBreakersAreKeptApart() {
        UUID breaker = UUID.randomUUID();
        BlockPos pos = new BlockPos(0, 0, 0);
        Cluster cluster = ready(List.of(change(WORLD, 0, 0, 0, 1),
                new BlockChange(WORLD, pos, breaker, ChangeKind.BREAK, "STONE", 2, 0)), loose(), 2).getFirst();

        assertEquals(ChangeKind.BREAK, cluster.blocks().get(pos).kind());
        assertEquals(Set.of(PLAYER), cluster.blocks().get(pos).placers());
        assertEquals(Set.of(breaker), cluster.blocks().get(pos).breakers());
        assertEquals(Set.of(breaker), cluster.responsibleFor(Set.of(), Set.of(pos)));
        assertEquals(Set.of(), cluster.responsibleFor(Set.of(), Set.of()));
    }

    @Test
    void oversizedIsFlaggedWithBounds() {
        List<BlockChange> changes = new ArrayList<>();
        for (int x = 0; x <= 9; x += 3) {
            changes.add(change(WORLD, x, 0, 0, 1));
        }
        Cluster cluster = ready(changes, settings(Duration.ofHours(1), Duration.ZERO, 1, 5), 1).getFirst();
        assertTrue(cluster.oversized());
        assertEquals(new BlockPos(0, 0, 0), cluster.min());
        assertEquals(new BlockPos(9, 0, 0), cluster.max());
        assertFalse(ready(changes, loose(), 1).getFirst().oversized());
    }

    @Test
    void clustersAcrossCellBordersAreJoined() {
        List<BlockChange> changes = new ArrayList<>();
        for (int i = 18; i >= -3; i -= 3) {
            changes.add(change(WORLD, i, 0, i, 1));
        }
        List<Cluster> clusters = ready(changes, loose(), 1);
        assertEquals(1, clusters.size());
        assertEquals(8, clusters.getFirst().positions().size());
    }

    @Test
    void aFreshChangeAnywhereKeepsTheWholeClusterWaiting() {
        ClusterSettings settings = settings(Duration.ofHours(1), Duration.ofMillis(100), 1, 1000);
        List<BlockChange> changes = new ArrayList<>();
        for (int x = 0; x < 30; x += 3) {
            changes.add(change(WORLD, x, 0, 0, 1));
        }
        changes.add(change(WORLD, 30, 0, 0, 150));
        assertTrue(ready(changes, settings, 200).isEmpty());
        assertEquals(11, ready(changes, settings, 250).getFirst().positions().size());
    }

    @Test
    void fiftyThousandPositionsClusterQuickly() {
        List<BlockChange> changes = new ArrayList<>();
        for (int build = 0; build < 5; build++) {
            for (int i = 0; i < 100; i++) {
                for (int j = 0; j < 100; j++) {
                    changes.add(change(WORLD, build * 100_000 + i, 64, j, 1));
                }
            }
        }
        long start = System.nanoTime();
        List<Cluster> ready = ready(changes, loose(), 1);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        assertEquals(5, ready.size());
        assertTrue(ready.stream().allMatch(cluster -> cluster.positions().size() == 10_000));
        assertTrue(elapsedMillis < 5000, "clustering 50k positions took " + elapsedMillis + " ms");
    }
}
