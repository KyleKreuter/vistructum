package de.kylekreuter.vistructum.ui.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.kylekreuter.vistructum.ui.gui.ResourcePack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameAssetsTest {

    private static final String VERSION = "1.21.4";
    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");
    private static final byte[] STONE = {(byte) 0x89, 'P', 'N', 'G', 1};
    private static final Logger LOGGER = Logger.getLogger("assets-test");

    @TempDir
    Path folder;

    private HttpServer mojang;
    private final AtomicInteger jarRequests = new AtomicInteger();
    private byte[] jar;
    private String advertisedSha1;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void startMojang() throws IOException {
        jar = clientJar();
        advertisedSha1 = sha1(jar);
        mojang = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        mojang.createContext("/mc/game/version_manifest_v2.json", exchange -> respond(exchange,
                ("{\"latest\":{},\"versions\":[{\"id\":\"1.21.3\",\"url\":\"/v1/packages/a/1.21.3.json\"},"
                        + "{\"id\":\"" + VERSION + "\",\"url\":\"" + base() + "/v1/packages/b/" + VERSION + ".json\"}]}")
                        .getBytes(StandardCharsets.UTF_8)));
        mojang.createContext("/v1/packages/b/" + VERSION + ".json", exchange -> respond(exchange,
                ("{\"id\":\"" + VERSION + "\",\"downloads\":{\"client\":{\"url\":\"" + base()
                        + "/v1/objects/client.jar\",\"sha1\":\"" + advertisedSha1 + "\",\"size\":" + jar.length + "}}}")
                        .getBytes(StandardCharsets.UTF_8)));
        mojang.createContext("/v1/objects/client.jar", exchange -> {
            jarRequests.incrementAndGet();
            respond(exchange, jar);
        });
        mojang.start();
    }

    @AfterEach
    void stopMojang() {
        mojang.stop(0);
    }

    @Test
    void theClientJarIsDownloadedAndOnlyTheWhitelistIsExtracted() throws Exception {
        GameAssets assets = assets(true);
        assertFalse(assets.available());

        assets.fetch(manifest(), LOGGER).get(10, TimeUnit.SECONDS);

        assertTrue(assets.available());
        assertEquals(Optional.of(VERSION), assets.cachedVersion());
        assertEquals(Set.of("blockstates/stone.json", "blockstates/oak_stairs.json", "models/block/stone.json",
                "models/block/oak_stairs.json", "models/item/stick.json", "textures/block/stone.png",
                "textures/block/water_still.png", "textures/block/water_still.png.mcmeta", "textures/item/stick.png",
                "textures/colormap/grass.png", "items/stick.json", GameAssets.MODELS, GameAssets.MARKER),
                files(folder.resolve("assets/" + VERSION)));
        assertEquals(Set.of(VERSION), files(folder.resolve("assets")).stream().map(file -> file.split("/")[0])
                .collect(Collectors.toSet()));
        assertEquals(advertisedSha1, Files.readString(folder.resolve("assets/" + VERSION + "/" + GameAssets.MARKER)));
        assertFalse(Files.exists(folder.resolve("evil.png")));
    }

    @Test
    void modelsJsonCollectsBlockstatesModelsItemsTexturesAndAnimations() throws Exception {
        GameAssets assets = assets(true);
        assets.fetch(manifest(), LOGGER).get(10, TimeUnit.SECONDS);

        JsonObject models;
        try (InputStream in = new GZIPInputStream(Files.newInputStream(assets.models(VERSION).orElseThrow()))) {
            models = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }

        assertEquals(VERSION, models.get("version").getAsString());
        assertEquals(Set.of("stone", "oak_stairs"), models.getAsJsonObject("blockstates").keySet());
        assertEquals("minecraft:block/stone", models.getAsJsonObject("blockstates").getAsJsonObject("stone")
                .getAsJsonObject("variants").getAsJsonObject("").get("model").getAsString());
        assertEquals(Set.of("block/stone", "block/oak_stairs", "item/stick"), models.getAsJsonObject("models").keySet());
        assertEquals(Set.of("stick"), models.getAsJsonObject("items").keySet());
        assertEquals(List.of("block/stone", "block/water_still", "colormap/grass", "item/stick"),
                models.getAsJsonArray("textures").asList().stream().map(texture -> texture.getAsString()).toList());
        JsonObject water = models.getAsJsonObject("animated").getAsJsonObject("block/water_still");
        assertEquals(Set.of("frametime", "interpolate"), water.keySet());
        assertEquals(2, water.get("frametime").getAsInt());
        assertEquals(Set.of("block/water_still"), models.getAsJsonObject("animated").keySet());
    }

    @Test
    void aSha1MismatchLeavesNothingBehind() throws Exception {
        advertisedSha1 = "0000000000000000000000000000000000000000";
        GameAssets assets = assets(true);

        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> assets.fetch(manifest(), LOGGER).get(10, TimeUnit.SECONDS));

        assertInstanceOf(IOException.class, failure.getCause());
        assertTrue(failure.getCause().getMessage().contains("SHA-1"), failure.getCause().getMessage());
        assertFalse(assets.available());
        assertEquals(Set.of(), files(folder.resolve("assets")));
        assertFalse(assets.downloading());
    }

    @Test
    void extractionReplacesLeftoversAtomically() throws Exception {
        Path staging = folder.resolve("assets/" + VERSION + ".tmp/textures/block");
        Files.createDirectories(staging);
        Files.write(staging.resolve("stale.png"), STONE);
        Path partial = folder.resolve("assets/" + VERSION + "/textures/block");
        Files.createDirectories(partial);
        Files.write(partial.resolve("stale.png"), STONE);
        Path older = folder.resolve("assets/1.21.3/" + GameAssets.MARKER);
        Files.createDirectories(older.getParent());
        Files.writeString(older, "old");
        GameAssets assets = assets(true);
        assertFalse(assets.available());

        assets.fetch(manifest(), LOGGER).get(10, TimeUnit.SECONDS);

        assertTrue(assets.available());
        assertFalse(Files.exists(folder.resolve("assets/" + VERSION + ".tmp")));
        assertFalse(Files.exists(folder.resolve("assets/" + VERSION + ".jar.part")));
        assertFalse(Files.exists(partial.resolve("stale.png")));
        assertTrue(Files.exists(older));
    }

    @Test
    void aCompleteCacheIsNotDownloadedAgain() throws Exception {
        assets(true).fetch(manifest(), LOGGER).get(10, TimeUnit.SECONDS);

        assets(true).fetch(manifest(), LOGGER).get(10, TimeUnit.SECONDS);

        assertEquals(1, jarRequests.get());
    }

    @Test
    void withoutTheOptInNothingIsDownloadedOrServed() throws Exception {
        assets(true).fetch(manifest(), LOGGER).get(10, TimeUnit.SECONDS);
        GameAssets disabled = assets(false);

        disabled.fetch(manifest(), LOGGER).get(10, TimeUnit.SECONDS);

        assertEquals(1, jarRequests.get());
        assertFalse(disabled.available());
        assertEquals(Optional.empty(), disabled.models(VERSION));
        assertEquals(Optional.empty(), disabled.texture(VERSION, "block/stone"));
    }

    @Test
    void anUnknownVersionFails() {
        GameAssets assets = new GameAssets(folder.resolve("assets"), "9.99", true);

        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> assets.fetch(manifest(), LOGGER).get(10, TimeUnit.SECONDS));

        assertTrue(failure.getCause().getMessage().contains("9.99"), failure.getCause().getMessage());
        assertFalse(assets.available());
    }

    @Test
    void theEndpointsServeTheCacheWithImmutableCaching() throws Exception {
        GameAssets assets = assets(true);
        assets.fetch(manifest(), LOGGER).get(10, TimeUnit.SECONDS);
        try (Served served = serve(assets)) {
            HttpResponse<String> state = served.get("/review/api/assets", Map.of());
            assertEquals(200, state.statusCode());
            assertEquals("no-cache", state.headers().firstValue("Cache-Control").orElseThrow());
            JsonObject body = JsonParser.parseString(state.body()).getAsJsonObject();
            assertTrue(body.get("available").getAsBoolean());
            assertEquals(VERSION, body.get("version").getAsString());
            assertFalse(body.get("downloading").getAsBoolean());

            HttpResponse<byte[]> gzipped = served.bytes("/review/api/assets/" + VERSION + "/models.json",
                    Map.of("Accept-Encoding", "gzip"));
            assertEquals(200, gzipped.statusCode());
            assertEquals("gzip", gzipped.headers().firstValue("Content-Encoding").orElseThrow());
            assertEquals(Reply.IMMUTABLE, gzipped.headers().firstValue("Cache-Control").orElseThrow());
            assertTrue(gzipped.headers().firstValue("Content-Type").orElseThrow().startsWith("application/json"));
            assertTrue(gzipped.headers().allValues("Vary").contains("Accept-Encoding"));
            String unpacked;
            try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(gzipped.body()))) {
                unpacked = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            HttpResponse<String> plain = served.get("/review/api/assets/" + VERSION + "/models.json", Map.of());
            assertEquals(200, plain.statusCode());
            assertTrue(plain.headers().firstValue("Content-Encoding").isEmpty());
            assertEquals(unpacked, plain.body());
            assertEquals(VERSION, JsonParser.parseString(plain.body()).getAsJsonObject().get("version").getAsString());

            HttpResponse<byte[]> stone = served.bytes("/review/api/assets/" + VERSION + "/textures/block/stone.png",
                    Map.of("Accept-Encoding", "gzip"));
            assertEquals(200, stone.statusCode());
            assertArrayEquals(STONE, stone.body());
            assertEquals("image/png", stone.headers().firstValue("Content-Type").orElseThrow());
            assertEquals(Reply.IMMUTABLE, stone.headers().firstValue("Cache-Control").orElseThrow());
            assertTrue(stone.headers().firstValue("Content-Encoding").isEmpty());
            assertEquals(200, served.get("/review/api/assets/" + VERSION + "/textures/colormap/grass.png", Map.of())
                    .statusCode());
            assertEquals(200, served.get("/review/api/assets/" + VERSION + "/textures/item/stick.png", Map.of())
                    .statusCode());
            HttpResponse<String> head = served.send(HttpRequest.newBuilder(served.uri("/review/api/assets/" + VERSION
                    + "/models.json")).method("HEAD", HttpRequest.BodyPublishers.noBody()), Map.of());
            assertEquals(200, head.statusCode());
        }
    }

    @Test
    void unknownVersionsIdsAndTraversalsAreNotFound() throws Exception {
        GameAssets assets = assets(true);
        assets.fetch(manifest(), LOGGER).get(10, TimeUnit.SECONDS);
        try (Served served = serve(assets)) {
            for (String path : List.of(
                    "/review/api/assets/1.21.3/models.json",
                    "/review/api/assets/1.21.3/textures/block/stone.png",
                    "/review/api/assets/" + VERSION,
                    "/review/api/assets/" + VERSION + "/models.json.gz",
                    "/review/api/assets/" + VERSION + "/complete",
                    "/review/api/assets/" + VERSION + "/textures/block/missing.png",
                    "/review/api/assets/" + VERSION + "/textures/block/stone",
                    "/review/api/assets/" + VERSION + "/textures/block/water_still.png.mcmeta",
                    "/review/api/assets/" + VERSION + "/textures/colormap/fire.png",
                    "/review/api/assets/" + VERSION + "/textures/entity/pig.png",
                    "/review/api/assets/" + VERSION + "/textures/stone.png",
                    "/review/api/assets/" + VERSION + "/textures/block/%2e%2e/%2e%2e/complete.png",
                    "/review/api/assets/" + VERSION + "/textures/block/%2e%2e/item/stick.png",
                    "/review/api/assets/" + VERSION + "/textures/block/..%2f..%2fcomplete.png",
                    "/review/api/assets/" + VERSION + "/textures//etc/passwd.png",
                    "/review/api/assets/" + VERSION + "/textures/block//stone.png",
                    "/review/api/assets/" + VERSION + "/textures/block/%2Fetc%2Fpasswd.png",
                    "/review/api/assets/%2e%2e/models.json",
                    "/review/api/assets/" + VERSION + "/blockstates/stone.json")) {
                HttpResponse<String> response = served.get(path, Map.of());
                assertEquals(404, response.statusCode(), path);
                assertEquals("not_found", JsonParser.parseString(response.body()).getAsJsonObject().get("error")
                        .getAsString(), path);
            }
            assertEquals(Optional.empty(), assets.texture(VERSION, "block/../item/stick"));
            assertEquals(Optional.empty(), assets.texture(VERSION, "/etc/passwd"));
            assertEquals(Optional.empty(), assets.texture(VERSION, "block/.hidden"));
        }
    }

    @Test
    void statusReportsTheTextures() throws Exception {
        GameAssets assets = assets(true);
        try (Served served = serve(assets)) {
            JsonObject before = served.status().getAsJsonObject("textures");
            assertTrue(before.get("enabled").getAsBoolean());
            assertFalse(before.get("available").getAsBoolean());
            assertTrue(before.get("version").isJsonNull());

            assets.fetch(manifest(), LOGGER).get(10, TimeUnit.SECONDS);

            JsonObject after = served.status().getAsJsonObject("textures");
            assertTrue(after.get("available").getAsBoolean());
            assertEquals(VERSION, after.get("version").getAsString());
        }
    }

    private GameAssets assets(boolean enabled) {
        return new GameAssets(folder.resolve("assets"), VERSION, enabled);
    }

    private URI manifest() {
        return URI.create(base() + "/mc/game/version_manifest_v2.json");
    }

    private String base() {
        return "http://127.0.0.1:" + mojang.getAddress().getPort();
    }

    private Served serve(GameAssets assets) throws IOException {
        FakeVistructum vistructum = new FakeVistructum(NOW);
        GameServer game = new GameServer() {
            @Override
            public Optional<String> playerName(UUID player) {
                return Optional.empty();
            }

            @Override
            public boolean canShare(UUID player) {
                return false;
            }

            @Override
            public Optional<String> dimension(String world) {
                return Optional.empty();
            }
        };
        URLClassLoader files = new URLClassLoader(new URL[0], null);
        WebServer server = WebServer.start(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                ResourcePack.build(), Optional.of(new WebServer.WebApp(vistructum,
                        new WebSettings(true, "127.0.0.1", "http://localhost"), game, Map.of(), assets, Runnable::run,
                        files, Clock.fixed(NOW, ZoneOffset.UTC), LOGGER)));
        return new Served(server, files, vistructum, client);
    }

    private static void respond(HttpExchange exchange, byte[] body) throws IOException {
        try (exchange) {
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }

    private static Set<String> files(Path root) throws IOException {
        if (!Files.exists(root)) {
            return Set.of();
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .collect(Collectors.toSet());
        }
    }

    private static byte[] clientJar() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("net/minecraft/client/main/Main.class", new byte[]{(byte) 0xCA, (byte) 0xFE});
        entries.put("assets/minecraft/sounds.json", utf8("{}"));
        entries.put("assets/minecraft/blockstates/stone.json",
                utf8("{\"variants\":{\"\":{\"model\":\"minecraft:block/stone\"}}}"));
        entries.put("assets/minecraft/blockstates/oak_stairs.json",
                utf8("{\"variants\":{\"facing=east\":{\"model\":\"minecraft:block/oak_stairs\"}}}"));
        entries.put("assets/minecraft/models/block/stone.json",
                utf8("{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"minecraft:block/stone\"}}"));
        entries.put("assets/minecraft/models/block/oak_stairs.json", utf8("{\"parent\":\"minecraft:block/stairs\"}"));
        entries.put("assets/minecraft/models/item/stick.json", utf8("{\"parent\":\"minecraft:item/handheld\"}"));
        entries.put("assets/minecraft/models/entity/pig.json", utf8("{}"));
        entries.put("assets/minecraft/items/stick.json",
                utf8("{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/stick\"}}"));
        entries.put("assets/minecraft/textures/block/stone.png", STONE);
        entries.put("assets/minecraft/textures/block/water_still.png", STONE);
        entries.put("assets/minecraft/textures/block/water_still.png.mcmeta",
                utf8("{\"animation\":{\"frametime\":2,\"interpolate\":false},\"villager\":{\"hat\":\"full\"}}"));
        entries.put("assets/minecraft/textures/item/stick.png", STONE);
        entries.put("assets/minecraft/textures/colormap/grass.png", STONE);
        entries.put("assets/minecraft/textures/colormap/fire.png", STONE);
        entries.put("assets/minecraft/textures/entity/pig.png", STONE);
        entries.put("assets/minecraft/textures/block/../../../../evil.png", STONE);
        entries.put("data/minecraft/recipe/stick.json", utf8("{}"));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String sha1(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Served(WebServer server, URLClassLoader files, FakeVistructum vistructum, HttpClient client)
            implements AutoCloseable {

        URI uri(String path) {
            return URI.create("http://127.0.0.1:" + server.port() + path);
        }

        HttpResponse<String> get(String path, Map<String, String> headers) throws Exception {
            return send(HttpRequest.newBuilder(uri(path)).GET(), headers);
        }

        HttpResponse<String> send(HttpRequest.Builder request, Map<String, String> headers) throws Exception {
            headers.forEach(request::header);
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<byte[]> bytes(String path, Map<String, String> headers) throws Exception {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).GET();
            headers.forEach(request::header);
            return client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        }

        JsonObject status() throws Exception {
            String login = vistructum.issueLogin(UUID.randomUUID(), "Staff").join();
            String session = vistructum.redeemLogin(login).join().orElseThrow().token();
            HttpResponse<String> response = get("/review/api/status", Map.of("Cookie", "vistructum_session=" + session));
            assertEquals(200, response.statusCode());
            return JsonParser.parseString(response.body()).getAsJsonObject();
        }

        @Override
        public void close() throws IOException {
            server.close();
            files.close();
        }
    }
}
