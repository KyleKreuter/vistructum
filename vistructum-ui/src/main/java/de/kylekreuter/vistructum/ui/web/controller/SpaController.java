package de.kylekreuter.vistructum.ui.web.controller;

import de.kylekreuter.vistructum.ui.web.Responses;
import de.kylekreuter.vistructum.ui.web.WebSettings;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class SpaController {

    private static final String FILES = "web/";
    private static final String INDEX = "index.html";
    private static final String NOT_BUILT = "The web app is not part of this build of vistructum-ui.";
    private static final String OCTET_STREAM = "application/octet-stream";
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

    private final ClassLoader files;

    public SpaController(ClassLoader files) {
        this.files = Objects.requireNonNull(files, "files");
    }

    public void register(JavalinConfig config) {
        Routes.any(config, WebSettings.ROOT, ctx -> Responses.redirect(ctx, WebSettings.HOME));
        Routes.read(config, WebSettings.HOME + "assets/<file>", this::asset);
        Routes.read(config, WebSettings.HOME + "*", this::index);
        Routes.writing(config, WebSettings.HOME + "*", ctx -> Responses.methodNotAllowed(ctx, "GET, HEAD"));
    }

    private void asset(Context ctx) {
        String relative = ctx.path().substring(WebSettings.HOME.length());
        Optional<byte[]> asset = safe(relative) ? read(relative) : Optional.empty();
        if (asset.isEmpty()) {
            Responses.notFound(ctx);
            return;
        }
        ctx.status(200).contentType(type(relative)).header(Responses.CACHE_CONTROL, Responses.IMMUTABLE)
                .result(asset.get());
    }

    private void index(Context ctx) {
        Optional<byte[]> index = read(INDEX);
        if (index.isEmpty()) {
            Responses.text(ctx, 503, NOT_BUILT);
            return;
        }
        ctx.status(200).contentType(type(INDEX)).header(Responses.CACHE_CONTROL, Responses.NO_CACHE)
                .result(index.get());
    }

    private Optional<byte[]> read(String relative) {
        try (InputStream in = files.getResourceAsStream(FILES + relative)) {
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
        return TYPES.getOrDefault(extension, OCTET_STREAM);
    }
}
