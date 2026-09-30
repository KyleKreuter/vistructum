package de.kylekreuter.vistructum.ui.web.controller;

import com.google.gson.JsonObject;
import de.kylekreuter.vistructum.ui.web.WebFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static de.kylekreuter.vistructum.ui.web.WebFixture.cookie;
import static de.kylekreuter.vistructum.ui.web.WebFixture.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusControllerTest {

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
    void statusCarriesTheInferenceModelsAndTheRecordingSwitch() throws Exception {
        JsonObject status = json(web.get("/review/api/status", cookie(web.session())));
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
    void statsAndActivityCheckTheirRange() throws Exception {
        Map<String, String> cookie = cookie(web.session());
        assertEquals(200, web.get("/review/api/stats", cookie).statusCode());
        assertEquals(400, web.get("/review/api/stats?from=2026-09-30T00:00:00Z&to=2026-09-01T00:00:00Z", cookie)
                .statusCode());
        assertEquals(400, web.get("/review/api/stats?from=yesterday", cookie).statusCode());
        assertTrue(json(web.get("/review/api/activity?page=1&pageSize=10", cookie)).getAsJsonArray("items").isEmpty());
        assertEquals(0, json(web.get("/review/api/activity", cookie)).get("total").getAsLong());
        assertEquals(400, web.get("/review/api/activity?pageSize=101", cookie).statusCode());
        assertEquals(400, web.get("/review/api/activity?page=0", cookie).statusCode());
    }
}
