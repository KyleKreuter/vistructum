package de.kylekreuter.vistructum.inference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubReleasesTest {

    private final Map<String, byte[]> routes = new HashMap<>();
    private HttpServer server;
    private String base;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = routes.get(exchange.getRequestURI().toString());
            exchange.sendResponseHeaders(body == null ? 404 : 200, body == null ? -1 : body.length);
            if (body != null) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
            exchange.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private GitHubReleases releases() {
        return new GitHubReleases(HttpClient.newHttpClient(), URI.create(base), "owner/repo");
    }

    private void route(String path, String body) {
        routes.put(path, body.getBytes(StandardCharsets.UTF_8));
    }

    private String release(String tag, boolean draft, String... assets) {
        StringBuilder list = new StringBuilder();
        for (String asset : assets) {
            list.append(list.isEmpty() ? "" : ",").append("{\"name\":\"").append(asset)
                    .append("\",\"browser_download_url\":\"").append(base).append("dl/").append(tag).append('/')
                    .append(asset).append("\"}");
        }
        return "{\"tag_name\":\"" + tag + "\",\"draft\":" + draft + ",\"prerelease\":false,\"assets\":[" + list + "]}";
    }

    @Test
    void picksTheNewestPublishedModelReleaseAndDownloadsItsAssets() throws IOException {
        route("/repos/owner/repo/releases?per_page=30", "[" + release("v1.0.0", false, "plugin.jar") + ","
                + release("models-new", true, "models.json") + "," + release("models-abc", false, "models.json",
                "mask.onnx") + "]");
        route("/dl/models-abc/models.json", "{\"models\":[{\"kind\":\"mask\",\"file\":\"mask.onnx\",\"sha256\":\"ff\","
                + "\"model_version\":\"bf-mask-3\",\"feature_spec\":\"fs-1\"}]}");
        route("/dl/models-abc/mask.onnx", "model-bytes");

        ModelRelease release = releases().latest().orElseThrow();

        assertEquals("models-abc", release.tag());
        ModelRelease.Entry entry = release.models().getFirst();
        assertEquals(new ModelRelease.Entry("mask", "mask.onnx", "ff", "bf-mask-3", "fs-1"), entry);
        assertArrayEquals("model-bytes".getBytes(StandardCharsets.UTF_8), releases().download(release, entry));
    }

    @Test
    void reportsNoReleaseWhenNoneCarriesModels() throws IOException {
        route("/repos/owner/repo/releases?per_page=30", "[" + release("v1.0.0", false, "plugin.jar") + "]");

        assertTrue(releases().latest().isEmpty());
    }

    @Test
    void failsOnHttpErrors() {
        assertThrows(IOException.class, () -> releases().latest());
    }
}
