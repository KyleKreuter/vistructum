package de.kylekreuter.vistructum.ui.web.controller;

import de.kylekreuter.vistructum.api.BlockLog;
import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.Review;
import de.kylekreuter.vistructum.api.Verdict;
import de.kylekreuter.vistructum.api.Vistructum;
import de.kylekreuter.vistructum.api.WebSession;
import de.kylekreuter.vistructum.ui.web.GameServer;
import de.kylekreuter.vistructum.ui.web.Handoff;
import de.kylekreuter.vistructum.ui.web.Responses;
import de.kylekreuter.vistructum.ui.web.WebSettings;
import de.kylekreuter.vistructum.ui.web.error.ApiError;
import de.kylekreuter.vistructum.ui.web.filter.SessionFilter;
import de.kylekreuter.vistructum.ui.web.view.RollbackView;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class BlockLogController {

    private static final String ATTRIBUTE = WebSettings.API + "findings/{id}/attribute";
    private static final String ROLLBACK = WebSettings.API + "findings/{id}/rollback";

    private final Vistructum vistructum;
    private final GameServer game;
    private final Handoff handoff;
    private final FindingController findings;

    public BlockLogController(Vistructum vistructum, GameServer game, Handoff handoff, FindingController findings) {
        this.vistructum = Objects.requireNonNull(vistructum, "vistructum");
        this.game = Objects.requireNonNull(game, "game");
        this.handoff = Objects.requireNonNull(handoff, "handoff");
        this.findings = Objects.requireNonNull(findings, "findings");
    }

    public void register(JavalinConfig config) {
        config.routes.post(ATTRIBUTE, this::attribute);
        config.routes.post(ROLLBACK, this::rollback);
    }

    private void attribute(Context ctx) {
        long id = FindingController.id(ctx);
        WebSession session = SessionFilter.session(ctx);
        BlockLog blockLog = available();
        ctx.future(() -> handoff.off(blockLog.attribute(id, session.playerName()))
                .thenCompose(found -> findings.summary(found.orElseThrow(ApiError::notFound)))
                .thenAccept(view -> Responses.json(ctx, view)));
    }

    private void rollback(Context ctx) {
        long id = FindingController.id(ctx);
        WebSession session = SessionFilter.session(ctx);
        BlockLog blockLog = available();
        ctx.future(() -> rollbackable(id, session)
                .thenCompose(ignored -> handoff.off(blockLog.rollback(id, session.playerName())))
                .thenAccept(changes -> Responses.json(ctx,
                        RollbackView.of(changes.orElseThrow(ApiError::notFound)))));
    }

    private BlockLog available() {
        BlockLog blockLog = vistructum.blockLog();
        if (!blockLog.available()) {
            throw ApiError.unavailable();
        }
        return blockLog;
    }

    private CompletableFuture<Void> rollbackable(long id, WebSession session) {
        return handoff.onMain(() -> game.canRollback(session.player())).thenCompose(allowed -> {
            if (!allowed) {
                throw ApiError.forbidden();
            }
            return handoff.off(vistructum.findings().get(id));
        }).thenAccept(found -> {
            Finding finding = found.orElseThrow(ApiError::notFound);
            if (!confirmed(finding) || finding.players().isEmpty()) {
                throw ApiError.notRollbackable();
            }
        });
    }

    private static boolean confirmed(Finding finding) {
        return finding.review().map(Review::verdict).filter(Verdict.CONFIRMED::equals).isPresent();
    }
}
