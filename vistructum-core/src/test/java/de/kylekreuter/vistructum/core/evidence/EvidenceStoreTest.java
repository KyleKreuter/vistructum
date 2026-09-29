package de.kylekreuter.vistructum.core.evidence;

import de.kylekreuter.vistructum.api.BlockAction;
import de.kylekreuter.vistructum.api.BlockBox;
import de.kylekreuter.vistructum.api.BlockEvent;
import de.kylekreuter.vistructum.api.Evidence;
import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.FindingCandidate;
import de.kylekreuter.vistructum.api.MotionFrame;
import de.kylekreuter.vistructum.api.Preview;
import de.kylekreuter.vistructum.api.Recording;
import de.kylekreuter.vistructum.api.Source;
import de.kylekreuter.vistructum.api.Verdict;
import de.kylekreuter.vistructum.core.alert.DetectedCandidate;
import de.kylekreuter.vistructum.core.alert.FindingStore;
import de.kylekreuter.vistructum.core.alert.ModelInput;
import de.kylekreuter.vistructum.core.recording.MotionChunk;
import de.kylekreuter.vistructum.core.recording.MotionStore;
import de.kylekreuter.vistructum.core.scene.BlockPos;
import de.kylekreuter.vistructum.core.store.Database;
import de.kylekreuter.vistructum.core.store.TestDatabase;
import de.kylekreuter.vistructum.core.tracking.BlockChangeStore;
import de.kylekreuter.vistructum.core.tracking.ChangeKind;
import de.kylekreuter.vistructum.core.tracking.TrackedChange;
import de.kylekreuter.vistructum.inference.ModelKind;
import de.kylekreuter.vistructum.inference.SurfaceScene;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceStoreTest {

    private static final Instant CREATED = Instant.parse("2026-09-28T20:00:00Z");
    private static final long T = CREATED.toEpochMilli();
    private static final UUID BUILDER = UUID.fromString("e3e04125-846a-3ff7-9f1f-aa61ff8eb6b7");
    private static final UUID FAR_AWAY = UUID.fromString("0f5b2c1e-3a4d-4e6f-8a9b-1c2d3e4f5a6b");
    private static final UUID EARLIER = UUID.fromString("7d8e9f0a-1b2c-4d3e-9f4a-5b6c7d8e9f0a");
    private static final BlockBox BOX = new BlockBox(0, 60, 0, 2, 60, 2);
    private static final BlockBox REGION = new BlockBox(-1, 59, -1, 3, 61, 3);
    private static final EvidenceSettings SETTINGS = new EvidenceSettings(8, Duration.ofSeconds(30), 1);

    @TempDir
    Path directory;
    private Database database;
    private FindingStore findings;
    private BlockChangeStore changes;
    private MotionStore motion;
    private EvidenceStore store;

    @BeforeEach
    void open() {
        database = TestDatabase.open(directory);
        findings = new FindingStore(database);
        changes = new BlockChangeStore(database);
        motion = new MotionStore(database);
        store = new EvidenceStore(database);
    }

    @AfterEach
    void close() {
        database.close();
    }

    @Test
    void securedEvidenceRebuildsTheVolumeBeforeTheFirstChange() throws Exception {
        Finding finding = finding();
        change(new BlockPos(1, 60, 1), ChangeKind.PLACE, "minecraft:stone", "minecraft:air", T - 5_000);
        change(new BlockPos(1, 60, 1), ChangeKind.BREAK, "minecraft:stone", "minecraft:stone", T - 4_000);
        change(new BlockPos(1, 60, 1), ChangeKind.PLACE, "minecraft:oak_planks", "minecraft:air", T - 3_000);
        change(new BlockPos(2, 60, 0), ChangeKind.BREAK, "minecraft:dirt", "minecraft:dirt", T - 2_000);
        change(new BlockPos(50, 60, 50), ChangeKind.PLACE, "minecraft:glass", "minecraft:air", T - 2_000);
        change(new BlockPos(0, 60, 0), ChangeKind.PLACE, "minecraft:glass", "minecraft:air", T + 1_000);

        assertTrue(store.secure(finding.id(), "world", BOX, current(), SETTINGS, CREATED).get());
        Evidence evidence = store.evidence(finding.id()).get().orElseThrow();

        assertEquals("minecraft:air", evidence.before().blockAt(1, 60, 1));
        assertEquals("minecraft:dirt", evidence.before().blockAt(2, 60, 0));
        assertEquals("minecraft:air", evidence.before().blockAt(0, 60, 0));
        assertEquals("minecraft:grass_block", evidence.before().blockAt(3, 59, 3));
        assertEquals(REGION.minX(), evidence.before().minX());
        assertEquals(5, evidence.before().sizeX());
        assertEquals(3, evidence.before().sizeY());
        assertEquals(List.of(
                new BlockEvent(Instant.ofEpochMilli(T - 5_000), BUILDER, "Builder", BlockAction.PLACE, 1, 60, 1,
                        "minecraft:stone"),
                new BlockEvent(Instant.ofEpochMilli(T - 4_000), BUILDER, "Builder", BlockAction.BREAK, 1, 60, 1,
                        "minecraft:stone"),
                new BlockEvent(Instant.ofEpochMilli(T - 3_000), BUILDER, "Builder", BlockAction.PLACE, 1, 60, 1,
                        "minecraft:oak_planks"),
                new BlockEvent(Instant.ofEpochMilli(T - 2_000), BUILDER, "Builder", BlockAction.BREAK, 2, 60, 0,
                        "minecraft:dirt")), evidence.changes());
    }

    @Test
    void recordingsOfNearbyPlayersWithinTheLeadAreCopiedAndTrimmed() throws Exception {
        Finding finding = finding();
        change(new BlockPos(1, 60, 1), ChangeKind.PLACE, "minecraft:stone", "minecraft:air", T - 5_000);
        long from = T - 5_000 - SETTINGS.lead().toMillis();
        motion.append(List.of(
                chunk(BUILDER, "Builder", from - 2_000, from + 3_000, 4, 61, 4),
                chunk(BUILDER, "Builder", T - 1_000, T + 4_000, 1, 61, 1),
                chunk(FAR_AWAY, "Stranger", T - 3_000, T, 100, 64, 100),
                chunk(EARLIER, "Early", from - 10_000, from - 5_000, 1, 61, 1)), 0).get();

        store.secure(finding.id(), "world", BOX, current(), SETTINGS, CREATED).get();
        List<Recording> recordings = store.evidence(finding.id()).get().orElseThrow().recordings();

        assertEquals(1, recordings.size());
        Recording recording = recordings.getFirst();
        assertEquals(BUILDER, recording.player());
        assertEquals("Builder", recording.playerName());
        assertEquals(from, recording.frames().getFirst().atMillis());
        assertEquals(T, recording.frames().getLast().atMillis());
        assertTrue(recording.frames().stream().allMatch(frame -> frame.atMillis() >= from && frame.atMillis() <= T));
    }

    @Test
    void evidenceCascadesWithTheFinding() throws Exception {
        Finding finding = finding();
        change(new BlockPos(1, 60, 1), ChangeKind.PLACE, "minecraft:stone", "minecraft:air", T - 5_000);
        motion.append(List.of(chunk(BUILDER, "Builder", T - 3_000, T, 1, 61, 1)), 0).get();
        store.secure(finding.id(), "world", BOX, current(), SETTINGS, CREATED).get();
        assertTrue(store.hasEvidence(finding.id()).get());
        assertEquals(1, rows("finding_recordings"));

        findings.review(finding.id(), Verdict.FALSE_ALARM, "Staff", CREATED).get();
        findings.deleteReviewedBefore(Verdict.FALSE_ALARM, CREATED.plusSeconds(1)).get();

        assertFalse(store.hasEvidence(finding.id()).get());
        assertEquals(0, rows("finding_evidence"));
        assertEquals(0, rows("finding_changes"));
        assertEquals(0, rows("finding_recordings"));
        assertEquals(1, rows("motion_chunks"));
    }

    @Test
    void missingFindingSecuresNothing() throws Exception {
        assertFalse(store.secure(4711, "world", BOX, current(), SETTINGS, CREATED).get());
        assertTrue(store.evidence(4711).get().isEmpty());
        assertEquals(0, rows("finding_changes"));
    }

    @Test
    void nearMeasuresTheDistanceToTheBoxSurface() {
        assertTrue(EvidenceStore.near(frame(0, 1.5, 61, 1.5), BOX, 0));
        assertTrue(EvidenceStore.near(frame(0, 11, 60, 1), BOX, 8));
        assertFalse(EvidenceStore.near(frame(0, 11.1, 60, 1), BOX, 8));
        assertFalse(EvidenceStore.near(frame(0, 9, 60, 9), BOX, 8));
    }

    private Finding finding() throws Exception {
        return findings.insertUnlessDuplicate(new DetectedCandidate(new FindingCandidate(Source.MASK, "world", BOX,
                0.97, 2, Set.of(BUILDER), "Achse Y", "bf-mask-2", new Preview(1, 1, new byte[]{40})),
                new ModelInput(ModelKind.MASK, SurfaceScene.maskOnly(1, 1, new byte[]{1}), 0, 0, 64, 64)), CREATED,
                Duration.ofDays(14)).get().orElseThrow();
    }

    private void change(BlockPos pos, ChangeKind kind, String blockData, String previousData, long changedAt) {
        changes.record(new TrackedChange("world", pos, BUILDER, "Builder", kind, blockData, previousData, changedAt));
    }

    private static VolumeSnapshot current() {
        VolumeSnapshot.Builder builder = new VolumeSnapshot.Builder(REGION);
        VolumeSnapshot shape = new VolumeSnapshot(REGION, List.of("x"), new int[(int) VolumeSnapshot.volume(REGION)]);
        for (int y = REGION.minY(); y <= REGION.maxY(); y++) {
            for (int z = REGION.minZ(); z <= REGION.maxZ(); z++) {
                for (int x = REGION.minX(); x <= REGION.maxX(); x++) {
                    String block = y == 59 ? "minecraft:grass_block" : "minecraft:air";
                    if (x == 1 && y == 60 && z == 1) {
                        block = "minecraft:oak_planks";
                    }
                    builder.set(shape.index(x, y, z), block);
                }
            }
        }
        return builder.build();
    }

    private static MotionChunk chunk(UUID player, String name, long start, long end, double x, double y, double z) {
        List<MotionFrame> frames = new ArrayList<>();
        for (long at = start; at <= end; at += 50) {
            frames.add(frame(at, x, y, z));
        }
        return new MotionChunk(player, name, "world", frames);
    }

    private static MotionFrame frame(long at, double x, double y, double z) {
        return new MotionFrame(at, x, y, z, 0f, 0f, MotionFrame.ON_GROUND, "minecraft:air");
    }

    private int rows(String table) throws Exception {
        return database.transaction(connection -> {
            try (PreparedStatement select = connection.prepareStatement("SELECT count(*) FROM " + table);
                 ResultSet rows = select.executeQuery()) {
                return rows.getInt(1);
            }
        }).get();
    }
}
