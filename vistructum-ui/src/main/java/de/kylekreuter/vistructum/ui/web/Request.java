package de.kylekreuter.vistructum.ui.web;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

record Request(String method, String path, Map<String, String> query, Map<String, String> cookies,
               Optional<String> csrfHeader, boolean acceptsGzip, byte[] body) {

    static final int MAX_BODY = 16 * 1024;
    static final String CSRF_HEADER = "X-Vistructum-Csrf";

    Request {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(path, "path");
        query = Map.copyOf(query);
        cookies = Map.copyOf(cookies);
        Objects.requireNonNull(csrfHeader, "csrfHeader");
        Objects.requireNonNull(body, "body");
    }

    static Request read(HttpExchange exchange) throws IOException {
        String encodings = String.join(",", exchange.getRequestHeaders().getOrDefault("Accept-Encoding", List.of()));
        return new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                query(exchange.getRequestURI().getRawQuery()),
                cookies(exchange.getRequestHeaders().getOrDefault("Cookie", List.of())),
                Optional.ofNullable(exchange.getRequestHeaders().getFirst(CSRF_HEADER)),
                encodings.toLowerCase().contains("gzip"), body(exchange.getRequestBody()));
    }

    boolean get() {
        return method.equals("GET") || method.equals("HEAD");
    }

    Optional<String> param(String name) {
        return Optional.ofNullable(query.get(name)).filter(value -> !value.isEmpty());
    }

    Optional<String> cookie(String name) {
        return Optional.ofNullable(cookies.get(name)).filter(value -> !value.isEmpty());
    }

    private static byte[] body(InputStream in) throws IOException {
        try (in) {
            byte[] body = in.readNBytes(MAX_BODY + 1);
            if (body.length > MAX_BODY) {
                throw new IOException("request body exceeds " + MAX_BODY + " bytes");
            }
            return body;
        }
    }

    static Map<String, String> query(String raw) {
        Map<String, String> query = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return query;
        }
        for (String pair : raw.split("&")) {
            int split = pair.indexOf('=');
            String name = decode(split < 0 ? pair : pair.substring(0, split));
            String value = split < 0 ? "" : decode(pair.substring(split + 1));
            query.putIfAbsent(name, value);
        }
        return query;
    }

    private static Map<String, String> cookies(List<String> headers) {
        Map<String, String> cookies = new HashMap<>();
        for (String header : headers) {
            for (String pair : header.split(";")) {
                int split = pair.indexOf('=');
                if (split > 0) {
                    cookies.putIfAbsent(pair.substring(0, split).trim(), pair.substring(split + 1).trim());
                }
            }
        }
        return cookies;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
