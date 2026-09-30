package de.kylekreuter.vistructum.ui.web;

import de.kylekreuter.vistructum.api.Vistructum;
import de.kylekreuter.vistructum.ui.gui.ResourcePack;
import de.kylekreuter.vistructum.ui.integration.punishment.PunishmentLog;
import de.kylekreuter.vistructum.ui.web.assets.GameAssets;
import de.kylekreuter.vistructum.ui.web.controller.ApiFallback;
import de.kylekreuter.vistructum.ui.web.controller.AssetController;
import de.kylekreuter.vistructum.ui.web.controller.AuthController;
import de.kylekreuter.vistructum.ui.web.controller.BlockLogController;
import de.kylekreuter.vistructum.ui.web.controller.FindingController;
import de.kylekreuter.vistructum.ui.web.controller.PackController;
import de.kylekreuter.vistructum.ui.web.controller.PlayerController;
import de.kylekreuter.vistructum.ui.web.controller.PublicEvidenceController;
import de.kylekreuter.vistructum.ui.web.controller.ShareController;
import de.kylekreuter.vistructum.ui.web.controller.SpaController;
import de.kylekreuter.vistructum.ui.web.controller.StatusController;
import de.kylekreuter.vistructum.ui.web.error.ErrorHandlers;
import de.kylekreuter.vistructum.ui.web.filter.BodySizeFilter;
import de.kylekreuter.vistructum.ui.web.filter.CsrfFilter;
import de.kylekreuter.vistructum.ui.web.filter.HeaderFilter;
import de.kylekreuter.vistructum.ui.web.filter.SessionFilter;
import de.kylekreuter.vistructum.ui.web.view.JsonViews;
import io.javalin.Javalin;
import io.javalin.compression.CompressionStrategy;
import io.javalin.compression.Gzip;
import io.javalin.config.JavalinConfig;
import io.javalin.json.JavalinGson;
import io.javalin.util.JavalinLogger;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

public final class WebApplication implements AutoCloseable {

    private static final int MAX_THREADS = 16;
    private static final int MIN_THREADS = 2;
    private static final int GZIP_THRESHOLD = 512;
    private static final List<String> UNCOMPRESSED = List.of("image/", "font/", "application/wasm",
            "application/octet-stream", "application/zip");

    private final Javalin javalin;

    private WebApplication(Javalin javalin) {
        this.javalin = javalin;
    }

    public static WebApplication start(InetSocketAddress address, ResourcePack pack, Optional<WebApp> app,
                                       Logger logger) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(app, "app");
        Objects.requireNonNull(logger, "logger");
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(WebApplication.class.getClassLoader());
        try {
            JavalinLogger.startupInfo = false;
            QueuedThreadPool pool = new QueuedThreadPool(MAX_THREADS, MIN_THREADS);
            pool.setName("vistructum-web");
            pool.setDaemon(true);
            Javalin javalin = Javalin.create(config -> configure(config, address, pool, pack, app, logger));
            javalin.start();
            return new WebApplication(javalin);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    public int port() {
        return javalin.port();
    }

    @Override
    public void close() {
        javalin.stop();
    }

    private static void configure(JavalinConfig config, InetSocketAddress address, QueuedThreadPool pool,
                                  ResourcePack pack, Optional<WebApp> app, Logger logger) {
        config.startup.showJavalinBanner = false;
        config.startup.showOldJavalinVersionWarning = false;
        config.startup.startupWatcherEnabled = false;
        config.jetty.host = address.getAddress() == null || address.getAddress().isAnyLocalAddress() ? null
                : address.getHostString();
        config.jetty.port = address.getPort();
        config.jetty.threadPool = pool;
        config.router.ignoreTrailingSlashes = false;
        config.http.compressionStrategy = compression();
        config.jsonMapper(new JavalinGson(JsonViews.gson(), false));
        new PackController(pack).register(config);
        app.ifPresent(web -> review(config, web, pool));
        new ErrorHandlers(logger).register(config);
    }

    private static void review(JavalinConfig config, WebApp web, Executor worker) {
        Handoff handoff = new Handoff(worker, web.mainThread());
        PlayerNames names = new PlayerNames(web.game(), handoff);
        Vistructum vistructum = web.vistructum();
        new BodySizeFilter().register(config, WebSettings.ROOT, WebSettings.HOME + "*");
        new HeaderFilter().register(config, WebSettings.ROOT, WebSettings.HOME + "*");
        new SessionFilter(vistructum.web(), handoff).register(config, WebSettings.API + "*");
        new CsrfFilter().register(config, WebSettings.API + "*");
        new AuthController(vistructum.web(), vistructum.blockLog(), web.punishments(), web.settings(),
                web.game(), handoff).register(config);
        new StatusController(vistructum, web.assets(), handoff, web.clock()).register(config);
        new ShareController(vistructum, web.settings(), web.game(), handoff).register(config);
        FindingController findings = new FindingController(vistructum, web.settings(), web.game(), names, handoff);
        new BlockLogController(vistructum, web.game(), handoff, findings).register(config);
        findings.register(config);
        new PlayerController(vistructum, names, handoff, web.punishments()).register(config);
        new PublicEvidenceController(vistructum, names, handoff).register(config);
        new AssetController(web.assets(), web.palette()).register(config);
        new ApiFallback().register(config);
        new SpaController(web.files()).register(config);
    }

    private static CompressionStrategy compression() {
        CompressionStrategy strategy = new CompressionStrategy(null, new Gzip());
        strategy.setDefaultMinSizeForCompression(GZIP_THRESHOLD);
        strategy.setExcludedMimeTypes(UNCOMPRESSED);
        return strategy;
    }

    public record WebApp(Vistructum vistructum, WebSettings settings, GameServer game, Map<String, Integer> palette,
                         GameAssets assets, Optional<PunishmentLog> punishments, Executor mainThread,
                         ClassLoader files, Clock clock) {

        public WebApp {
            Objects.requireNonNull(vistructum, "vistructum");
            Objects.requireNonNull(settings, "settings");
            Objects.requireNonNull(game, "game");
            palette = Map.copyOf(palette);
            Objects.requireNonNull(assets, "assets");
            Objects.requireNonNull(punishments, "punishments");
            Objects.requireNonNull(mainThread, "mainThread");
            Objects.requireNonNull(files, "files");
            Objects.requireNonNull(clock, "clock");
        }
    }
}
