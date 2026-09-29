package de.kylekreuter.vistructum.ui.web.controller;

import de.kylekreuter.vistructum.ui.web.WebFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;

import static de.kylekreuter.vistructum.ui.web.WebFixture.INDEX;
import static de.kylekreuter.vistructum.ui.web.WebFixture.SCRIPT;
import static de.kylekreuter.vistructum.ui.web.WebFixture.cookie;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpaControllerTest {

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
    void appRoutesFallBackToTheIndexAndAssetsAreCachedLong() throws Exception {
        HttpResponse<String> route = web.get("/review/findings/7/evidence", Map.of());
        assertEquals(200, route.statusCode());
        assertEquals(INDEX, route.body());
        assertEquals("no-cache", route.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals("text/html;charset=utf-8", route.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("nosniff", route.headers().firstValue("X-Content-Type-Options").orElseThrow());
        assertEquals(INDEX, web.get("/review/", Map.of()).body());
        HttpResponse<String> asset = web.get("/review/assets/app-1234.js", Map.of());
        assertEquals(200, asset.statusCode());
        assertEquals(SCRIPT, asset.body());
        assertEquals("public, max-age=31536000, immutable", asset.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals("application/javascript; charset=utf-8", asset.headers().firstValue("Content-Type")
                .orElseThrow());
        assertEquals(404, web.get("/review/assets/missing.js", Map.of()).statusCode());
        assertEquals("Not found", web.get("/review/assets/missing.js", Map.of()).body());
        assertEquals(404, web.get("/review/assets/%2e%2e/index.html", Map.of()).statusCode());
        assertEquals(404, web.get("/review/api/unknown", cookie(web.session())).statusCode());
    }

    @Test
    void theRootRedirectsToTheApp() throws Exception {
        HttpResponse<String> response = web.get("/review", Map.of());
        assertEquals(302, response.statusCode());
        assertEquals("/review/", response.headers().firstValue("Location").orElseThrow());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals("no-referrer", response.headers().firstValue("Referrer-Policy").orElseThrow());
        assertEquals(302, web.post("/review", "", Map.of()).statusCode());
    }

    @Test
    void changesOutsideTheApiAreNotAllowed() throws Exception {
        HttpResponse<String> response = web.post("/review/findings", "", Map.of());
        assertEquals(405, response.statusCode());
        assertEquals("GET, HEAD", response.headers().firstValue("Allow").orElseThrow());
        assertEquals("Method not allowed", response.body());
        HttpResponse<String> head = web.head("/review/findings", Map.of());
        assertEquals(200, head.statusCode());
        assertEquals("", head.body());
    }

    @Test
    void aBuildWithoutTheAppAnswersUnavailable() throws Exception {
        web = web.withoutBuiltFiles();
        HttpResponse<String> response = web.get("/review/", Map.of());
        assertEquals(503, response.statusCode());
        assertTrue(response.body().contains("not part of this build"));
    }

    @Test
    void nothingIsServedBelowReviewWhenTheAppIsDisabled() throws Exception {
        web = web.withoutApp();
        assertEquals(404, web.get("/review/", Map.of()).statusCode());
        assertEquals(404, web.get("/review/api/palette", Map.of()).statusCode());
        assertEquals(404, web.get("/review/login?token=x", Map.of()).statusCode());
        HttpResponse<byte[]> pack = web.bytes("/pack.zip", Map.of());
        assertEquals(200, pack.statusCode());
        assertEquals("application/zip", pack.headers().firstValue("Content-Type").orElseThrow());
        assertTrue(pack.body().length > 0);
        assertEquals(String.valueOf(pack.body().length), pack.headers().firstValue("Content-Length").orElseThrow());
    }
}
