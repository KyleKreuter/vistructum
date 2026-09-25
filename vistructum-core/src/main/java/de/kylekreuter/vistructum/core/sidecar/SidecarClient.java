package de.kylekreuter.vistructum.core.sidecar;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.kylekreuter.vistructum.inference.InferResult;
import de.kylekreuter.vistructum.inference.ModelKind;
import de.kylekreuter.vistructum.inference.SceneCodec;
import de.kylekreuter.vistructum.inference.SurfaceScene;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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

    public CompletableFuture<InferResult> infer(ModelKind kind, SurfaceScene scene, JsonObject context) {
        JsonObject body = SceneCodec.encode(kind, scene);
        if (context != null) {
            body.add("context", context);
        }
        HttpRequest request = requestBuilder("/infer")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();
        return send(request).thenApply(response -> gson.fromJson(successful(response), InferResult.class));
    }

    public CompletableFuture<Map<String, String>> versions() {
        HttpRequest request = requestBuilder("/version").GET().build();
        return send(request).thenApply(response -> {
            Map<String, String> versions = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : JsonParser.parseString(successful(response)).getAsJsonObject()
                    .entrySet()) {
                versions.put(entry.getKey(), entry.getValue().getAsJsonObject().get("model_version").getAsString());
            }
            return versions;
        });
    }

    @Override
    public void close() {
        httpClient.close();
    }

    private static String successful(HttpResponse<String> response) {
        if (response.statusCode() / 100 != 2) {
            throw new SidecarException(response.statusCode(), response.body());
        }
        return response.body();
    }

    private HttpRequest.Builder requestBuilder(String path) {
        return HttpRequest.newBuilder(baseUri.resolve(path)).timeout(requestTimeout);
    }

    private CompletableFuture<HttpResponse<String>> send(HttpRequest request) {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }
}
