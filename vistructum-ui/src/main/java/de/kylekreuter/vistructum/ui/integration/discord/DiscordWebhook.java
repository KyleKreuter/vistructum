package de.kylekreuter.vistructum.ui.integration.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.logging.Logger;

final class DiscordWebhook implements AutoCloseable {

    record Image(String filename, byte[] png) {

        Image {
            Objects.requireNonNull(filename, "filename");
            Objects.requireNonNull(png, "png");
        }
    }

    static final int ATTEMPTS = 3;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration MAX_RETRY_AFTER = Duration.ofMinutes(1);
    private static final Duration DEFAULT_RETRY_AFTER = Duration.ofSeconds(1);
    private static final int MAX_ERROR_LENGTH = 200;
    private static final String JSON = "application/json";

    private final URI webhook;
    private final Logger logger;
    private final HttpClient http;
    private final ExecutorService queue;

    DiscordWebhook(URI webhook, Logger logger) {
        this.webhook = Objects.requireNonNull(webhook, "webhook");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        this.queue = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("vistructum-discord")
                .factory());
    }

    CompletableFuture<Optional<String>> send(JsonObject payload, Optional<Image> image) {
        URI endpoint = endpoint(webhook, "", "wait=true&with_components=true");
        return CompletableFuture.supplyAsync(() -> deliver("a message", () -> image
                .map(picture -> multipart(endpoint, payload, picture))
                .orElseGet(() -> json(endpoint, "POST", payload)))
                .flatMap(DiscordWebhook::messageId), queue);
    }

    CompletableFuture<Boolean> edit(String messageId, JsonObject payload) {
        URI endpoint = endpoint(webhook, "/messages/" + messageId, "with_components=true");
        return CompletableFuture.supplyAsync(() -> deliver("an edit of message " + messageId,
                () -> json(endpoint, "PATCH", payload)).isPresent(), queue);
    }

    @Override
    public void close() {
        queue.shutdownNow();
        http.close();
    }

    static URI endpoint(URI webhook, String suffix, String query) {
        String path = webhook.getRawPath().replaceAll("/+$", "") + suffix;
        String existing = webhook.getRawQuery();
        String combined = existing == null || existing.isEmpty() ? query : existing + "&" + query;
        return URI.create(webhook.getScheme() + "://" + webhook.getRawAuthority() + path + "?" + combined);
    }

    static Duration retryAfter(HttpResponse<String> response) {
        Duration wait = retryAfterBody(response.body())
                .or(() -> response.headers().firstValue("Retry-After").flatMap(DiscordWebhook::seconds))
                .orElse(DEFAULT_RETRY_AFTER);
        return wait.compareTo(MAX_RETRY_AFTER) > 0 ? MAX_RETRY_AFTER : wait;
    }

    private Optional<HttpResponse<String>> deliver(String what, Supplier<HttpRequest> request) {
        String failure = "";
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            Duration pause = Duration.ofSeconds(attempt);
            try {
                HttpResponse<String> response = http.send(request.get(), HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    return Optional.of(response);
                }
                failure = "HTTP " + status + error(response.body());
                if (status == 429) {
                    pause = retryAfter(response);
                } else if (status < 500) {
                    break;
                }
            } catch (IOException e) {
                failure = e.getClass().getSimpleName();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
            if (attempt < ATTEMPTS && !pause(pause)) {
                return Optional.empty();
            }
        }
        logger.warning(what + " cannot be delivered to Discord and is dropped: " + failure);
        return Optional.empty();
    }

    private static boolean pause(Duration duration) {
        try {
            Thread.sleep(duration);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static HttpRequest json(URI endpoint, String method, JsonObject payload) {
        return HttpRequest.newBuilder(endpoint).timeout(TIMEOUT).header("Content-Type", JSON)
                .method(method, HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();
    }

    private static HttpRequest multipart(URI endpoint, JsonObject payload, Image image) {
        JsonObject withAttachment = payload.deepCopy();
        JsonObject attachment = new JsonObject();
        attachment.addProperty("id", 0);
        attachment.addProperty("filename", image.filename());
        JsonArray attachments = new JsonArray();
        attachments.add(attachment);
        withAttachment.add("attachments", attachments);
        String boundary = "vistructum-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        write(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"payload_json\"\r\n"
                + "Content-Type: " + JSON + "\r\n\r\n" + withAttachment + "\r\n");
        write(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"files[0]\"; filename=\""
                + image.filename() + "\"\r\nContent-Type: image/png\r\n\r\n");
        body.writeBytes(image.png());
        write(body, "\r\n--" + boundary + "--\r\n");
        return HttpRequest.newBuilder(endpoint).timeout(TIMEOUT)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
    }

    private static void write(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(StandardCharsets.UTF_8));
    }

    private static Optional<String> messageId(HttpResponse<String> response) {
        return object(response.body()).map(body -> body.get("id")).filter(JsonElement::isJsonPrimitive)
                .map(JsonElement::getAsString);
    }

    private static Optional<Duration> retryAfterBody(String body) {
        return object(body).map(json -> json.get("retry_after")).filter(JsonElement::isJsonPrimitive)
                .filter(value -> value.getAsJsonPrimitive().isNumber())
                .map(value -> Duration.ofMillis(Math.round(value.getAsDouble() * 1000)));
    }

    private static Optional<Duration> seconds(String header) {
        try {
            return Optional.of(Duration.ofMillis(Math.round(Double.parseDouble(header.trim()) * 1000)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static String error(String body) {
        return object(body).map(json -> json.get("message")).filter(JsonElement::isJsonPrimitive)
                .map(message -> ": " + abbreviated(message.getAsString())).orElse("");
    }

    private static String abbreviated(String text) {
        return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH);
    }

    private static Optional<JsonObject> object(String body) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonElement json = JsonParser.parseString(body);
            return json.isJsonObject() ? Optional.of(json.getAsJsonObject()) : Optional.empty();
        } catch (JsonParseException | IllegalStateException | NumberFormatException e) {
            return Optional.empty();
        }
    }
}
