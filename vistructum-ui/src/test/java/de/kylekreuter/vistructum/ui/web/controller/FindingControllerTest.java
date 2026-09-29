package de.kylekreuter.vistructum.ui.web.controller;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.kylekreuter.vistructum.api.BlockVolume;
import de.kylekreuter.vistructum.api.FindingTerrain;
import de.kylekreuter.vistructum.ui.web.WebFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

import static de.kylekreuter.vistructum.ui.web.WebFixture.BUILDER;
import static de.kylekreuter.vistructum.ui.web.WebFixture.CSRF;
import static de.kylekreuter.vistructum.ui.web.WebFixture.NOW;
import static de.kylekreuter.vistructum.ui.web.WebFixture.authorized;
import static de.kylekreuter.vistructum.ui.web.WebFixture.cookie;
import static de.kylekreuter.vistructum.ui.web.WebFixture.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FindingControllerTest {

    @TempDir
    Path root;

    private WebFixture web;

    @BeforeEach
    void start() throws Exception {
        web = WebFixture.withData(root);
    }

    @AfterEach
    void stop() throws Exception {
        web.close();
    }

    @Test
    void changesNeedTheCsrfHeader() throws Exception {
        String session = web.session();
        String body = "{\"verdict\":\"FALSE_ALARM\"}";
        HttpResponse<String> missing = web.post("/review/api/findings/8/verdict", body,
                Map.of("Cookie", "vistructum_session=" + session + "; vistructum_csrf=" + CSRF));
        assertEquals(403, missing.statusCode());
        assertEquals("csrf", json(missing).get("error").getAsString());
        HttpResponse<String> wrong = web.post("/review/api/findings/8/verdict", body, Map.of(
                "Cookie", "vistructum_session=" + session + "; vistructum_csrf=" + CSRF, "X-Vistructum-Csrf", "other"));
        assertEquals(403, wrong.statusCode());
        assertTrue(web.vistructum.findings.get(8L).open());
        HttpResponse<String> accepted = web.post("/review/api/findings/8/verdict", body, authorized(session));
        assertEquals(200, accepted.statusCode());
        JsonObject review = json(accepted).getAsJsonObject("review");
        assertEquals("FALSE_ALARM", review.get("verdict").getAsString());
        assertEquals("Staff", review.get("reviewer").getAsString());
        assertEquals(400, web.post("/review/api/findings/8/verdict", "{\"verdict\":\"MAYBE\"}", authorized(session))
                .statusCode());
        assertEquals(400, web.post("/review/api/findings/8/verdict", "[]", authorized(session)).statusCode());
        assertEquals(404, web.post("/review/api/findings/99/verdict", body, authorized(session)).statusCode());
    }

    @Test
    void findingsAreListedWithEvidenceAndPaging() throws Exception {
        Map<String, String> cookie = cookie(web.session());
        JsonObject page = json(web.get("/review/api/findings?state=any&pageSize=1", cookie));
        assertEquals(2, page.get("total").getAsLong());
        assertEquals(1, page.get("page").getAsInt());
        assertEquals(1, page.get("pageSize").getAsInt());
        assertFalse(page.has("nextBefore"));
        JsonObject second = json(web.get("/review/api/findings?state=any&pageSize=1&page=2", cookie));
        assertEquals(7, second.getAsJsonArray("items").get(0).getAsJsonObject().get("id").getAsLong());
        assertEquals(2, second.get("page").getAsInt());
        JsonObject beyond = json(web.get("/review/api/findings?pageSize=1&page=3", cookie));
        assertTrue(beyond.getAsJsonArray("items").isEmpty());
        assertEquals(2, beyond.get("total").getAsLong());
        assertEquals(25, json(web.get("/review/api/findings", cookie)).get("pageSize").getAsInt());
        JsonObject first = page.getAsJsonArray("items").get(0).getAsJsonObject();
        assertEquals(8, first.get("id").getAsLong());
        assertEquals("fullscan", first.get("source").getAsString());
        assertTrue(first.get("review").isJsonNull());
        assertTrue(first.get("sharedSince").isJsonNull());
        assertTrue(first.get("shareUrl").isJsonNull());
        assertFalse(first.get("hasEvidence").getAsBoolean());
        assertFalse(first.has("teleport"));
        assertEquals(NOW.minusSeconds(3600).toString(), first.get("createdAt").getAsString());
        JsonObject detail = json(web.get("/review/api/findings/7", cookie));
        assertTrue(detail.get("hasEvidence").getAsBoolean());
        assertEquals("/tp @s 105 65 205", detail.get("teleport").getAsString());
        assertEquals(BUILDER.toString(), detail.getAsJsonArray("players").get(0).getAsJsonObject().get("uuid")
                .getAsString());
        assertEquals("Builder", detail.getAsJsonArray("players").get(0).getAsJsonObject().get("name").getAsString());
        assertEquals(400, web.get("/review/api/findings?pageSize=101", cookie).statusCode());
        assertEquals(400, web.get("/review/api/findings?pageSize=0", cookie).statusCode());
        assertEquals(400, web.get("/review/api/findings?page=0", cookie).statusCode());
        assertEquals(400, web.get("/review/api/findings?page=x", cookie).statusCode());
        assertEquals(400, web.get("/review/api/findings?state=closed", cookie).statusCode());
        assertEquals(404, web.get("/review/api/findings/99", cookie).statusCode());
    }

    @Test
    void theFindingIdIsCheckedBeforeTheRoute() throws Exception {
        String session = web.session();
        assertEquals(400, web.get("/review/api/findings/x", cookie(session)).statusCode());
        assertEquals(400, web.get("/review/api/findings/x/other", cookie(session)).statusCode());
        assertEquals(400, web.get("/review/api/findings/x/a/b", cookie(session)).statusCode());
        assertEquals(400, web.method("PUT", "/review/api/findings/x/share", authorized(session)).statusCode());
        assertEquals(404, web.get("/review/api/findings/7/other", cookie(session)).statusCode());
        assertEquals(404, web.get("/review/api/findings/7/a/b", cookie(session)).statusCode());
        assertEquals(404, web.method("DELETE", "/review/api/findings/7", authorized(session)).statusCode());
        assertEquals(404, web.post("/review/api/findings", "", authorized(session)).statusCode());
        assertEquals(404, web.get("/review/api/findings/7/verdict", cookie(session)).statusCode());
    }

    @Test
    void headAnswersWithoutABody() throws Exception {
        HttpResponse<String> head = web.head("/review/api/findings/7", cookie(web.session()));
        assertEquals(200, head.statusCode());
        assertEquals("", head.body());
        assertTrue(head.headers().firstValue("Content-Type").orElseThrow().startsWith("application/json"));
    }

    @Test
    void jsonIsCompressedWhenTheClientAcceptsGzip() throws Exception {
        String session = web.session();
        HttpResponse<byte[]> response = web.bytes("/review/api/findings/7/evidence",
                Map.of("Cookie", "vistructum_session=" + session, "Accept-Encoding", "gzip"));
        assertEquals("gzip", response.headers().firstValue("Content-Encoding").orElseThrow());
        assertTrue(response.headers().allValues("Vary").contains("Accept-Encoding"));
        String body;
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(response.body()))) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        JsonObject evidence = JsonParser.parseString(body).getAsJsonObject();
        assertEquals(100, evidence.getAsJsonObject("before").get("minX").getAsInt());
        assertEquals(NOW.toEpochMilli(), evidence.getAsJsonArray("changes").get(0).getAsJsonObject().get("t")
                .getAsLong());
        HttpResponse<String> plain = web.get("/review/api/findings/7/evidence", cookie(session));
        assertTrue(plain.headers().firstValue("Content-Encoding").isEmpty());
        assertEquals(body, plain.body());
    }

    @Test
    void thumbnailsArePicturesAndMissingScenesAreNotFound() throws Exception {
        String session = web.session();
        HttpResponse<byte[]> thumbnail = web.bytes("/review/api/findings/7/thumbnail.png",
                Map.of("Cookie", "vistructum_session=" + session, "Accept-Encoding", "gzip"));
        assertEquals(200, thumbnail.statusCode());
        assertEquals("image/png", thumbnail.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("private, max-age=3600", thumbnail.headers().firstValue("Cache-Control").orElseThrow());
        assertTrue(thumbnail.headers().firstValue("Content-Encoding").isEmpty());
        assertEquals(404, web.get("/review/api/findings/99/thumbnail.png", cookie(session)).statusCode());
        assertEquals(404, web.get("/review/api/findings/7/scene", cookie(session)).statusCode());
        assertEquals(503, web.get("/review/api/findings/7/heatmap", cookie(session)).statusCode());
        assertEquals(404, web.get("/review/api/findings/99/heatmap", cookie(session)).statusCode());
        assertEquals(404, web.get("/review/api/findings/8/evidence", cookie(session)).statusCode());
    }

    @Test
    void terrainIsServedWithItsSceneOrigin() throws Exception {
        web.vistructum.terrain.put(8L, new FindingTerrain(new BlockVolume(100, 60, 200, 2, 1, 1,
                List.of("minecraft:stone", "minecraft:oak_leaves[distance=1]"), new int[]{0, 1}),
                Optional.of(new FindingTerrain.SceneOrigin(96, 192))));
        String session = web.session();
        JsonObject detail = json(web.get("/review/api/findings/8", cookie(session)));
        assertTrue(detail.get("hasTerrain").getAsBoolean());
        assertFalse(json(web.get("/review/api/findings/7", cookie(session))).get("hasTerrain").getAsBoolean());
        JsonObject terrain = json(web.get("/review/api/findings/8/terrain", cookie(session)));
        JsonObject blocks = terrain.getAsJsonObject("blocks");
        assertEquals(100, blocks.get("minX").getAsInt());
        assertEquals(2, blocks.get("sizeX").getAsInt());
        assertEquals("minecraft:oak_leaves[distance=1]", blocks.getAsJsonArray("palette").get(1).getAsString());
        assertEquals(1, blocks.getAsJsonArray("cells").get(1).getAsInt());
        assertEquals(96, terrain.getAsJsonObject("sceneOrigin").get("x").getAsInt());
        assertEquals(192, terrain.getAsJsonObject("sceneOrigin").get("z").getAsInt());
        assertEquals(404, web.get("/review/api/findings/7/terrain", cookie(session)).statusCode());
    }
}
