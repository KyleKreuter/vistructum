package de.kylekreuter.vistructum.ui.web;

import io.javalin.http.Context;
import io.javalin.http.HandlerType;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Optional;

public final class Requests {

    public static final String SESSION_COOKIE = "vistructum_session";
    public static final String CSRF_COOKIE = "vistructum_csrf";
    public static final String CSRF_HEADER = "X-Vistructum-Csrf";
    public static final int MAX_BODY = 16 * 1024;

    private Requests() {
    }

    public static boolean reading(Context ctx) {
        return ctx.method().equals(HandlerType.GET) || ctx.method().equals(HandlerType.HEAD);
    }

    public static Optional<String> param(Context ctx, String name) {
        return Optional.ofNullable(ctx.queryParam(name)).filter(value -> !value.isEmpty());
    }

    public static Optional<String> cookie(Context ctx, String name) {
        Enumeration<String> headers = ctx.req().getHeaders("Cookie");
        if (headers == null) {
            return Optional.empty();
        }
        for (String header : Collections.list(headers)) {
            for (String pair : header.split(";")) {
                int split = pair.indexOf('=');
                if (split > 0 && pair.substring(0, split).trim().equals(name)) {
                    return Optional.of(pair.substring(split + 1).trim()).filter(value -> !value.isEmpty());
                }
            }
        }
        return Optional.empty();
    }
}
