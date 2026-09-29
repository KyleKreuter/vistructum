package de.kylekreuter.vistructum.ui.web;

import com.google.gson.JsonElement;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebServerTest {

    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");
    private static final UUID STAFF = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BUILDER = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final String CSRF = "csrf-token";
    private static final String INDEX = "<!doctype html><title>review</title>";

    @TempDir
    Path web;

    private FakeVistructum vistructum;
    private final Set<UUID> sharers = new HashSet<>();
    private WebServer server;
    private URLClassLoader files;
    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @BeforeEach
    void start() throws IOException {
        vistructum = new FakeVistructum(NOW);
        vistructum.findings.put(7L, finding(7, Optional.of(new Review(Verdict.CONFIRMED, "Staff", NOW))));
        vistructum.findings.put(8L, finding(8, Optional.empty()));
        vistructum.evidence.put(7L, evidence());
        vistructum.skins.put(BUILDER, new PlayerSkin(BUILDER, Optional.of("Builder"), new byte[]{1, 2, 3}, true));
        vistructum.skins.put(STRANGER, new PlayerSkin(STRANGER, Optional.of("Stranger"), new byte[]{4}, false));
        Files.createDirectories(web.resolve("web/assets"));
        Files.writeString(web.resolve("web/index.html"), INDEX);
        Files.writeString(web.resolve("web/assets/app-1234.js"), "console.log('review');");
        files = new URLClassLoader(new URL[]{web.toUri().toURL()}, null);
        server = start(Optional.of(app(files)));
    }

    @AfterEach
    void stop() throws IOException {
        server.close();
        files.close();
    }

    @Test
    void privateEndpointsNeedASession() throws Exception {
        HttpResponse<String> response = get("/review/api/me", Map.of());
        assertEquals(401, response.statusCode());
        assertEquals("unauthorized", json(response).get("error").getAsString());
        assertEquals(401, get("/review/api/findings", Map.of("Cookie", "vistructum_session=forged")).statusCode());
    }

    @Test
    void loginSetsTheCookiesAndRedirectsToTheRequestedPage() throws Exception {
        String login = vistructum.issueLogin(STAFF, "Staff").join();
        HttpResponse<String> response = get("/review/login?token=" + login + "&next=/review/findings/7/evidence",
                Map.of());
        assertEquals(302, response.statusCode());
        assertEquals("/review/findings/7/evidence", response.headers().firstValue("Location").orElseThrow());
        List<String> cookies = response.headers().allValues("Set-Cookie");
        String session = cookies.stream().filter(cookie -> cookie.startsWith("vistructum_session=")).findFirst()
                .orElseThrow();
        String csrf = cookies.stream().filter(cookie -> cookie.startsWith("vistructum_csrf=")).findFirst().orElseThrow();
        assertTrue(session.contains("HttpOnly") && session.contains("SameSite=Strict") && session.contains("Path=/review")
                && session.contains("Max-Age=43200"), session);
        assertFalse(csrf.contains("HttpOnly"), csrf);
        assertTrue(session.contains("Secure") && csrf.contains("Secure"), session);
        String token = session.substring("vistructum_session=".length(), session.indexOf(';'));
        HttpResponse<String> me = get("/review/api/me", Map.of("Cookie", "vistructum_session=" + token));
        assertEquals(200, me.statusCode());
        assertEquals("Staff", json(me).get("name").getAsString());
        assertEquals(STAFF.toString(), json(me).get("player").getAsString());
        assertFalse(json(me).get("canShare").getAsBoolean());
    }

    @Test
    void usedOrUnknownLoginTokensAndForeignTargetsAreRejected() throws Exception {
        String login = vistructum.issueLogin(STAFF, "Staff").join();
        HttpResponse<String> probe = send(HttpRequest.newBuilder(uri("/review/login?token=" + login))
                .method("HEAD", HttpRequest.BodyPublishers.noBody()), Map.of());
        assertEquals(405, probe.statusCode());
        HttpResponse<String> first = get("/review/login?token=" + login + "&next=//evil.example/", Map.of());
        assertEquals("/review/", first.headers().firstValue("Location").orElseThrow());
        HttpResponse<String> again = get("/review/login?token=" + login, Map.of());
        assertEquals(302, again.statusCode());
        assertEquals("/review/?login=expired", again.headers().firstValue("Location").orElseThrow());
        assertTrue(again.headers().allValues("Set-Cookie").isEmpty());
    }

    @Test
    void changesNeedTheCsrfHeader() throws Exception {
        String session = session();
        String body = "{\"verdict\":\"FALSE_ALARM\"}";
        HttpResponse<String> missing = post("/review/api/findings/8/verdict", body,
                Map.of("Cookie", "vistructum_session=" + session + "; vistructum_csrf=" + CSRF));
        assertEquals(403, missing.statusCode());
        assertEquals("csrf", json(missing).get("error").getAsString());
        HttpResponse<String> wrong = post("/review/api/findings/8/verdict", body, Map.of(
                "Cookie", "vistructum_session=" + session + "; vistructum_csrf=" + CSRF, "X-Vistructum-Csrf", "other"));
        assertEquals(403, wrong.statusCode());
        assertTrue(vistructum.findings.get(8L).open());
        HttpResponse<String> accepted = post("/review/api/findings/8/verdict", body, authorized(session));
        assertEquals(200, accepted.statusCode());
        JsonObject review = json(accepted).getAsJsonObject("review");
        assertEquals("FALSE_ALARM", review.get("verdict").getAsString());
        assertEquals("Staff", review.get("reviewer").getAsString());
        assertEquals(400, post("/review/api/findings/8/verdict", "{\"verdict\":\"MAYBE\"}", authorized(session))
                .statusCode());
    }

    @Test
    void sharingNeedsThePermissionAndAConfirmedFindingWithEvidence() throws Exception {
        String session = session();
        HttpResponse<String> forbidden = post("/review/api/findings/7/share", "", authorized(session));
        assertEquals(403, forbidden.statusCode());
        assertEquals("forbidden", json(forbidden).get("error").getAsString());
        sharers.add(STAFF);
        HttpResponse<String> shared = post("/review/api/findings/7/share", "", authorized(session));
        assertEquals(200, shared.statusCode());
        assertEquals("https://review.example/review/e/share-7", json(shared).get("url").getAsString());
        assertEquals(NOW.toString(), json(shared).get("sharedSince").getAsString());
        HttpResponse<String> again = post("/review/api/findings/7/share", "", authorized(session));
        assertEquals(json(shared).get("url"), json(again).get("url"));
        JsonObject detail = json(get("/review/api/findings/7", Map.of("Cookie", "vistructum_session=" + session)));
        assertEquals("https://review.example/review/e/share-7", detail.get("shareUrl").getAsString());
        HttpResponse<String> open = post("/review/api/findings/8/share", "", authorized(session));
        assertEquals(409, open.statusCode());
        assertEquals("not_shareable", json(open).get("error").getAsString());
        assertEquals(404, post("/review/api/findings/99/share", "", authorized(session)).statusCode());
        HttpResponse<String> revoked = send(HttpRequest.newBuilder(uri("/review/api/findings/7/share")).DELETE(),
                authorized(session));
        assertEquals(204, revoked.statusCode());
        assertTrue(vistructum.shares.isEmpty());
    }

    @Test
    void findingsAreListedWithEvidenceAndPaging() throws Exception {
        String session = session();
        Map<String, String> cookie = Map.of("Cookie", "vistructum_session=" + session);
        JsonObject page = json(get("/review/api/findings?state=any&limit=1", cookie));
        assertEquals(2, page.get("total").getAsLong());
        assertEquals(8, page.get("nextBefore").getAsLong());
        JsonObject first = page.getAsJsonArray("items").get(0).getAsJsonObject();
        assertEquals(8, first.get("id").getAsLong());
        assertEquals("fullscan", first.get("source").getAsString());
        assertTrue(first.get("review").isJsonNull());
        assertTrue(first.get("sharedSince").isJsonNull());
        assertTrue(first.get("shareUrl").isJsonNull());
        assertFalse(first.get("hasEvidence").getAsBoolean());
        JsonObject detail = json(get("/review/api/findings/7", cookie));
        assertTrue(detail.get("hasEvidence").getAsBoolean());
        assertEquals("/tp @s 105 65 205", detail.get("teleport").getAsString());
        assertEquals(BUILDER.toString(), detail.getAsJsonArray("players").get(0).getAsJsonObject().get("uuid")
                .getAsString());
        assertEquals("Builder", detail.getAsJsonArray("players").get(0).getAsJsonObject().get("name").getAsString());
        assertEquals(400, get("/review/api/findings?limit=101", cookie).statusCode());
        assertEquals(400, get("/review/api/findings?state=closed", cookie).statusCode());
        assertEquals(404, get("/review/api/findings/99", cookie).statusCode());
    }

    @Test
    void jsonIsCompressedWhenTheClientAcceptsGzip() throws Exception {
        String session = session();
        HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder(uri("/review/api/findings/7/evidence"))
                .header("Cookie", "vistructum_session=" + session).header("Accept-Encoding", "gzip").build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals("gzip", response.headers().firstValue("Content-Encoding").orElseThrow());
        String body;
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(response.body()))) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        JsonObject evidence = JsonParser.parseString(body).getAsJsonObject();
        assertEquals(100, evidence.getAsJsonObject("before").get("minX").getAsInt());
        assertEquals(NOW.toEpochMilli(), evidence.getAsJsonArray("changes").get(0).getAsJsonObject().get("t")
                .getAsLong());
    }

    @Test
    void publicEvidenceIsRelativeAndOmitsTheLocation() throws Exception {
        vistructum.share(7, "Staff").join();
        HttpResponse<String> response = get("/review/api/public/share-7", Map.of());
        assertEquals(200, response.statusCode());
        JsonObject body = json(response);
        JsonObject finding = body.getAsJsonObject("finding");
        assertEquals(Set.of("id", "createdAt", "verdict", "players"), finding.keySet());
        assertEquals("CONFIRMED", finding.get("verdict").getAsString());
        JsonObject evidence = body.getAsJsonObject("evidence");
        JsonObject before = evidence.getAsJsonObject("before");
        assertEquals(0, before.get("minX").getAsInt());
        assertEquals(0, before.get("minY").getAsInt());
        assertEquals(0, before.get("minZ").getAsInt());
        JsonObject change = evidence.getAsJsonArray("changes").get(0).getAsJsonObject();
        assertEquals(1, change.get("x").getAsInt());
        assertEquals(2, change.get("y").getAsInt());
        assertEquals(3, change.get("z").getAsInt());
        JsonObject frame = evidence.getAsJsonArray("recordings").get(0).getAsJsonObject().getAsJsonArray("frames")
                .get(0).getAsJsonObject();
        assertEquals(0.5, frame.get("x").getAsDouble());
        assertEquals(-1.0, frame.get("y").getAsDouble());
        assertEquals(4.25, frame.get("z").getAsDouble());
        assertFalse(body.toString().contains("world"));
        HttpResponse<byte[]> skin = client.send(HttpRequest.newBuilder(uri("/review/api/public/share-7/skins/"
                + BUILDER + ".png")).build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, skin.statusCode());
        assertEquals("slim", skin.headers().firstValue("X-Skin-Model").orElseThrow());
        assertEquals(404, get("/review/api/public/share-7/skins/" + STRANGER + ".png", Map.of()).statusCode());
        assertEquals(404, get("/review/api/public/unknown", Map.of()).statusCode());
        vistructum.unshare(7, "Staff").join();
        assertEquals(404, get("/review/api/public/share-7", Map.of()).statusCode());
    }

    @Test
    void publicEvidenceStaysAvailableAfterTheVerdictChanges() throws Exception {
        vistructum.share(7, "Staff").join();
        vistructum.findings.put(7L, finding(7, Optional.of(new Review(Verdict.FALSE_ALARM, "Staff", NOW))));

        HttpResponse<String> response = get("/review/api/public/share-7", Map.of());

        assertEquals(200, response.statusCode());
        assertEquals("FALSE_ALARM", json(response).getAsJsonObject("finding").get("verdict").getAsString());
    }

    @Test
    void statusCarriesTheInferenceModelsAndTheRecordingSwitch() throws Exception {
        JsonObject status = json(get("/review/api/status", Map.of("Cookie", "vistructum_session=" + session())));
        assertEquals(3, status.get("trackedChanges").getAsInt());
        assertTrue(status.get("recordingEnabled").getAsBoolean());
        JsonObject inference = status.getAsJsonObject("inference");
        assertEquals("LOCAL", inference.get("mode").getAsString());
        assertEquals("bf-mask-1", inference.getAsJsonArray("models").get(0).getAsJsonObject().get("version")
                .getAsString());
        assertFalse(inference.has("detail"));
        assertEquals(0, status.getAsJsonArray("scans").size());
        JsonObject textures = status.getAsJsonObject("textures");
        assertFalse(textures.get("enabled").getAsBoolean());
        assertFalse(textures.get("available").getAsBoolean());
        assertTrue(textures.get("version").isJsonNull());
    }

    @Test
    void assetsAreUnavailableWithoutTheOptIn() throws Exception {
        HttpResponse<String> response = get("/review/api/assets", Map.of());
        assertEquals(200, response.statusCode());
        assertFalse(json(response).get("available").getAsBoolean());
        assertTrue(json(response).get("version").isJsonNull());
        assertEquals(404, get("/review/api/assets/1.21.4/models.json", Map.of()).statusCode());
        assertEquals(404, get("/review/api/assets/1.21.4/textures/block/stone.png", Map.of()).statusCode());
    }

    @Test
    void paletteIsPublicAndCached() throws Exception {
        HttpResponse<String> response = get("/review/api/palette", Map.of());
        assertEquals(200, response.statusCode());
        assertEquals(0x707070, json(response).get("minecraft:stone").getAsInt());
        assertTrue(response.headers().firstValue("Cache-Control").orElseThrow().contains("max-age=86400"));
    }

    @Test
    void appRoutesFallBackToTheIndexAndAssetsAreCachedLong() throws Exception {
        HttpResponse<String> route = get("/review/findings/7/evidence", Map.of());
        assertEquals(200, route.statusCode());
        assertEquals(INDEX, route.body());
        assertEquals("no-cache", route.headers().firstValue("Cache-Control").orElseThrow());
        assertTrue(route.headers().firstValue("Content-Type").orElseThrow().startsWith("text/html"));
        HttpResponse<String> asset = get("/review/assets/app-1234.js", Map.of());
        assertEquals(200, asset.statusCode());
        assertTrue(asset.headers().firstValue("Cache-Control").orElseThrow().contains("immutable"));
        assertTrue(asset.headers().firstValue("Content-Type").orElseThrow().startsWith("application/javascript"));
        assertEquals(404, get("/review/assets/missing.js", Map.of()).statusCode());
        assertEquals(404, get("/review/assets/%2e%2e/index.html", Map.of()).statusCode());
        assertEquals(404, get("/review/api/unknown", Map.of("Cookie", "vistructum_session=" + session()))
                .statusCode());
        assertEquals("/review/", get("/review", Map.of()).headers().firstValue("Location").orElseThrow());
    }

    @Test
    void aBuildWithoutTheAppAnswersUnavailable() throws Exception {
        server.close();
        try (URLClassLoader empty = new URLClassLoader(new URL[0], null)) {
            server = start(Optional.of(app(empty)));
            HttpResponse<String> response = get("/review/", Map.of());
            assertEquals(503, response.statusCode());
            assertTrue(response.body().contains("not part of this build"));
        }
    }

    @Test
    void nothingIsServedBelowReviewWhenTheAppIsDisabled() throws Exception {
        server.close();
        server = start(Optional.empty());
        assertEquals(404, get("/review/", Map.of()).statusCode());
        assertEquals(404, get("/review/api/palette", Map.of()).statusCode());
        assertEquals(404, get("/review/login?token=x", Map.of()).statusCode());
        HttpResponse<byte[]> pack = client.send(HttpRequest.newBuilder(uri("/pack.zip")).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, pack.statusCode());
        assertTrue(pack.body().length > 0);
    }

    private WebServer start(Optional<WebServer.WebApp> app) throws IOException {
        return WebServer.start(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), ResourcePack.build(), app);
    }

    private WebServer.WebApp app(ClassLoader loader) {
        GameServer game = new GameServer() {
            @Override
            public Optional<String> playerName(UUID player) {
                return player.equals(BUILDER) ? Optional.of("Builder") : Optional.empty();
            }

            @Override
            public boolean canShare(UUID player) {
                return sharers.contains(player);
            }

            @Override
            public Optional<String> dimension(String world) {
                return world.equals("world") ? Optional.empty() : Optional.of("minecraft:" + world);
            }
        };
        return new WebServer.WebApp(vistructum, new WebSettings(true, "127.0.0.1", "https://review.example/"), game,
                Map.of("minecraft:stone", 0x707070), new GameAssets(web.resolve("assets"), "1.21.4", false),
                Runnable::run, loader, Clock.fixed(NOW, ZoneOffset.UTC),
                Logger.getLogger("test"));
    }

    private String session() {
        String login = vistructum.issueLogin(STAFF, "Staff").join();
        return vistructum.redeemLogin(login).join().orElseThrow().token();
    }

    private static Map<String, String> authorized(String session) {
        return Map.of("Cookie", "vistructum_session=" + session + "; vistructum_csrf=" + CSRF,
                "X-Vistructum-Csrf", CSRF);
    }

    private HttpResponse<String> get(String path, Map<String, String> headers) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).GET(), headers);
    }

    private HttpResponse<String> post(String path, String body, Map<String, String> headers) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).POST(HttpRequest.BodyPublishers.ofString(body)), headers);
    }

    private HttpResponse<String> send(HttpRequest.Builder request, Map<String, String> headers) throws Exception {
        headers.forEach(request::header);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.port() + path);
    }

    private static JsonObject json(HttpResponse<String> response) {
        JsonElement json = JsonParser.parseString(response.body());
        return json.getAsJsonObject();
    }

    private static Finding finding(long id, Optional<Review> review) {
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
}
