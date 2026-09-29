package de.kylekreuter.vistructum.ui.web;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

final class ReviewHandler implements HttpHandler {

    private static final int GZIP_THRESHOLD = 512;

    private final ReviewApi api;
    private final StaticFiles files;
    private final Executor worker;
    private final Logger logger;

    ReviewHandler(ReviewApi api, StaticFiles files, Executor worker, Logger logger) {
        this.api = Objects.requireNonNull(api, "api");
        this.files = Objects.requireNonNull(files, "files");
        this.worker = Objects.requireNonNull(worker, "worker");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void handle(HttpExchange exchange) {
        Request request;
        try {
            request = Request.read(exchange);
        } catch (IOException e) {
            write(exchange, Reply.error(400, "bad_request"), false, false);
            return;
        }
        CompletableFuture<Reply> reply;
        try {
            reply = dispatch(request);
        } catch (RuntimeException e) {
            reply = CompletableFuture.failedFuture(e);
        }
        boolean head = request.method().equals("HEAD");
        reply.exceptionally(error -> failure(request, error))
                .thenAcceptAsync(response -> write(exchange, response, request.acceptsGzip(), head), worker);
    }

    private CompletableFuture<Reply> dispatch(Request request) {
        String path = request.path();
        if (path.equals(WebSettings.ROOT)) {
            return CompletableFuture.completedFuture(Reply.redirect(WebSettings.HOME));
        }
        if (!path.startsWith(WebSettings.HOME)) {
            return CompletableFuture.completedFuture(Reply.text(404, "Not found"));
        }
        if (path.startsWith(ReviewApi.API)) {
            return api.route(request);
        }
        if (!request.get()) {
            return CompletableFuture.completedFuture(Reply.text(405, "Method not allowed").with("Allow", "GET, HEAD"));
        }
        if (path.equals(WebSettings.ROOT + "/login")) {
            return api.login(request);
        }
        if (path.startsWith(StaticFiles.ASSETS)) {
            return CompletableFuture.completedFuture(files.asset(path));
        }
        return CompletableFuture.completedFuture(files.index());
    }

    private Reply failure(Request request, Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        if (cause instanceof ApiError apiError) {
            return apiError.reply();
        }
        logger.log(Level.WARNING, request.method() + " " + request.path() + " failed", cause);
        return Reply.error(500, "internal");
    }

    private static void write(HttpExchange exchange, Reply reply, boolean acceptsGzip, boolean head) {
        try (exchange) {
            byte[] body = reply.body();
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", reply.contentType());
            headers.set("X-Content-Type-Options", "nosniff");
            reply.headers().forEach(header -> headers.add(header.name(), header.value()));
            if (reply.compressible()) {
                headers.add("Vary", "Accept-Encoding");
                if (acceptsGzip && body.length >= GZIP_THRESHOLD) {
                    body = gzip(body);
                    headers.set("Content-Encoding", "gzip");
                }
            }
            if (head || body.length == 0 || reply.status() == 204 || reply.status() == 304) {
                exchange.sendResponseHeaders(reply.status(), -1);
                return;
            }
            exchange.sendResponseHeaders(reply.status(), body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        } catch (IOException e) {
            exchange.close();
        }
    }

    private static byte[] gzip(byte[] body) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(body.length / 4 + 64);
        try (GZIPOutputStream out = new GZIPOutputStream(bytes)) {
            out.write(body);
        }
        return bytes.toByteArray();
    }
}
