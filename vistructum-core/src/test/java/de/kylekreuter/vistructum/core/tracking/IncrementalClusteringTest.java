package de.kylekreuter.vistructum.core.tracking;

import de.kylekreuter.vistructum.core.scene.BlockPos;
import de.kylekreuter.vistructum.core.store.Database;
import de.kylekreuter.vistructum.core.store.TestDatabase;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IncrementalClusteringTest {

    private static final String[] WORLDS = {"world", "world_nether"};
    private static final String[] BLOCKS = {"minecraft:stone", "minecraft:oak_stairs[facing=north]", "minecraft:dirt"};
    private static final long START = 1_000_000;
    private static final int TICKS = 80;

    @TempDir
    Path directory;

    static LongStream scenarios() {
        return LongStream.range(0, 300);
    }

    @ParameterizedTest
    @MethodSource("scenarios")
    void reportsTheSameClustersAsAFullRecomputation(long scenario) {
        Random random = new Random(scenario);
        ClusterSettings settings = new ClusterSettings(Duration.ofMillis(600 + random.nextInt(800)),
                1 + random.nextInt(3), Duration.ofMillis(random.nextInt(200)), 1 + random.nextInt(4),
                4 + random.nextInt(14));
        UUID[] players = new UUID[1 + random.nextInt(4)];
        int[][] builders = new int[players.length][];
        for (int i = 0; i < players.length; i++) {
            players[i] = UUID.randomUUID();
            builders[i] = new int[]{random.nextInt(4), random.nextInt(40) - 20, random.nextInt(8), random.nextInt(40) - 20};
        }
        int spread = random.nextInt(3);
        ReferenceTracker reference = new ReferenceTracker();
        Path folder = directory.resolve("s" + scenario);
        Database database = TestDatabase.open(folder);
        BlockChangeStore store = new BlockChangeStore(database);
        long now = START;
        try {
            for (int tick = 0; tick < TICKS; tick++) {
                long previous = now;
                now += random.nextInt(10) == 0 ? 400 + random.nextInt(1200) : 10 + random.nextInt(120);
                int changes = random.nextInt(10) < 3 ? 0 : random.nextInt(12);
                for (int c = 0; c < changes; c++) {
                    int player = random.nextInt(players.length);
                    int[] at = builders[player];
                    if (random.nextInt(20) == 0) {
                        at[0] = random.nextInt(WORLDS.length);
                        at[1] = random.nextInt(40) - 20;
                        at[3] = random.nextInt(40) - 20;
                    }
                    int axis = 1 + random.nextInt(3);
                    at[axis] += (random.nextBoolean() ? 1 : -1) * (1 + random.nextInt(1 + spread));
                    at[2] = Math.floorMod(at[2], 8);
                    TrackedChange change = new TrackedChange(WORLDS[at[0] % WORLDS.length],
                            new BlockPos(at[1], at[2], at[3]), players[player], "p" + player,
                            random.nextInt(3) == 0 ? ChangeKind.BREAK : ChangeKind.PLACE,
                            BLOCKS[random.nextInt(BLOCKS.length)], "minecraft:air",
                            previous + 1 + random.nextLong(now - previous));
                    reference.record(change);
                    store.record(change);
                }
                if (random.nextInt(25) == 0) {
                    store.count().join();
                    database.close();
                    database = TestDatabase.open(folder);
                    store = new BlockChangeStore(database);
                }
                List<Cluster> expected = reference.takeReady(settings, now).stream()
                        .filter(cluster -> !cluster.oversized()).toList();
                List<Cluster> actual = store.takeReady(settings, now).join();
                assertEquals(new HashSet<>(expected), new HashSet<>(actual),
                        "scenario " + scenario + ", tick " + tick + ", " + settings);
                assertEquals(expected.size(), actual.size(), "scenario " + scenario + ", tick " + tick);
            }
        } finally {
            database.close();
        }
    }
}
