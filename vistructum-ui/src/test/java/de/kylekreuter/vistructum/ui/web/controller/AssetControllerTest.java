package de.kylekreuter.vistructum.ui.web.controller;

import de.kylekreuter.vistructum.ui.web.WebFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;

import static de.kylekreuter.vistructum.ui.web.WebFixture.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetControllerTest {

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
    void assetsAreUnavailableWithoutTheOptIn() throws Exception {
        HttpResponse<String> response = web.get("/review/api/assets", Map.of());
        assertEquals(200, response.statusCode());
        assertEquals("no-cache", response.headers().firstValue("Cache-Control").orElseThrow());
        assertFalse(json(response).get("available").getAsBoolean());
        assertTrue(json(response).get("version").isJsonNull());
        assertEquals(404, web.get("/review/api/assets/1.21.4/models.json", Map.of()).statusCode());
        assertEquals(404, web.get("/review/api/assets/1.21.4/textures/block/stone.png", Map.of()).statusCode());
        assertEquals(404, web.get("/review/api/assets/1.21.4", Map.of()).statusCode());
        assertEquals(404, web.get("/review/api/assets/1.21.4/other/file", Map.of()).statusCode());
    }

    @Test
    void paletteIsPublicAndCached() throws Exception {
        HttpResponse<String> response = web.get("/review/api/palette", Map.of());
        assertEquals(200, response.statusCode());
        assertEquals(0x707070, json(response).get("minecraft:stone").getAsInt());
        assertEquals("public, max-age=86400", response.headers().firstValue("Cache-Control").orElseThrow());
    }
}
