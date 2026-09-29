package de.kylekreuter.vistructum.ui.web.filter;

import de.kylekreuter.vistructum.ui.web.Requests;
import de.kylekreuter.vistructum.ui.web.error.ApiError;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

public final class CsrfFilter {

    public void register(JavalinConfig config, String path) {
        config.routes.beforeMatched(path, CsrfFilter::check);
    }

    private static void check(Context ctx) {
        if (Requests.reading(ctx)) {
            return;
        }
        Optional<String> cookie = Requests.cookie(ctx, Requests.CSRF_COOKIE);
        Optional<String> header = Optional.ofNullable(ctx.header(Requests.CSRF_HEADER)).filter(value -> !value.isEmpty());
        if (cookie.isEmpty() || header.isEmpty() || !MessageDigest.isEqual(
                cookie.get().getBytes(StandardCharsets.UTF_8), header.get().getBytes(StandardCharsets.UTF_8))) {
            throw ApiError.csrf();
        }
    }
}
