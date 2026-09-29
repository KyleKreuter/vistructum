package de.kylekreuter.vistructum.ui.web.controller;

import de.kylekreuter.vistructum.api.EvidenceShare;
import de.kylekreuter.vistructum.api.Vistructum;
import de.kylekreuter.vistructum.api.WebSession;
import de.kylekreuter.vistructum.ui.web.GameServer;
import de.kylekreuter.vistructum.ui.web.Handoff;
import de.kylekreuter.vistructum.ui.web.Responses;
import de.kylekreuter.vistructum.ui.web.WebSettings;
import de.kylekreuter.vistructum.ui.web.error.ApiError;
import de.kylekreuter.vistructum.ui.web.filter.SessionFilter;
import de.kylekreuter.vistructum.ui.web.view.ShareView;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class ShareController {

    private static final String SHARE = WebSettings.API + "findings/{id}/share";

    private final Vistructum vistructum;
    private final WebSettings settings;
    private final GameServer game;
    private final Handoff handoff;

    public ShareController(Vistructum vistructum, WebSettings settings, GameServer game, Handoff handoff) {
        this.vistructum = Objects.requireNonNull(vistructum, "vistructum");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.game = Objects.requireNonNull(game, "game");
        this.handoff = Objects.requireNonNull(handoff, "handoff");
    }

    public void register(JavalinConfig config) {
        config.routes.post(SHARE, this::share);
        config.routes.delete(SHARE, this::unshare);
    }

    private void share(Context ctx) {
        long id = FindingController.id(ctx);
        WebSession session = SessionFilter.session(ctx);
        ctx.future(() -> permittedExisting(id, session)
                .thenCompose(ignored -> handoff.off(vistructum.web().share(id, session.playerName())))
                .thenCompose(token -> {
                    token.orElseThrow(ApiError::notShareable);
                    return handoff.off(vistructum.web().shared(id));
                })
                .thenAccept(shared -> {
                    EvidenceShare active = shared.orElseThrow(ApiError::notShareable);
                    Responses.json(ctx, ShareView.of(settings.shareLink(active.token()), active.sharedSince()));
                }));
    }

    private void unshare(Context ctx) {
        long id = FindingController.id(ctx);
        WebSession session = SessionFilter.session(ctx);
        ctx.future(() -> permittedExisting(id, session)
                .thenCompose(ignored -> handoff.off(vistructum.web().unshare(id, session.playerName())))
                .thenRun(() -> Responses.noContent(ctx)));
    }

    private CompletableFuture<Void> permittedExisting(long id, WebSession session) {
        return handoff.onMain(() -> game.canShare(session.player())).thenCompose(allowed -> {
            if (!allowed) {
                throw ApiError.forbidden();
            }
            return handoff.off(vistructum.findings().get(id));
        }).thenAccept(found -> found.orElseThrow(ApiError::notFound));
    }
}
