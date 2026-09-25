package de.kylekreuter.vistructum.sidecar;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.kylekreuter.vistructum.inference.GitHubReleases;
import de.kylekreuter.vistructum.inference.InferResult;
import de.kylekreuter.vistructum.inference.InferenceEngine;
import de.kylekreuter.vistructum.inference.ModelFiles;
import de.kylekreuter.vistructum.inference.ModelInfo;
import de.kylekreuter.vistructum.inference.ModelKind;
import de.kylekreuter.vistructum.inference.ReleaseSource;
import de.kylekreuter.vistructum.inference.SceneCodec;
import de.kylekreuter.vistructum.inference.SurfaceScene;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SidecarServer implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger("vistructum.sidecar");
    private static final int HTTP_THREADS = 4;

    private final Gson gson = new GsonBuilder().serializeNulls().create();
    private final InferenceEngine engine;
    private final HttpServer server;
    private final ExecutorService handlers = Executors.newFixedThreadPool(HTTP_THREADS);
    private final ScheduledExecutorService updates = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("vistructum-update-schedule").daemon().factory());

    private SidecarServer(InferenceEngine engine, HttpServer server) {
        this.engine = engine;
        this.server = server;
    }

    public static void main(String[] args) throws IOException {
        SidecarSettings settings = SidecarSettings.fromEnvironment(System.getenv());
        if (args.length == 1 && args[0].equals("health")) {
            System.exit(healthy(settings.port()) ? 0 : 1);
        }
        SidecarServer sidecar = start(settings, GitHubReleases.of(settings.repository()));
        Runtime.getRuntime().addShutdownHook(new Thread(sidecar::close));
        LOGGER.info("listening on port " + sidecar.port());
    }

    public static SidecarServer start(SidecarSettings settings, ReleaseSource releases) throws IOException {
        InferenceEngine engine = InferenceEngine.start(new ModelFiles(settings.modelDirectory()), settings.threads(),
                LOGGER);
        HttpServer http = HttpServer.create(new InetSocketAddress(settings.port()), 0);
        SidecarServer sidecar = new SidecarServer(engine, http);
        http.createContext("/health", exchange -> sidecar.handle(exchange, "GET", sidecar::health));
        http.createContext("/version", exchange -> sidecar.handle(exchange, "GET", sidecar::version));
        http.createContext("/infer", exchange -> sidecar.handle(exchange, "POST", sidecar::infer));
        http.setExecutor(sidecar.handlers);
        http.start();
        if (settings.autoUpdate()) {
            long hours = settings.checkInterval().toHours();
            sidecar.updates.scheduleAtFixedRate(() -> engine.update(releases).whenComplete((installed, error) -> {
                if (error != null) {
                    LOGGER.warning("model update check failed: " + rootMessage(error));
                }
            }), 0, hours, TimeUnit.HOURS);
        }
        return sidecar;
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        updates.shutdownNow();
        server.stop(0);
        handlers.shutdownNow();
        engine.close();
    }

    private Response health(HttpExchange exchange) {
        Map<ModelKind, ModelInfo> models = engine.models().join();
        Map<String, Boolean> loaded = new LinkedHashMap<>();
        for (ModelKind kind : ModelKind.values()) {
            loaded.put(kind.id(), models.containsKey(kind));
        }
        String status = models.size() == ModelKind.values().length ? "ok" : "degraded";
        return new Response(models.isEmpty() ? 503 : 200, Map.of("status", status, "models", loaded));
    }

    private Response version(HttpExchange exchange) {
        Map<String, ModelInfo> versions = new LinkedHashMap<>();
        engine.models().join().forEach((kind, info) -> versions.put(kind.id(), info));
        return new Response(200, versions);
    }

    private Response infer(HttpExchange exchange) throws IOException {
        JsonObject body;
        try {
            body = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            return error(400, "body is not a JSON object");
        }
        Optional<ModelKind> kind = body.has("kind") && body.get("kind").isJsonPrimitive()
                ? ModelKind.byId(body.get("kind").getAsString()) : Optional.empty();
        if (kind.isEmpty()) {
            return error(422, "unknown kind " + body.get("kind"));
        }
        if (!engine.models().join().containsKey(kind.get())) {
            return error(503, "model for kind '" + kind.get().id() + "' not loaded");
        }
        SurfaceScene scene;
        try {
            scene = SceneCodec.decode(kind.get(), body);
        } catch (IllegalArgumentException e) {
            return error(422, e.getMessage());
        }
        InferResult result = engine.infer(kind.get(), scene).join();
        return new Response(200, result);
    }

    private void handle(HttpExchange exchange, String method, Handler handler) throws IOException {
        try (exchange) {
            Response response;
            try {
                response = exchange.getRequestMethod().equals(method) ? handler.handle(exchange)
                        : error(405, "use " + method);
            } catch (IOException | RuntimeException e) {
                LOGGER.log(Level.WARNING, exchange.getRequestURI() + " failed", e);
                response = error(500, rootMessage(e));
            }
            byte[] bytes = gson.toJson(response.body()).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    private static Response error(int status, String detail) {
        return new Response(status, Map.of("detail", detail));
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    static boolean healthy(int port) {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/health"))
                .timeout(Duration.ofSeconds(2)).GET().build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private interface Handler {
        Response handle(HttpExchange exchange) throws IOException;
    }

    private record Response(int status, Object body) {
    }
}
