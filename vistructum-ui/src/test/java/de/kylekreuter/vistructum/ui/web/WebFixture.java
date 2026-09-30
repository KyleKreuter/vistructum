package de.kylekreuter.vistructum.ui.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.kylekreuter.vistructum.api.BlockAction;
import de.kylekreuter.vistructum.api.BlockBox;
import de.kylekreuter.vistructum.api.BlockEvent;
import de.kylekreuter.vistructum.api.BlockVolume;
import de.kylekreuter.vistructum.api.Evidence;
import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.MotionFrame;
import de.kylekreuter.vistructum.api.PlayerSkin;
import de.kylekreuter.vistructum.api.Recording;
import de.kylekreuter.vistructum.api.Review;
import de.kylekreuter.vistructum.api.Source;
import de.kylekreuter.vistructum.api.Verdict;
import de.kylekreuter.vistructum.ui.gui.ResourcePack;
import de.kylekreuter.vistructum.ui.web.assets.GameAssets;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class WebFixture implements AutoCloseable {

    public static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");
    public static final UUID STAFF = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    public static final UUID BUILDER = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    public static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    public static final String CSRF = "csrf-token";
    public static final String INDEX = "<!doctype html><title>review</title>";
    public static final String SCRIPT = "console.log('review');";

    public final FakeVistructum vistructum = new FakeVistructum(NOW);
    public final Set<UUID> sharers = ConcurrentHashMap.newKeySet();
    public final Set<UUID> rollbackers = ConcurrentHashMap.newKeySet();

    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final Path root;
    private URLClassLoader files;
    private WebApplication application;

    private WebFixture(Path root) {
        this.root = root;
    }

    public static WebFixture withData(Path root) throws IOException {
        WebFixture fixture = new WebFixture(root);
        fixture.vistructum.findings.put(7L, finding(7, Optional.of(new Review(Verdict.CONFIRMED, "Staff", NOW))));
        fixture.vistructum.findings.put(8L, finding(8, Optional.empty()));
        fixture.vistructum.evidence.put(7L, evidence());
        fixture.vistructum.skins.put(BUILDER, new PlayerSkin(BUILDER, Optional.of("Builder"), new byte[]{1, 2, 3},
                true));
        fixture.vistructum.skins.put(STRANGER, new PlayerSkin(STRANGER, Optional.of("Stranger"), new byte[]{4},
                false));
        Files.createDirectories(root.resolve("web/assets"));
        Files.writeString(root.resolve("web/index.html"), INDEX);
        Files.writeString(root.resolve("web/assets/app-1234.js"), SCRIPT);
        return fixture.start(new URLClassLoader(new URL[]{root.toUri().toURL()}, null),
                new GameAssets(root.resolve("assets"), "1.21.4", false), true);
    }

    public static WebFixture serving(Path root, GameAssets assets) {
        return new WebFixture(root).start(new URLClassLoader(new URL[0], null), assets, true);
    }

    public WebFixture withoutApp() throws IOException {
        close();
        return start(new URLClassLoader(new URL[0], null), new GameAssets(root.resolve("assets"), "1.21.4", false),
                false);
    }

    public WebFixture withoutBuiltFiles() throws IOException {
        close();
        return start(new URLClassLoader(new URL[0], null), new GameAssets(root.resolve("assets"), "1.21.4", false),
                true);
    }

    private WebFixture start(URLClassLoader loader, GameAssets assets, boolean enabled) {
        files = loader;
        Optional<WebApplication.WebApp> app = enabled ? Optional.of(new WebApplication.WebApp(vistructum,
                new WebSettings(true, "127.0.0.1", "https://review.example/"), game(),
                Map.of("minecraft:stone", 0x707070), assets, Runnable::run, loader, Clock.fixed(NOW, ZoneOffset.UTC)))
                : Optional.empty();
        application = WebApplication.start(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                ResourcePack.build(), app, Logger.getLogger("test"));
        return this;
    }

    public String session() {
        String login = vistructum.issueLogin(STAFF, "Staff").join();
        return vistructum.redeemLogin(login).join().orElseThrow().token();
    }

    public static Map<String, String> cookie(String session) {
        return Map.of("Cookie", "vistructum_session=" + session);
    }

    public static Map<String, String> authorized(String session) {
        return Map.of("Cookie", "vistructum_session=" + session + "; vistructum_csrf=" + CSRF,
                "X-Vistructum-Csrf", CSRF);
    }

    public URI uri(String path) {
        return URI.create("http://127.0.0.1:" + application.port() + path);
    }

    public HttpResponse<String> get(String path, Map<String, String> headers) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).GET(), headers);
    }

    public HttpResponse<String> head(String path, Map<String, String> headers) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).method("HEAD", HttpRequest.BodyPublishers.noBody()), headers);
    }

    public HttpResponse<String> post(String path, String body, Map<String, String> headers) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).POST(HttpRequest.BodyPublishers.ofString(body)), headers);
    }

    public HttpResponse<String> method(String method, String path, Map<String, String> headers) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).method(method, HttpRequest.BodyPublishers.noBody()), headers);
    }

    public HttpResponse<String> send(HttpRequest.Builder request, Map<String, String> headers) throws Exception {
        headers.forEach(request::header);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<byte[]> bytes(String path, Map<String, String> headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).GET();
        headers.forEach(request::header);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    public static JsonObject json(HttpResponse<String> response) {
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    public static Finding finding(long id, Optional<Review> review) {
        return new Finding(id, Source.FULLSCAN, "world", new BlockBox(100, 60, 200, 110, 70, 210), 0.9, 2,
                Set.of(BUILDER), "d", "bf-scan-3", NOW.minusSeconds(3600), review);
    }

    private static Evidence evidence() {
        BlockVolume before = new BlockVolume(100, 60, 200, 2, 1, 1, List.of("minecraft:air", "minecraft:stone"),
                new int[]{0, 1});
        BlockEvent change = new BlockEvent(NOW, BUILDER, "Builder", BlockAction.PLACE, 101, 62, 203,
                "minecraft:stone");
        MotionFrame frame = new MotionFrame(NOW.toEpochMilli(), 100.5, 59.0, 204.25, 90f, 10f, MotionFrame.ON_GROUND,
                "minecraft:stone");
        return new Evidence(7, before, List.of(change), List.of(new Recording(BUILDER, "Builder", List.of(frame))));
    }

    private GameServer game() {
        return new GameServer() {
            @Override
            public Optional<String> playerName(UUID player) {
                return player.equals(BUILDER) ? Optional.of("Builder") : Optional.empty();
            }

            @Override
            public boolean canShare(UUID player) {
                return sharers.contains(player);
            }

            @Override
            public boolean canRollback(UUID player) {
                return rollbackers.contains(player);
            }

            @Override
            public Optional<String> dimension(String world) {
                return world.equals("world") ? Optional.empty() : Optional.of("minecraft:" + world);
            }
        };
    }

    @Override
    public void close() throws IOException {
        application.close();
        files.close();
    }
}
