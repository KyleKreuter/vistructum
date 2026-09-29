package de.kylekreuter.vistructum.ui.web;

import com.sun.net.httpserver.HttpServer;
import de.kylekreuter.vistructum.api.Vistructum;
import de.kylekreuter.vistructum.ui.gui.ResourcePack;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Logger;

public final class WebServer implements AutoCloseable {

    private static final int THREADS = 4;

    private final HttpServer server;
    private final ExecutorService pool;

    private WebServer(HttpServer server, ExecutorService pool) {
        this.server = server;
        this.pool = pool;
    }

    public static WebServer start(InetSocketAddress address, ResourcePack pack, Optional<WebApp> app)
            throws IOException {
        Objects.requireNonNull(pack, "pack");
        HttpServer server = HttpServer.create(address, 0);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS,
                Thread.ofPlatform().name("vistructum-web-", 0).daemon().factory());
        Executor worker = task -> {
            try {
                pool.execute(task);
            } catch (RejectedExecutionException closed) {
                task.run();
            }
        };
        server.setExecutor(pool);
        server.createContext("/pack.zip", exchange -> {
            try (exchange) {
                exchange.getResponseHeaders().set("Content-Type", "application/zip");
                exchange.sendResponseHeaders(200, pack.zip().length);
                try (OutputStream body = exchange.getResponseBody()) {
                    body.write(pack.zip());
                }
            }
        });
        app.ifPresent(web -> server.createContext(WebSettings.ROOT, new ReviewHandler(
                new ReviewApi(web.vistructum(), web.settings(), web.game(), web.palette(), web.assets(), web.mainThread(),
                        worker, web.clock()),
                new StaticFiles(web.files()), worker, web.logger())));
        server.start();
        return new WebServer(server, pool);
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
        pool.shutdownNow();
    }

    public record WebApp(Vistructum vistructum, WebSettings settings, GameServer game, Map<String, Integer> palette,
                         GameAssets assets, Executor mainThread, ClassLoader files, Clock clock, Logger logger) {

        public WebApp {
            Objects.requireNonNull(vistructum, "vistructum");
            Objects.requireNonNull(settings, "settings");
            Objects.requireNonNull(game, "game");
            palette = Map.copyOf(palette);
            Objects.requireNonNull(assets, "assets");
            Objects.requireNonNull(mainThread, "mainThread");
            Objects.requireNonNull(files, "files");
            Objects.requireNonNull(clock, "clock");
            Objects.requireNonNull(logger, "logger");
        }
    }
}
