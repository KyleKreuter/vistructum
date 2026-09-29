package de.kylekreuter.vistructum.ui.web.controller;

import de.kylekreuter.vistructum.ui.web.WebFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static de.kylekreuter.vistructum.ui.web.WebFixture.STAFF;
import static de.kylekreuter.vistructum.ui.web.WebFixture.authorized;
import static de.kylekreuter.vistructum.ui.web.WebFixture.cookie;
import static de.kylekreuter.vistructum.ui.web.WebFixture.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthControllerTest {

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
    void privateEndpointsNeedASession() throws Exception {
        HttpResponse<String> response = web.get("/review/api/me", Map.of());
        assertEquals(401, response.statusCode());
        assertEquals("unauthorized", json(response).get("error").getAsString());
        assertEquals("application/json;charset=utf-8", response.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElseThrow());
        assertEquals(401, web.get("/review/api/findings", cookie("forged")).statusCode());
        assertEquals(401, web.get("/review/api/unknown", Map.of()).statusCode());
        assertEquals(401, web.post("/review/api/findings/8/verdict", "{}", Map.of()).statusCode());
    }

    @Test
    void loginSetsTheCookiesAndRedirectsToTheRequestedPage() throws Exception {
        String login = web.vistructum.issueLogin(STAFF, "Staff").join();
        HttpResponse<String> response = web.get("/review/login?token=" + login + "&next=/review/findings/7/evidence",
                Map.of());
        assertEquals(302, response.statusCode());
        assertEquals("/review/findings/7/evidence", response.headers().firstValue("Location").orElseThrow());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals("no-referrer", response.headers().firstValue("Referrer-Policy").orElseThrow());
        assertEquals("", response.body());
        List<String> cookies = response.headers().allValues("Set-Cookie");
        assertEquals(2, cookies.size());
        String session = cookies.stream().filter(cookie -> cookie.startsWith("vistructum_session=")).findFirst()
                .orElseThrow();
        String csrf = cookies.stream().filter(cookie -> cookie.startsWith("vistructum_csrf=")).findFirst().orElseThrow();
        assertTrue(session.contains("HttpOnly") && session.contains("SameSite=Strict") && session.contains("Path=/review")
                && session.contains("Max-Age=43200"), session);
        assertFalse(session.contains("Expires"), session);
        assertFalse(csrf.contains("HttpOnly"), csrf);
        assertTrue(session.contains("Secure") && csrf.contains("Secure"), session);
        String token = session.substring("vistructum_session=".length(), session.indexOf(';'));
        HttpResponse<String> me = web.get("/review/api/me", cookie(token));
        assertEquals(200, me.statusCode());
        assertEquals("Staff", json(me).get("name").getAsString());
        assertEquals(STAFF.toString(), json(me).get("player").getAsString());
        assertFalse(json(me).get("canShare").getAsBoolean());
    }

    @Test
    void usedOrUnknownLoginTokensAndForeignTargetsAreRejected() throws Exception {
        String login = web.vistructum.issueLogin(STAFF, "Staff").join();
        HttpResponse<String> probe = web.head("/review/login?token=" + login, Map.of());
        assertEquals(405, probe.statusCode());
        assertEquals("GET", probe.headers().firstValue("Allow").orElseThrow());
        HttpResponse<String> posted = web.post("/review/login?token=" + login, "", Map.of());
        assertEquals(405, posted.statusCode());
        assertEquals("GET, HEAD", posted.headers().firstValue("Allow").orElseThrow());
        HttpResponse<String> first = web.get("/review/login?token=" + login + "&next=//evil.example/", Map.of());
        assertEquals("/review/", first.headers().firstValue("Location").orElseThrow());
        HttpResponse<String> again = web.get("/review/login?token=" + login, Map.of());
        assertEquals(302, again.statusCode());
        assertEquals("/review/?login=expired", again.headers().firstValue("Location").orElseThrow());
        assertTrue(again.headers().allValues("Set-Cookie").isEmpty());
        assertEquals("/review/?login=expired", web.get("/review/login", Map.of()).headers().firstValue("Location")
                .orElseThrow());
    }

    @Test
    void logoutNeedsTheCsrfHeaderAndClearsBothCookies() throws Exception {
        String session = web.session();
        HttpResponse<String> missing = web.post("/review/api/logout", "", cookie(session));
        assertEquals(403, missing.statusCode());
        assertEquals("csrf", json(missing).get("error").getAsString());
        HttpResponse<String> response = web.post("/review/api/logout", "", authorized(session));
        assertEquals(204, response.statusCode());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("text/plain"));
        List<String> cookies = response.headers().allValues("Set-Cookie");
        assertEquals(2, cookies.size());
        assertTrue(cookies.stream().allMatch(cookie -> cookie.contains("Max-Age=0")), cookies.toString());
        assertEquals(401, web.get("/review/api/me", cookie(session)).statusCode());
    }

    @Test
    void wrongMethodsBelowTheApiAreNotFound() throws Exception {
        String session = web.session();
        HttpResponse<String> response = web.method("DELETE", "/review/api/me", authorized(session));
        assertEquals(404, response.statusCode());
        assertEquals("not_found", json(response).get("error").getAsString());
        assertEquals(404, web.post("/review/api/status", "", authorized(session)).statusCode());
        assertEquals(403, web.post("/review/api/status", "", cookie(session)).statusCode());
        assertEquals(404, web.get("/review/api/logout", cookie(session)).statusCode());
    }

    @Test
    void oversizedBodiesAreRejected() throws Exception {
        String session = web.session();
        HttpResponse<String> response = web.post("/review/api/findings/8/verdict", "x".repeat(16385),
                authorized(session));
        assertEquals(400, response.statusCode());
        assertEquals("bad_request", json(response).get("error").getAsString());
    }
}
