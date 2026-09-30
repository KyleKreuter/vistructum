package de.kylekreuter.vistructum.ui.integration.discord;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordWebhookTest {

    private record Received(String method, String path, String query, String contentType, String body) {
    }

    private record Reply(int status, String body) {
    }

    private final List<Received> received = new CopyOnWriteArrayList<>();
    private final Deque<Reply> replies = new ArrayDeque<>();
    private HttpServer server;
    private DiscordWebhook webhook;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::handle);
        server.start();
        URI url = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/webhooks/1/token?thread_id=5");
        webhook = new DiscordWebhook(url, Logger.getAnonymousLogger());
    }

    @AfterEach
    void stop() {
        webhook.close();
        server.stop(0);
    }

    @Test
    void messageWithImageIsSentAsMultipartAndReturnsItsId() throws Exception {
        replies.add(new Reply(200, "{\"id\":\"111\"}"));

        Optional<String> id = webhook.send(payload(), Optional.of(new DiscordWebhook.Image("finding-7.png",
                new byte[]{1, 2, 3}))).get(10, TimeUnit.SECONDS);

        assertEquals(Optional.of("111"), id);
        Received request = received.getFirst();
        assertEquals("POST", request.method());
        assertEquals("/api/webhooks/1/token", request.path());
        assertEquals("thread_id=5&wait=true&with_components=true", request.query());
        assertTrue(request.contentType().startsWith("multipart/form-data; boundary="));
        assertTrue(request.body().contains("name=\"payload_json\""));
        assertTrue(request.body().contains("\"attachments\":[{\"id\":0,\"filename\":\"finding-7.png\"}]"));
        assertTrue(request.body().contains("name=\"files[0]\"; filename=\"finding-7.png\""));
    }

    @Test
    void messageWithoutImageIsSentAsJson() throws Exception {
        replies.add(new Reply(200, "{\"id\":\"112\"}"));

        assertEquals(Optional.of("112"), webhook.send(payload(), Optional.empty()).get(10, TimeUnit.SECONDS));
        assertEquals("application/json", received.getFirst().contentType());
        assertEquals(payload().toString(), received.getFirst().body());
    }

    @Test
    void rateLimitIsWaitedOutAndRetried() throws Exception {
        replies.add(new Reply(429, "{\"message\":\"You are being rate limited.\",\"retry_after\":0.05}"));
        replies.add(new Reply(200, "{\"id\":\"113\"}"));

        assertEquals(Optional.of("113"), webhook.send(payload(), Optional.empty()).get(10, TimeUnit.SECONDS));
        assertEquals(2, received.size());
    }

    @Test
    void clientErrorIsDroppedWithoutRetry() throws Exception {
        replies.add(new Reply(400, "{\"message\":\"Invalid Form Body\"}"));

        assertTrue(webhook.send(payload(), Optional.empty()).get(10, TimeUnit.SECONDS).isEmpty());
        assertEquals(1, received.size());
    }

    @Test
    void serverErrorsAreRetriedUpToTheLimit() throws Exception {
        for (int i = 0; i < DiscordWebhook.ATTEMPTS; i++) {
            replies.add(new Reply(502, ""));
        }

        assertTrue(webhook.send(payload(), Optional.empty()).get(30, TimeUnit.SECONDS).isEmpty());
        assertEquals(DiscordWebhook.ATTEMPTS, received.size());
    }

    @Test
    void editPatchesTheStoredMessage() throws Exception {
        replies.add(new Reply(200, "{\"id\":\"111\"}"));

        assertTrue(webhook.edit("111", payload()).get(10, TimeUnit.SECONDS));
        Received request = received.getFirst();
        assertEquals("PATCH", request.method());
        assertEquals("/api/webhooks/1/token/messages/111", request.path());
        assertEquals("thread_id=5&with_components=true", request.query());
    }

    @Test
    void failedEditReportsFalse() throws Exception {
        replies.add(new Reply(404, "{\"message\":\"Unknown Message\"}"));

        assertFalse(webhook.edit("111", payload()).get(10, TimeUnit.SECONDS));
    }

    @Test
    void endpointKeepsTheWebhookQuery() {
        assertEquals(URI.create("https://discord.com/api/webhooks/1/t/messages/9?a=b&c=d"),
                DiscordWebhook.endpoint(URI.create("https://discord.com/api/webhooks/1/t/?a=b"), "/messages/9", "c=d"));
        assertEquals(URI.create("https://discord.com/api/webhooks/1/t?c=d"),
                DiscordWebhook.endpoint(URI.create("https://discord.com/api/webhooks/1/t"), "", "c=d"));
    }

    private static JsonObject payload() {
        JsonObject payload = new JsonObject();
        payload.addProperty("content", "hello");
        return payload;
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
        received.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                exchange.getRequestURI().getRawQuery(), exchange.getRequestHeaders().getFirst("Content-Type"), body));
        Reply reply;
        synchronized (replies) {
            reply = replies.isEmpty() ? new Reply(500, "") : replies.removeFirst();
        }
        byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(reply.status(), bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }
}
