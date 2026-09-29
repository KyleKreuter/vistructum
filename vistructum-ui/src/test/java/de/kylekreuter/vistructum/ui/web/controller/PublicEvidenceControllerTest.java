package de.kylekreuter.vistructum.ui.web.controller;

import com.google.gson.JsonObject;
import de.kylekreuter.vistructum.api.Review;
import de.kylekreuter.vistructum.api.Verdict;
import de.kylekreuter.vistructum.ui.web.WebFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static de.kylekreuter.vistructum.ui.web.WebFixture.BUILDER;
import static de.kylekreuter.vistructum.ui.web.WebFixture.NOW;
import static de.kylekreuter.vistructum.ui.web.WebFixture.STRANGER;
import static de.kylekreuter.vistructum.ui.web.WebFixture.finding;
import static de.kylekreuter.vistructum.ui.web.WebFixture.json;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PublicEvidenceControllerTest {

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
    void publicEvidenceIsRelativeAndOmitsTheLocation() throws Exception {
        web.vistructum.share(7, "Staff").join();
        HttpResponse<String> response = web.get("/review/api/public/share-7", Map.of());
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
        HttpResponse<byte[]> skin = web.bytes("/review/api/public/share-7/skins/" + BUILDER + ".png", Map.of());
        assertEquals(200, skin.statusCode());
        assertArrayEquals(new byte[]{1, 2, 3}, skin.body());
        assertEquals("slim", skin.headers().firstValue("X-Skin-Model").orElseThrow());
        assertEquals("image/png", skin.headers().firstValue("Content-Type").orElseThrow());
        assertEquals(404, web.get("/review/api/public/share-7/skins/" + STRANGER + ".png", Map.of()).statusCode());
        assertEquals(404, web.get("/review/api/public/share-7/skins/" + BUILDER + ".jpg", Map.of()).statusCode());
        assertEquals(400, web.get("/review/api/public/share-7/skins/nobody.png", Map.of()).statusCode());
        assertEquals(404, web.get("/review/api/public/share-7/other", Map.of()).statusCode());
        assertEquals(404, web.get("/review/api/public/unknown", Map.of()).statusCode());
        assertEquals(401, web.get("/review/api/public", Map.of()).statusCode());
        assertEquals(401, web.post("/review/api/public/share-7", "", Map.of()).statusCode());
        web.vistructum.unshare(7, "Staff").join();
        assertEquals(404, web.get("/review/api/public/share-7", Map.of()).statusCode());
    }

    @Test
    void publicEvidenceStaysAvailableAfterTheVerdictChanges() throws Exception {
        web.vistructum.share(7, "Staff").join();
        web.vistructum.findings.put(7L, finding(7, Optional.of(new Review(Verdict.FALSE_ALARM, "Staff", NOW))));

        HttpResponse<String> response = web.get("/review/api/public/share-7", Map.of());

        assertEquals(200, response.statusCode());
        assertEquals("FALSE_ALARM", json(response).getAsJsonObject("finding").get("verdict").getAsString());
    }
}
