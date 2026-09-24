package de.kylekreuter.vistructum.core.sidecar;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.kylekreuter.vistructum.core.scene.SurfaceScene;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Non-blocking HTTP client for the vistructum sidecar. Every call returns immediately; the request runs on the
 * {@link HttpClient}'s own executor, never on the calling thread, so it is safe to call from the server main thread.
 */
public final class SidecarClient implements AutoCloseable {

    private final HttpClient httpClient;
    private final URI baseUri;
    private final Duration requestTimeout;
    private final Gson gson = new Gson();

    public SidecarClient(String host, int port, Duration connectTimeout, Duration requestTimeout) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        this.baseUri = URI.create("http://" + host + ":" + port);
        this.requestTimeout = requestTimeout;
    }

    public CompletableFuture<InferResult> infer(String kind, SurfaceScene scene) {
        HttpRequest request = requestBuilder("/infer")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(SceneCodec.encode(kind, scene))))
                .build();
        return send(request).thenApply(response -> {
            if (response.statusCode() / 100 != 2) {
                throw new SidecarException(response.statusCode(), response.body());
            }
            return gson.fromJson(response.body(), InferResult.class);
        });
    }

    public CompletableFuture<Health> health() {
        HttpRequest request = requestBuilder("/health").GET().build();
        return send(request).thenApply(response -> gson.fromJson(response.body(), Health.class));
    }

    public CompletableFuture<JsonObject> version() {
        HttpRequest request = requestBuilder("/version").GET().build();
        return send(request).thenApply(response -> JsonParser.parseString(response.body()).getAsJsonObject());
    }

    @Override
    public void close() {
        httpClient.close();
    }

    private HttpRequest.Builder requestBuilder(String path) {
        return HttpRequest.newBuilder(baseUri.resolve(path)).timeout(requestTimeout);
    }

    private CompletableFuture<HttpResponse<String>> send(HttpRequest request) {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }
}
