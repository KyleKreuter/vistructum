package de.kylekreuter.vistructum.core.alert;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.kylekreuter.vistructum.api.BlockBox;
import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.FindingCandidate;
import de.kylekreuter.vistructum.api.Preview;
import de.kylekreuter.vistructum.api.Source;
import de.kylekreuter.vistructum.api.TrainingExport;
import de.kylekreuter.vistructum.api.Verdict;
import de.kylekreuter.vistructum.core.store.Database;
import de.kylekreuter.vistructum.core.store.TestDatabase;
import de.kylekreuter.vistructum.inference.ModelKind;
import de.kylekreuter.vistructum.inference.SceneCodec;
import de.kylekreuter.vistructum.inference.SurfaceScene;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FindingExporterTest {

    private static final Instant NOW = Instant.parse("2026-09-26T08:15:30.250Z");
    private static final Duration DEDUPE = Duration.ofDays(14);
    private static final int MASK_FINDINGS = 40;

    @TempDir
    Path directory;
    private Database database;
    private FindingStore store;
    private FindingExporter exporter;

    @BeforeEach
    void open() {
        database = TestDatabase.open(directory);
        store = new FindingStore(database);
        exporter = new FindingExporter(store, directory.resolve("exports"), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void close() {
        database.close();
    }

    private Finding insert(Source source, int x, ModelInput input) throws Exception {
        FindingCandidate candidate = new FindingCandidate(source, "world", new BlockBox(x, 60, 0, x + 10, 62, 10), 0.9,
                2, Set.of(), "d", input.kind() == ModelKind.MASK ? "bf-mask-3" : "bf-scan-2",
                new Preview(1, 1, new byte[]{40}));
        return store.insertUnlessDuplicate(new DetectedCandidate(candidate, input), NOW, DEDUPE).get().orElseThrow();
    }

    private static ModelInput mask(int seed) {
        byte[] modified = new byte[70 * 50];
        for (int i = seed; i < modified.length; i += 7) {
            modified[i] = 1;
        }
        return new ModelInput(ModelKind.MASK, SurfaceScene.maskOnly(70, 50, modified), 0, 6, 64, 70);
    }

    private static ModelInput surface() {
        int count = 80 * 80;
        short[] blocks = new short[count];
        short[] heights = new short[count];
        byte[] luminance = new byte[count];
        for (int i = 0; i < count; i++) {
            blocks[i] = (short) (i % 5 == 0 ? SurfaceScene.UNKNOWN : i % 31);
            heights[i] = (short) (60 + i % 9);
            luminance[i] = (byte) (i * 13);
        }
        return new ModelInput(ModelKind.FULLSCAN, new SurfaceScene(80, 80, blocks, heights, luminance, new byte[count]),
                16, 16, 80, 80);
    }

    @Test
    void exportWritesOneLinePerReviewedFindingAndKind() throws Exception {
        for (int i = 0; i < MASK_FINDINGS; i++) {
            Finding finding = insert(Source.MASK, i * 100, mask(i % 7));
            store.review(finding.id(), i % 4 == 0 ? Verdict.FALSE_ALARM : Verdict.CONFIRMED, "Staff", NOW).get();
        }
        ModelInput surface = surface();
        Finding scanned = insert(Source.FULLSCAN, -1000, surface);
        store.review(scanned.id(), Verdict.FALSE_ALARM, "Staff", NOW).get();
        insert(Source.MASK, -2000, mask(1));

        TrainingExport export = exporter.export().get();

        assertEquals(directory.resolve("exports").resolve("findings-20260926-081530-250"), export.directory());
        assertEquals(Map.of("mask", (long) MASK_FINDINGS, "fullscan", 1L), export.samples());
        assertEquals(MASK_FINDINGS + 1, export.total());
        assertEquals(0, export.skipped());
        List<String> maskLines = Files.readAllLines(export.directory().resolve("mask.jsonl"), StandardCharsets.UTF_8);
        assertEquals(MASK_FINDINGS, maskLines.size());
        JsonObject first = JsonParser.parseString(maskLines.getFirst()).getAsJsonObject();
        assertEquals(1, first.get("finding").getAsLong());
        assertEquals("mask", first.get("source").getAsString());
        assertEquals("FALSE_ALARM", first.get("verdict").getAsString());
        assertEquals("bf-mask-3", first.get("model_version").getAsString());
        assertEquals("mask", first.get("kind").getAsString());
        assertEquals(6, first.getAsJsonObject("window").get("left").getAsInt());
        assertEquals(70, first.getAsJsonObject("window").get("right").getAsInt());
        FindingStoreTest.assertSceneEquals(mask(0).scene(), SceneCodec.decode(ModelKind.MASK, first));
        assertEquals("CONFIRMED", JsonParser.parseString(maskLines.get(1)).getAsJsonObject().get("verdict").getAsString());

        List<String> surfaceLines = Files.readAllLines(export.directory().resolve("fullscan.jsonl"),
                StandardCharsets.UTF_8);
        assertEquals(1, surfaceLines.size());
        JsonObject line = JsonParser.parseString(surfaceLines.getFirst()).getAsJsonObject();
        assertEquals(scanned.id(), line.get("finding").getAsLong());
        assertEquals("fullscan", line.get("source").getAsString());
        assertEquals("fullscan", line.get("kind").getAsString());
        FindingStoreTest.assertSceneEquals(surface.scene(), SceneCodec.decode(ModelKind.FULLSCAN, line));
    }

    @Test
    void volumeFindingsOfTheScanPathAreExportedAsMaskScenes() throws Exception {
        Finding volume = insert(Source.FULLSCAN, 0, mask(3));
        store.review(volume.id(), Verdict.CONFIRMED, "Staff", NOW).get();

        TrainingExport export = exporter.export().get();

        assertEquals(Map.of("mask", 1L), export.samples());
        assertFalse(Files.exists(export.directory().resolve("fullscan.jsonl")));
        JsonObject line = JsonParser.parseString(Files.readString(export.directory().resolve("mask.jsonl")).strip())
                .getAsJsonObject();
        assertEquals("fullscan", line.get("source").getAsString());
        assertEquals("mask", line.get("kind").getAsString());
    }

    @Test
    void emptyExportCreatesAnEmptyDirectoryAndReportsSkippedFindings() throws Exception {
        Finding finding = insert(Source.MASK, 0, mask(0));
        database.transaction(connection -> connection.createStatement()
                .executeUpdate("DELETE FROM finding_scenes WHERE finding_id = " + finding.id())).get();
        store.review(finding.id(), Verdict.CONFIRMED, "Staff", NOW).get();

        TrainingExport export = exporter.export().get();

        assertTrue(export.samples().isEmpty());
        assertEquals(1, export.skipped());
        assertTrue(Files.isDirectory(export.directory()));
        try (Stream<Path> files = Files.list(export.directory())) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void secondExportAtTheSameInstantFails() throws Exception {
        exporter.export().get();

        assertThrows(Exception.class, () -> exporter.export().get());
    }
}
