package de.kylekreuter.vistructum.ui.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

record Reply(int status, String contentType, byte[] body, List<Header> headers, Optional<Stored> stored) {

    static final String JSON = "application/json; charset=utf-8";
    static final String PNG = "image/png";
    static final String TEXT = "text/plain; charset=utf-8";
    static final String NO_STORE = "no-store";
    static final String NO_CACHE = "no-cache";
    static final String IMMUTABLE = "public, max-age=31536000, immutable";

    private static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();

    Reply {
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(body, "body");
        headers = List.copyOf(headers);
        Objects.requireNonNull(stored, "stored");
    }

    Reply(int status, String contentType, byte[] body, List<Header> headers) {
        this(status, contentType, body, headers, Optional.empty());
    }

    static Reply file(String contentType, Path path, boolean gzipped) {
        return new Reply(200, contentType, new byte[0], List.of(new Header("Cache-Control", IMMUTABLE)),
                Optional.of(new Stored(path, gzipped)));
    }

    static Reply json(JsonElement json) {
        return json(200, json);
    }

    static Reply json(int status, JsonElement json) {
        return new Reply(status, JSON, GSON.toJson(json).getBytes(StandardCharsets.UTF_8),
                List.of(new Header("Cache-Control", NO_STORE)));
    }

    static Reply error(int status, String code) {
        JsonObject body = new JsonObject();
        body.addProperty("error", code);
        return json(status, body);
    }

    static Reply png(byte[] png) {
        return new Reply(200, PNG, png, List.of(new Header("Cache-Control", "private, max-age=3600")));
    }

    static Reply text(int status, String text) {
        return new Reply(status, TEXT, text.getBytes(StandardCharsets.UTF_8),
                List.of(new Header("Cache-Control", NO_STORE)));
    }

    static Reply noContent() {
        return new Reply(204, TEXT, new byte[0], List.of(new Header("Cache-Control", NO_STORE)));
    }

    static Reply redirect(String location) {
        return new Reply(302, TEXT, new byte[0], List.of(new Header("Location", location),
                new Header("Cache-Control", NO_STORE), new Header("Referrer-Policy", "no-referrer")));
    }

    Reply with(String name, String value) {
        List<Header> more = new ArrayList<>(headers.size() + 1);
        headers.stream().filter(header -> !header.name().equalsIgnoreCase(name) || name.equalsIgnoreCase("Set-Cookie"))
                .forEach(more::add);
        more.add(new Header(name, value));
        return new Reply(status, contentType, body, more, stored);
    }

    boolean compressible() {
        return contentType.startsWith("application/json") || contentType.startsWith("text/")
                || contentType.startsWith("application/javascript") || contentType.startsWith("image/svg");
    }

    record Stored(Path path, boolean gzipped) {

        Stored {
            Objects.requireNonNull(path, "path");
        }
    }

    record Header(String name, String value) {

        Header {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
        }
    }
}
