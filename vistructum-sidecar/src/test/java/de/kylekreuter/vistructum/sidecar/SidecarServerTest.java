package de.kylekreuter.vistructum.sidecar;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.kylekreuter.vistructum.inference.ModelKind;
import de.kylekreuter.vistructum.inference.ModelRelease;
import de.kylekreuter.vistructum.inference.ReleaseSource;
import de.kylekreuter.vistructum.inference.SceneCodec;
import de.kylekreuter.vistructum.inference.SurfaceScene;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SidecarServerTest {

    @TempDir
    Path models;

    private SidecarServer sidecar;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void start() throws IOException {
        SidecarSettings settings = new SidecarSettings(models, 0, 1, false, "owner/repo", Duration.ofHours(24));
        sidecar = SidecarServer.start(settings, new NoReleases());
    }

    @AfterEach
    void stop() {
        sidecar.close();
        client.close();
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(uri(path)).POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + sidecar.port() + path);
    }

    @Test
    void reportsHealthAndVersionsOfTheBundledModels() throws Exception {
        HttpResponse<String> health = get("/health");
        assertEquals(200, health.statusCode());
        assertEquals("ok", JsonParser.parseString(health.body()).getAsJsonObject().get("status").getAsString());
        assertTrue(SidecarServer.healthy(sidecar.port()));

        JsonObject versions = JsonParser.parseString(get("/version").body()).getAsJsonObject();
        assertEquals("bf-mask-2", versions.getAsJsonObject("mask").get("model_version").getAsString());
        assertTrue(versions.getAsJsonObject("fullscan").get("tta").getAsBoolean());
    }

    @Test
    void infersAScene() throws Exception {
        byte[] modified = new byte[70 * 70];
        JsonObject request = SceneCodec.encode(ModelKind.MASK, SurfaceScene.maskOnly(70, 70, modified));

        HttpResponse<String> response = post("/infer", request.toString());

        assertEquals(200, response.statusCode());
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals("mask", result.get("kind").getAsString());
        assertEquals(4, result.get("windows").getAsInt());
        assertEquals(false, result.get("flagged").getAsBoolean());
    }

    @Test
    void rejectsInvalidRequests() throws Exception {
        assertEquals(400, post("/infer", "not json").statusCode());
        assertEquals(422, post("/infer", "{\"kind\":\"other\",\"width\":1,\"height\":1}").statusCode());
        assertEquals(422, post("/infer", "{\"kind\":\"mask\",\"width\":1,\"height\":1}").statusCode());
        assertEquals(405, get("/infer").statusCode());
    }

    private static final class NoReleases implements ReleaseSource {

        @Override
        public Optional<ModelRelease> latest() {
            return Optional.empty();
        }

        @Override
        public byte[] download(ModelRelease release, ModelRelease.Entry entry) {
            throw new UnsupportedOperationException();
        }
    }
}
