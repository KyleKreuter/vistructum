package de.kylekreuter.vistructum.ui.web;

import de.kylekreuter.vistructum.ui.web.view.ErrorView;
import io.javalin.http.Context;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

public final class Responses {

    public static final String JSON = "application/json; charset=utf-8";
    public static final String PNG = "image/png";
    public static final String TEXT = "text/plain; charset=utf-8";
    public static final String ZIP = "application/zip";
    public static final String NO_STORE = "no-store";
    public static final String NO_CACHE = "no-cache";
    public static final String IMMUTABLE = "public, max-age=31536000, immutable";
    public static final String CACHE_CONTROL = "Cache-Control";

    private static final String PRIVATE_HOUR = "private, max-age=3600";
    private static final String CONTENT_ENCODING = "Content-Encoding";
    private static final String GZIP = "gzip";

    private Responses() {
    }

    public static void json(Context ctx, Object view) {
        json(ctx, 200, view);
    }

    public static void json(Context ctx, int status, Object view) {
        String json = ctx.jsonMapper().toJsonString(view, view.getClass());
        ctx.status(status).contentType(JSON).header(CACHE_CONTROL, NO_STORE)
                .result(json.getBytes(StandardCharsets.UTF_8));
    }

    public static void error(Context ctx, int status, String code) {
        json(ctx, status, new ErrorView(code));
    }

    public static void png(Context ctx, byte[] png) {
        ctx.status(200).contentType(PNG).header(CACHE_CONTROL, PRIVATE_HOUR).result(png);
    }

    public static void text(Context ctx, int status, String text) {
        ctx.status(status).contentType(TEXT).header(CACHE_CONTROL, NO_STORE)
                .result(text.getBytes(StandardCharsets.UTF_8));
    }

    public static void noContent(Context ctx) {
        ctx.status(204).contentType(TEXT).header(CACHE_CONTROL, NO_STORE);
    }

    public static void redirect(Context ctx, String location) {
        ctx.status(302).contentType(TEXT).header("Location", location).header(CACHE_CONTROL, NO_STORE)
                .header("Referrer-Policy", "no-referrer");
    }

    public static void methodNotAllowed(Context ctx, String allow) {
        text(ctx, 405, "Method not allowed");
        ctx.header("Allow", allow);
    }

    public static void notFound(Context ctx) {
        text(ctx, 404, "Not found");
    }

    public static void cookie(Context ctx, String cookie) {
        ctx.res().addHeader("Set-Cookie", cookie);
    }

    public static void file(Context ctx, String contentType, Path file, boolean gzipped) {
        ctx.status(200).contentType(contentType).header(CACHE_CONTROL, IMMUTABLE);
        boolean inflate = gzipped && !acceptsGzip(ctx);
        try {
            if (gzipped && !inflate) {
                ctx.header(CONTENT_ENCODING, GZIP);
            }
            if (!inflate) {
                ctx.res().setContentLengthLong(Files.size(file));
            }
            InputStream in = Files.newInputStream(file);
            ctx.result(inflate ? new GZIPInputStream(in) : in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static boolean acceptsGzip(Context ctx) {
        Enumeration<String> headers = ctx.req().getHeaders("Accept-Encoding");
        String encodings = headers == null ? "" : String.join(",", Collections.list(headers));
        return encodings.toLowerCase(Locale.ROOT).contains(GZIP);
    }
}
