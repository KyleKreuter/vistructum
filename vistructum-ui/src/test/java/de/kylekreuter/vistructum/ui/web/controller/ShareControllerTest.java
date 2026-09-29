package de.kylekreuter.vistructum.ui.web.controller;

import de.kylekreuter.vistructum.ui.web.WebFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static de.kylekreuter.vistructum.ui.web.WebFixture.NOW;
import static de.kylekreuter.vistructum.ui.web.WebFixture.STAFF;
import static de.kylekreuter.vistructum.ui.web.WebFixture.authorized;
import static de.kylekreuter.vistructum.ui.web.WebFixture.cookie;
import static de.kylekreuter.vistructum.ui.web.WebFixture.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShareControllerTest {

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
    void sharingNeedsThePermissionAndAConfirmedFindingWithEvidence() throws Exception {
        String session = web.session();
        HttpResponse<String> forbidden = web.post("/review/api/findings/7/share", "", authorized(session));
        assertEquals(403, forbidden.statusCode());
        assertEquals("forbidden", json(forbidden).get("error").getAsString());
        web.sharers.add(STAFF);
        HttpResponse<String> shared = web.post("/review/api/findings/7/share", "", authorized(session));
        assertEquals(200, shared.statusCode());
        assertEquals("https://review.example/review/e/share-7", json(shared).get("url").getAsString());
        assertEquals(NOW.toString(), json(shared).get("sharedSince").getAsString());
        HttpResponse<String> again = web.post("/review/api/findings/7/share", "", authorized(session));
        assertEquals(json(shared).get("url"), json(again).get("url"));
        var detail = json(web.get("/review/api/findings/7", cookie(session)));
        assertEquals("https://review.example/review/e/share-7", detail.get("shareUrl").getAsString());
        HttpResponse<String> open = web.post("/review/api/findings/8/share", "", authorized(session));
        assertEquals(409, open.statusCode());
        assertEquals("not_shareable", json(open).get("error").getAsString());
        assertEquals(404, web.post("/review/api/findings/99/share", "", authorized(session)).statusCode());
        assertEquals(404, web.get("/review/api/findings/7/share", cookie(session)).statusCode());
        HttpResponse<String> revoked = web.send(HttpRequest.newBuilder(web.uri("/review/api/findings/7/share"))
                .DELETE(), authorized(session));
        assertEquals(204, revoked.statusCode());
        assertEquals("", revoked.body());
        assertTrue(web.vistructum.shares.isEmpty());
    }
}
