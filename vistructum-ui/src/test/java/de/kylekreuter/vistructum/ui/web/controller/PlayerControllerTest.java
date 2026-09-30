package de.kylekreuter.vistructum.ui.web.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.kylekreuter.vistructum.ui.integration.punishment.Punishment;
import de.kylekreuter.vistructum.ui.integration.punishment.PunishmentKind;
import de.kylekreuter.vistructum.ui.web.WebFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.kylekreuter.vistructum.ui.web.WebFixture.BUILDER;
import static de.kylekreuter.vistructum.ui.web.WebFixture.STRANGER;
import static de.kylekreuter.vistructum.ui.web.WebFixture.authorized;
import static de.kylekreuter.vistructum.ui.web.WebFixture.cookie;
import static de.kylekreuter.vistructum.ui.web.WebFixture.json;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerControllerTest {

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
    void aPlayerSummaryCountsTheFindings() throws Exception {
        JsonObject summary = json(web.get("/review/api/players/" + BUILDER, cookie(web.session())));
        assertEquals(BUILDER.toString(), summary.get("uuid").getAsString());
        assertEquals("Builder", summary.get("name").getAsString());
        JsonObject findings = summary.getAsJsonObject("findings");
        assertEquals(2, findings.get("total").getAsLong());
        assertEquals(1, findings.get("open").getAsLong());
        assertEquals(1, findings.get("confirmed").getAsLong());
        assertEquals(0, findings.get("falseAlarms").getAsLong());
        JsonObject stranger = json(web.get("/review/api/players/" + STRANGER, cookie(web.session())));
        assertTrue(stranger.get("name").isJsonNull());
    }

    @Test
    void skinsAndFacesArePictures() throws Exception {
        String session = web.session();
        HttpResponse<byte[]> skin = web.bytes("/review/api/players/" + STRANGER + "/skin.png", cookie(session));
        assertEquals(200, skin.statusCode());
        assertArrayEquals(new byte[]{4}, skin.body());
        assertEquals("classic", skin.headers().firstValue("X-Skin-Model").orElseThrow());
        assertEquals("private, max-age=3600", skin.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals(404, web.get("/review/api/players/" + STRANGER + "/face.png", cookie(session)).statusCode());
    }

    @Test
    void punishmentsAreListedNewestFirstWithOperatorNames() throws Exception {
        Instant issued = Instant.parse("2026-09-01T10:00:00Z");
        web.punishments.put(STRANGER, List.of(
                new Punishment(PunishmentKind.WARN, Optional.of("spam"), Optional.of("Console"), Optional.empty(),
                        Optional.of(issued.minusSeconds(86400)), Optional.empty(), false),
                new Punishment(PunishmentKind.BAN, Optional.of("swastika"), Optional.empty(), Optional.of(BUILDER),
                        Optional.of(issued), Optional.of(issued.plusSeconds(2592000)), true)));
        JsonObject punishments = json(web.get("/review/api/players/" + STRANGER + "/punishments",
                cookie(web.session())));
        assertEquals("LibertyBans", punishments.get("source").getAsString());
        JsonArray items = punishments.getAsJsonArray("items");
        assertEquals(2, items.size());
        JsonObject ban = items.get(0).getAsJsonObject();
        assertEquals("BAN", ban.get("type").getAsString());
        assertEquals("swastika", ban.get("reason").getAsString());
        assertEquals("Builder", ban.get("operator").getAsString());
        assertEquals(issued.toString(), ban.get("issuedAt").getAsString());
        assertEquals(issued.plusSeconds(2592000).toString(), ban.get("expiresAt").getAsString());
        assertTrue(ban.get("active").getAsBoolean());
        JsonObject warning = items.get(1).getAsJsonObject();
        assertEquals("Console", warning.get("operator").getAsString());
        assertTrue(warning.get("expiresAt").isJsonNull());
        assertFalse(warning.get("active").getAsBoolean());
    }

    @Test
    void aPlayerWithoutPunishmentsHasAnEmptyHistory() throws Exception {
        String session = web.session();
        JsonObject punishments = json(web.get("/review/api/players/" + BUILDER + "/punishments", cookie(session)));
        assertEquals(0, punishments.getAsJsonArray("items").size());
        assertEquals(401, web.get("/review/api/players/" + BUILDER + "/punishments", Map.of()).statusCode());
        assertEquals("LibertyBans", json(web.get("/review/api/me", cookie(session))).get("punishments").getAsString());
    }

    @Test
    void unknownPlayerRoutesAreRejected() throws Exception {
        String session = web.session();
        assertEquals(400, web.get("/review/api/players/nobody", cookie(session)).statusCode());
        assertEquals(400, web.get("/review/api/players/nobody/other", cookie(session)).statusCode());
        assertEquals(404, web.get("/review/api/players/" + BUILDER + "/other", cookie(session)).statusCode());
        assertEquals(404, web.get("/review/api/players/nobody/a/b", cookie(session)).statusCode());
        assertEquals(404, web.get("/review/api/players", cookie(session)).statusCode());
        assertEquals(404, web.post("/review/api/players/nobody", "", authorized(session)).statusCode());
    }
}
