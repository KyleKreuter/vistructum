package de.kylekreuter.vistructum.inference;

import ai.onnxruntime.OrtEnvironment;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ParityTest {

    private static final String STALE = "stale parity fixture, regenerate with: PYTHONPATH=ml:ml/train python3 ml/parity.py";
    private static final double SCORE_TOLERANCE = 1e-5;

    @ParameterizedTest
    @ValueSource(strings = {"mask-small", "mask-area", "fullscan-small", "fullscan-area", "fullscan-unknown"})
    void matchesThePythonRuntime(String name) throws IOException, ModelException {
        JsonObject fixture = read(name);
        ModelKind kind = ModelKind.byId(fixture.get("kind").getAsString()).orElseThrow();
        SurfaceScene scene = SceneCodec.decode(kind, fixture);
        byte[] bundled = ModelFiles.bundled(kind).orElseThrow();
        assertEquals(fixture.get("model_sha256").getAsString(), Model.sha256(bundled), STALE);

        FeatureMap features = Features.extract(kind, scene);
        assertArrayEquals(Base64.getDecoder().decode(fixture.get("features").getAsString()), features.values(), name);

        WindowBatch batch = Features.windows(kind, features);
        JsonArray positions = fixture.getAsJsonArray("positions");
        assertEquals(positions.size(), batch.size());
        for (int i = 0; i < batch.size(); i++) {
            assertEquals(positions.get(i).getAsJsonArray().get(0).getAsInt(), batch.tops()[i]);
            assertEquals(positions.get(i).getAsJsonArray().get(1).getAsInt(), batch.lefts()[i]);
        }

        try (Model model = Model.load(OrtEnvironment.getEnvironment(), bundled, 1)) {
            Scoring.Scores scores = model.score(batch);
            JsonArray expected = fixture.getAsJsonArray("scores");
            for (int i = 0; i < expected.size(); i++) {
                assertEquals(expected.get(i).getAsDouble(), scores.values()[i], SCORE_TOLERANCE, name + " window " + i);
            }
            assertEquals(fixture.get("refined").getAsInt(), scores.refined());

            InferResult result = model.infer(scene);
            assertEquals(detections(fixture.getAsJsonArray("detections")), rounded(result.detections()));
            assertEquals(!result.detections().isEmpty(), result.flagged());
        }
    }

    private static List<String> detections(JsonArray array) {
        List<String> out = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject d = element.getAsJsonObject();
            out.add(describe(d.get("top").getAsInt(), d.get("left").getAsInt(), d.get("bottom").getAsInt(),
                    d.get("right").getAsInt(), d.get("score").getAsDouble(), d.get("votes").getAsInt()));
        }
        return out;
    }

    private static List<String> rounded(List<Detection> detections) {
        return detections.stream().map(d -> describe(d.top(), d.left(), d.bottom(), d.right(), d.score(), d.votes()))
                .toList();
    }

    private static String describe(int top, int left, int bottom, int right, double score, int votes) {
        return top + "," + left + "," + bottom + "," + right + " votes " + votes + " score "
                + String.format(Locale.ROOT, "%.4f", score);
    }

    private static JsonObject read(String name) throws IOException {
        try (Reader reader = new InputStreamReader(ParityTest.class.getResourceAsStream("/parity/" + name + ".json"),
                StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
