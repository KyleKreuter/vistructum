package de.kylekreuter.vistructum.ui.web.controller;

import de.kylekreuter.vistructum.ui.web.WebFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static de.kylekreuter.vistructum.ui.web.WebFixture.STAFF;
import static de.kylekreuter.vistructum.ui.web.WebFixture.STRANGER;
import static de.kylekreuter.vistructum.ui.web.WebFixture.authorized;
import static de.kylekreuter.vistructum.ui.web.WebFixture.cookie;
import static de.kylekreuter.vistructum.ui.web.WebFixture.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockLogControllerTest {

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
    void attributionReturnsTheFindingWithTheLoggedBuilders() throws Exception {
        String session = web.session();
        web.vistructum.builders.put(8L, Set.of(STRANGER));

        HttpResponse<String> response = web.post("/review/api/findings/8/attribute", "", authorized(session));

        assertEquals(200, response.statusCode());
        var players = json(response).getAsJsonArray("players");
        assertEquals(1, players.size());
        assertEquals(STRANGER.toString(), players.get(0).getAsJsonObject().get("uuid").getAsString());
        assertEquals(404, web.post("/review/api/findings/99/attribute", "", authorized(session)).statusCode());
        assertEquals(403, web.post("/review/api/findings/8/attribute", "", cookie(session)).statusCode());
    }

    @Test
    void rollbackNeedsThePermissionAndAConfirmedFindingWithPlayers() throws Exception {
        String session = web.session();
        HttpResponse<String> forbidden = web.post("/review/api/findings/7/rollback", "", authorized(session));
        assertEquals(403, forbidden.statusCode());
        assertEquals("forbidden", json(forbidden).get("error").getAsString());
        web.rollbackers.add(STAFF);

        HttpResponse<String> open = web.post("/review/api/findings/8/rollback", "", authorized(session));
        assertEquals(409, open.statusCode());
        assertEquals("not_rollbackable", json(open).get("error").getAsString());
        assertEquals(404, web.post("/review/api/findings/99/rollback", "", authorized(session)).statusCode());

        HttpResponse<String> rolledBack = web.post("/review/api/findings/7/rollback", "", authorized(session));
        assertEquals(200, rolledBack.statusCode());
        assertEquals(12, json(rolledBack).get("restored").getAsInt());
        assertEquals(2, json(rolledBack).get("skipped").getAsInt());
        assertEquals(Map.of(7L, "Staff"), web.vistructum.rollbacks);
        var detail = json(web.get("/review/api/findings/7", cookie(session)));
        assertEquals("Staff", detail.get("rolledBackBy").getAsString());
        assertEquals(WebFixture.NOW.toString(), detail.get("rolledBackAt").getAsString());
    }

    @Test
    void withoutABlockLogTheActionsAreUnavailable() throws Exception {
        String session = web.session();
        web.rollbackers.add(STAFF);
        web.vistructum.blockLogAvailable = false;

        HttpResponse<String> attribute = web.post("/review/api/findings/8/attribute", "", authorized(session));
        assertEquals(503, attribute.statusCode());
        assertEquals("unavailable", json(attribute).get("error").getAsString());
        assertEquals(503, web.post("/review/api/findings/7/rollback", "", authorized(session)).statusCode());
        var me = json(web.get("/review/api/me", cookie(session)));
        assertFalse(me.get("blockLog").getAsBoolean());
        assertFalse(me.get("canRollback").getAsBoolean());
    }

    @Test
    void theSessionShowsTheBlockLogAndTheRollbackPermission() throws Exception {
        String session = web.session();
        var before = json(web.get("/review/api/me", cookie(session)));
        assertTrue(before.get("blockLog").getAsBoolean());
        assertFalse(before.get("canRollback").getAsBoolean());
        web.rollbackers.add(STAFF);
        assertTrue(json(web.get("/review/api/me", cookie(session))).get("canRollback").getAsBoolean());
    }
}
