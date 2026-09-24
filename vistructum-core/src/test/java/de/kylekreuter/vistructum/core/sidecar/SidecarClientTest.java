package de.kylekreuter.vistructum.core.sidecar;

import com.sun.net.httpserver.HttpServer;
import de.kylekreuter.vistructum.core.scene.SurfaceScene;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SidecarClientTest {

    private HttpServer server;
    private SidecarClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.start();
        client = new SidecarClient("localhost", server.getAddress().getPort(), Duration.ofSeconds(2), Duration.ofSeconds(2));
    }

    @AfterEach
    void stopServer() {
        client.close();
        server.stop(0);
    }

    @Test
    void inferParsesSuccessResponseWithDetections() throws Exception {
        server.createContext("/infer", exchange -> respond(exchange, 200, """
                {"kind":"fullscan","model_version":"bf-bin-1","threshold":0.75,"min_votes":2,"windows":40,
                 "max_score":0.91,"flagged":true,"elapsed_ms":12.5,
                 "detections":[{"top":1,"left":2,"bottom":9,"right":10,"score":0.91,"votes":3}]}"""));

        InferResult result = client.infer("fullscan", tinyScene()).get(2, TimeUnit.SECONDS);

        assertEquals("fullscan", result.kind());
        assertEquals("bf-bin-1", result.modelVersion());
        assertEquals(0.75, result.threshold());
        assertEquals(2, result.minVotes());
        assertEquals(40, result.windows());
        assertEquals(0.91, result.maxScore());
        assertTrue(result.flagged());
        assertEquals(12.5, result.elapsedMs());
        assertEquals(1, result.detections().size());
        assertEquals(new Detection(1, 2, 9, 10, 0.91, 3), result.detections().get(0));
    }

    @Test
    void infer503CompletesExceptionallyWithStatusAndBody() {
        String body = "{\"detail\":\"model for kind 'fullscan' not loaded\"}";
        server.createContext("/infer", exchange -> respond(exchange, 503, body));

        CompletableFuture<InferResult> future = client.infer("fullscan", tinyScene());

        SidecarException exception = assertSidecarException(future);
        assertEquals(503, exception.status());
        assertEquals(body, exception.body());
    }

    @Test
    void infer422CompletesExceptionallyWithStatusAndBody() {
        String body = "{\"detail\":\"'blocks' has 3 int16 values, expected 4\"}";
        server.createContext("/infer", exchange -> respond(exchange, 422, body));

        CompletableFuture<InferResult> future = client.infer("fullscan", tinyScene());

        SidecarException exception = assertSidecarException(future);
        assertEquals(422, exception.status());
        assertEquals(body, exception.body());
    }

    @Test
    void health200IsParsedAsOk() throws Exception {
        server.createContext("/health", exchange -> respond(exchange, 200,
                "{\"status\":\"ok\",\"models\":{\"mask\":true,\"fullscan\":true}}"));

        Health health = client.health().get(2, TimeUnit.SECONDS);

        assertEquals("ok", health.status());
        assertEquals(true, health.models().get("mask"));
        assertEquals(true, health.models().get("fullscan"));
    }

    @Test
    void health503IsParsedAsDegradedNotAsAnException() throws Exception {
        server.createContext("/health", exchange -> respond(exchange, 503,
                "{\"status\":\"degraded\",\"models\":{\"mask\":false,\"fullscan\":true}}"));

        Health health = client.health().get(2, TimeUnit.SECONDS);

        assertEquals("degraded", health.status());
        assertEquals(false, health.models().get("mask"));
        assertEquals(true, health.models().get("fullscan"));
    }

    @Test
    void requestTimeoutCompletesExceptionally() throws IOException {
        server.createContext("/health", exchange -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, "{\"status\":\"ok\",\"models\":{}}");
        });
        SidecarClient shortTimeoutClient = new SidecarClient(
                "localhost", server.getAddress().getPort(), Duration.ofSeconds(2), Duration.ofMillis(100));

        CompletableFuture<Health> future = shortTimeoutClient.health();

        ExecutionException exception = assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
        assertTrue(exception.getCause() instanceof java.net.http.HttpTimeoutException);
        shortTimeoutClient.close();
    }

    @Test
    void futureCompletesOffTheCallingThread() throws Exception {
        server.createContext("/health", exchange -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, "{\"status\":\"ok\",\"models\":{}}");
        });
        Thread callingThread = Thread.currentThread();

        long start = System.nanoTime();
        CompletableFuture<Thread> completionThread = client.health()
                .handle((health, error) -> Thread.currentThread());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsedMs < 200, "health() must return before the server responds, took " + elapsedMs + "ms");
        assertNotEquals(callingThread, completionThread.get(2, TimeUnit.SECONDS));
    }

    private static SidecarException assertSidecarException(CompletableFuture<?> future) {
        CompletionException wrapper = assertThrows(CompletionException.class, future::join);
        assertTrue(wrapper.getCause() instanceof SidecarException, "expected SidecarException, got " + wrapper.getCause());
        return (SidecarException) wrapper.getCause();
    }

    private static SurfaceScene tinyScene() {
        return SurfaceScene.maskOnly(2, 2, new byte[] {0, 1, 0, 1});
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        exchange.getRequestBody().readAllBytes();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
