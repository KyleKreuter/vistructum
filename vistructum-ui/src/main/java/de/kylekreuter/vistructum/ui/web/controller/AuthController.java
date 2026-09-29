package de.kylekreuter.vistructum.ui.web.controller;

import de.kylekreuter.vistructum.api.WebAccess;
import de.kylekreuter.vistructum.api.WebSession;
import de.kylekreuter.vistructum.ui.web.GameServer;
import de.kylekreuter.vistructum.ui.web.Handoff;
import de.kylekreuter.vistructum.ui.web.Requests;
import de.kylekreuter.vistructum.ui.web.Responses;
import de.kylekreuter.vistructum.ui.web.WebSettings;
import de.kylekreuter.vistructum.ui.web.filter.Access;
import de.kylekreuter.vistructum.ui.web.filter.SessionFilter;
import de.kylekreuter.vistructum.ui.web.view.MeView;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class AuthController {

    private static final String EXPIRED = WebSettings.HOME + "?login=expired";
    private static final int COOKIE_SECONDS = WebAccess.SESSION_HOURS * 3600;

    private final WebAccess web;
    private final WebSettings settings;
    private final GameServer game;
    private final Handoff handoff;
    private final SecureRandom random = new SecureRandom();

    public AuthController(WebAccess web, WebSettings settings, GameServer game, Handoff handoff) {
        this.web = Objects.requireNonNull(web, "web");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.game = Objects.requireNonNull(game, "game");
        this.handoff = Objects.requireNonNull(handoff, "handoff");
    }

    public void register(JavalinConfig config) {
        config.routes.get(WebSettings.ROOT + "/login", this::login);
        config.routes.head(WebSettings.ROOT + "/login", ctx -> Responses.methodNotAllowed(ctx, "GET"));
        config.routes.addHttpHandler(HandlerType.POST, WebSettings.API + "logout", this::logout, Access.PUBLIC);
        Routes.read(config, WebSettings.API + "me", this::me);
    }

    private void login(Context ctx) {
        String next = Requests.param(ctx, "next").filter(AuthController::safeNext).orElse(WebSettings.HOME);
        Optional<String> token = Requests.param(ctx, "token");
        if (token.isEmpty()) {
            Responses.redirect(ctx, EXPIRED);
            return;
        }
        ctx.future(() -> handoff.off(web.redeemLogin(token.get())).thenAccept(issued -> {
            if (issued.isEmpty()) {
                Responses.redirect(ctx, EXPIRED);
                return;
            }
            Responses.redirect(ctx, next);
            Responses.cookie(ctx, cookie(Requests.SESSION_COOKIE, issued.get().token(), COOKIE_SECONDS, true));
            Responses.cookie(ctx, cookie(Requests.CSRF_COOKIE, csrfToken(), COOKIE_SECONDS, false));
        }));
    }

    private void logout(Context ctx) {
        CompletableFuture<Void> ended = Requests.cookie(ctx, Requests.SESSION_COOKIE)
                .map(token -> handoff.off(web.endSession(token)))
                .orElseGet(() -> CompletableFuture.completedFuture(null));
        ctx.future(() -> ended.thenRun(() -> {
            Responses.noContent(ctx);
            Responses.cookie(ctx, cookie(Requests.SESSION_COOKIE, "", 0, true));
            Responses.cookie(ctx, cookie(Requests.CSRF_COOKIE, "", 0, false));
        }));
    }

    private void me(Context ctx) {
        WebSession session = SessionFilter.session(ctx);
        ctx.future(() -> handoff.onMain(() -> game.canShare(session.player()))
                .thenAccept(canShare -> Responses.json(ctx, MeView.of(session, canShare))));
    }

    private String cookie(String name, String value, int maxAge, boolean httpOnly) {
        return name + "=" + value + "; Path=" + WebSettings.ROOT + "; Max-Age=" + maxAge
                + (httpOnly ? "; HttpOnly" : "") + "; SameSite=Strict" + (settings.secure() ? "; Secure" : "");
    }

    private String csrfToken() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static boolean safeNext(String next) {
        return next.startsWith(WebSettings.HOME) && !next.contains("//") && !next.contains("\\")
                && next.chars().allMatch(c -> c > 0x20 && c < 0x7F);
    }
}
