package de.kylekreuter.vistructum.ui.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class StaticFiles {

    static final String ROOT = "web/";
    static final String ASSETS = WebSettings.ROOT + "/assets/";

    private static final String INDEX = "index.html";
    private static final String IMMUTABLE = "public, max-age=31536000, immutable";
    private static final String NO_CACHE = "no-cache";
    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("js", "application/javascript; charset=utf-8"),
            Map.entry("mjs", "application/javascript; charset=utf-8"),
            Map.entry("css", "text/css; charset=utf-8"),
            Map.entry("json", "application/json; charset=utf-8"),
            Map.entry("map", "application/json; charset=utf-8"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("webp", "image/webp"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("woff", "font/woff"),
            Map.entry("woff2", "font/woff2"),
            Map.entry("txt", "text/plain; charset=utf-8"),
            Map.entry("wasm", "application/wasm"));

    private final ClassLoader loader;

    StaticFiles(ClassLoader loader) {
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    Reply asset(String path) {
        String relative = path.substring(WebSettings.HOME.length());
        if (!safe(relative)) {
            return Reply.text(404, "Not found");
        }
        return read(relative)
                .map(bytes -> new Reply(200, type(relative), bytes, List.of(new Reply.Header("Cache-Control", IMMUTABLE))))
                .orElseGet(() -> Reply.text(404, "Not found"));
    }

    Reply index() {
        return read(INDEX)
                .map(bytes -> new Reply(200, type(INDEX), bytes, List.of(new Reply.Header("Cache-Control", NO_CACHE))))
                .orElseGet(() -> Reply.text(503, "The web app is not part of this build of vistructum-ui."));
    }

    private Optional<byte[]> read(String relative) {
        try (InputStream in = loader.getResourceAsStream(ROOT + relative)) {
            return in == null ? Optional.empty() : Optional.of(in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean safe(String relative) {
        if (relative.isEmpty() || relative.endsWith("/") || relative.contains("\\")) {
            return false;
        }
        for (String segment : relative.split("/")) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return false;
            }
        }
        return true;
    }

    private static String type(String name) {
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return TYPES.getOrDefault(extension, "application/octet-stream");
    }
}
