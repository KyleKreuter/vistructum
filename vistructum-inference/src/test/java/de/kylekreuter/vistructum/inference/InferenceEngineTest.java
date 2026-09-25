package de.kylekreuter.vistructum.inference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InferenceEngineTest {

    private static final Logger LOGGER = Logger.getLogger("test");

    @TempDir
    Path directory;

    @Test
    void loadsTheBundledModelsWhenTheDirectoryIsEmpty() {
        try (InferenceEngine engine = InferenceEngine.start(new ModelFiles(directory), 1, LOGGER)) {
            Map<ModelKind, ModelInfo> models = engine.models().join();

            assertEquals(Model.sha256(TestModels.bundled(ModelKind.MASK)), models.get(ModelKind.MASK).sha256());
            assertEquals(Model.sha256(TestModels.bundled(ModelKind.FULLSCAN)), models.get(ModelKind.FULLSCAN).sha256());
        }
    }

    @Test
    void prefersAStoredModelAndIgnoresAnInvalidOne() throws Exception {
        Files.write(directory.resolve("mask.onnx"), TestModels.testMask());
        Files.write(directory.resolve("fullscan.onnx"), new byte[]{1, 2, 3});

        try (InferenceEngine engine = InferenceEngine.start(new ModelFiles(directory), 1, LOGGER)) {
            Map<ModelKind, ModelInfo> models = engine.models().join();

            assertEquals("test-mask-1", models.get(ModelKind.MASK).version());
            assertEquals(Model.sha256(TestModels.bundled(ModelKind.FULLSCAN)), models.get(ModelKind.FULLSCAN).sha256());
        }
    }

    @Test
    void installsANewerCompatibleModelAndStoresIt() throws Exception {
        try (InferenceEngine engine = InferenceEngine.start(new ModelFiles(directory), 1, LOGGER)) {
            FakeReleases releases = new FakeReleases().offer("mask", TestModels.testMask(), Contract.FEATURE_SPEC);

            List<ModelInfo> installed = engine.update(releases).join();

            assertEquals(List.of("test-mask-1"), installed.stream().map(ModelInfo::version).toList());
            assertEquals("test-mask-1", engine.models().join().get(ModelKind.MASK).version());
            assertArrayEquals(TestModels.testMask(), Files.readAllBytes(directory.resolve("mask.onnx")));
            InferResult result = engine.infer(ModelKind.MASK, SurfaceScene.maskOnly(64, 64, new byte[64 * 64])).join();
            assertEquals("test-mask-1", result.modelVersion());
            assertFalse(result.flagged());
        }
    }

    @Test
    void skipsModelsThatAreAlreadyActive() {
        try (InferenceEngine engine = InferenceEngine.start(new ModelFiles(directory), 1, LOGGER)) {
            FakeReleases releases = new FakeReleases()
                    .offer("mask", TestModels.bundled(ModelKind.MASK), Contract.FEATURE_SPEC);

            assertTrue(engine.update(releases).join().isEmpty());
            assertEquals(0, releases.downloads);
        }
    }

    @Test
    void skipsModelsForAnotherFeatureSpec() {
        try (InferenceEngine engine = InferenceEngine.start(new ModelFiles(directory), 1, LOGGER)) {
            FakeReleases releases = new FakeReleases().offer("mask", TestModels.testMask(), "fs-2");

            assertTrue(engine.update(releases).join().isEmpty());
            assertEquals(0, releases.downloads);
            assertFalse(Files.exists(directory.resolve("mask.onnx")));
        }
    }

    @Test
    void rejectsADownloadWithAWrongChecksum() {
        try (InferenceEngine engine = InferenceEngine.start(new ModelFiles(directory), 1, LOGGER)) {
            FakeReleases releases = new FakeReleases().offer("mask", TestModels.testMask(), "00", Contract.FEATURE_SPEC);

            assertTrue(engine.update(releases).join().isEmpty());
            assertFalse(Files.exists(directory.resolve("mask.onnx")));
        }
    }

    @Test
    void rejectsAModelOfTheWrongKind() {
        try (InferenceEngine engine = InferenceEngine.start(new ModelFiles(directory), 1, LOGGER)) {
            FakeReleases releases = new FakeReleases()
                    .offer("fullscan", TestModels.testMask(), Contract.FEATURE_SPEC);

            assertTrue(engine.update(releases).join().isEmpty());
            assertFalse(Files.exists(directory.resolve("fullscan.onnx")));
        }
    }
}
