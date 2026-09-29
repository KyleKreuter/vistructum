package de.kylekreuter.vistructum.core.alert;

import de.kylekreuter.vistructum.api.BlockBox;
import de.kylekreuter.vistructum.api.BlockVolume;
import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.FindingCandidate;
import de.kylekreuter.vistructum.api.FindingTerrain;
import de.kylekreuter.vistructum.api.Preview;
import de.kylekreuter.vistructum.api.Source;
import de.kylekreuter.vistructum.core.store.Database;
import de.kylekreuter.vistructum.core.store.TestDatabase;
import de.kylekreuter.vistructum.inference.ModelKind;
import de.kylekreuter.vistructum.inference.SurfaceScene;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainStoreTest {

    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");
    private static final Duration DEDUPE = Duration.ofDays(7);
    private static final BlockVolume BLOCKS = new BlockVolume(10, 58, 20, 2, 2, 1,
            List.of("minecraft:stone", "minecraft:air", "minecraft:oak_leaves[distance=1,persistent=false]"),
            new int[]{0, 0, 2, 1});

    @TempDir
    Path directory;

    private Database database;
    private FindingStore findings;
    private TerrainStore store;

    @BeforeEach
    void open() throws Exception {
        database = TestDatabase.open(directory);
        findings = new FindingStore(database);
        store = new TerrainStore(database);
    }

    @AfterEach
    void close() {
        database.close();
    }

    @Test
    void terrainIsStoredWithTheFinding() throws Exception {
        FindingTerrain terrain = new FindingTerrain(BLOCKS, Optional.of(new FindingTerrain.SceneOrigin(0, 256)));
        Finding finding = insert(new BlockBox(10, 60, 20, 11, 60, 20), Optional.of(terrain));
        assertTrue(store.hasTerrain(finding.id()).get());
        assertEquals(Optional.of(terrain), store.terrain(finding.id()).get());
    }

    @Test
    void terrainWithoutSceneOriginRoundTrips() throws Exception {
        FindingTerrain terrain = new FindingTerrain(BLOCKS, Optional.empty());
        Finding finding = insert(new BlockBox(10, 60, 20, 11, 60, 20), Optional.of(terrain));
        assertEquals(Optional.of(terrain), store.terrain(finding.id()).get());
    }

    @Test
    void findingsWithoutTerrainHaveNone() throws Exception {
        Finding finding = insert(new BlockBox(100, 60, 20, 101, 60, 20), Optional.empty());
        assertFalse(store.hasTerrain(finding.id()).get());
        assertTrue(store.terrain(finding.id()).get().isEmpty());
        assertTrue(store.terrain(4711).get().isEmpty());
    }

    private Finding insert(BlockBox box, Optional<FindingTerrain> terrain) throws Exception {
        FindingCandidate candidate = new FindingCandidate(Source.FULLSCAN, "world", box, 0.9, 3, Set.of(),
                "Kachel 0,256", "bf-scan-2", new Preview(1, 1, new byte[]{12}));
        ModelInput input = new ModelInput(ModelKind.FULLSCAN, SurfaceScene.maskOnly(2, 1, new byte[]{1, 0}), 0, 0,
                1, 2);
        return findings.insertUnlessDuplicate(new DetectedCandidate(candidate, input, terrain), NOW, DEDUPE).get()
                .orElseThrow();
    }
}
