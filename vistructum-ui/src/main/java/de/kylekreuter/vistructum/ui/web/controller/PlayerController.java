package de.kylekreuter.vistructum.ui.web.controller;

import de.kylekreuter.vistructum.api.FindingQuery;
import de.kylekreuter.vistructum.api.Findings;
import de.kylekreuter.vistructum.api.Players;
import de.kylekreuter.vistructum.api.ReviewState;
import de.kylekreuter.vistructum.api.Vistructum;
import de.kylekreuter.vistructum.ui.web.Handoff;
import de.kylekreuter.vistructum.ui.web.PlayerNames;
import de.kylekreuter.vistructum.ui.web.Responses;
import de.kylekreuter.vistructum.ui.web.WebSettings;
import de.kylekreuter.vistructum.ui.web.assets.Pngs;
import de.kylekreuter.vistructum.ui.web.error.ApiError;
import de.kylekreuter.vistructum.ui.web.view.PlayerSummaryView;
import de.kylekreuter.vistructum.ui.web.view.PlayerView;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PlayerController {

    private static final String PLAYER = WebSettings.API + "players/{uuid}";
    private static final int FACE_SCALE = 8;

    private final Vistructum vistructum;
    private final PlayerNames names;
    private final Handoff handoff;

    public PlayerController(Vistructum vistructum, PlayerNames names, Handoff handoff) {
        this.vistructum = Objects.requireNonNull(vistructum, "vistructum");
        this.names = Objects.requireNonNull(names, "names");
        this.handoff = Objects.requireNonNull(handoff, "handoff");
    }

    public void register(JavalinConfig config) {
        Routes.read(config, PLAYER, this::summary);
        Routes.read(config, PLAYER + "/skin.png", this::skin);
        Routes.read(config, PLAYER + "/face.png", this::face);
        Routes.read(config, PLAYER + "/{part}", ctx -> {
            player(ctx);
            throw ApiError.notFound();
        });
    }

    private void summary(Context ctx) {
        UUID player = player(ctx);
        Findings findings = vistructum.findings();
        FindingQuery all = FindingQuery.all().player(player);
        CompletableFuture<Long> total = handoff.off(findings.count(all));
        CompletableFuture<Long> open = handoff.off(findings.count(all.state(ReviewState.OPEN)));
        CompletableFuture<Long> confirmed = handoff.off(findings.count(all.state(ReviewState.CONFIRMED)));
        CompletableFuture<Long> falseAlarms = handoff.off(findings.count(all.state(ReviewState.FALSE_ALARM)));
        CompletableFuture<Map<UUID, Optional<String>>> known = names.of(Set.of(player));
        ctx.future(() -> CompletableFuture.allOf(total, open, confirmed, falseAlarms, known).thenAccept(ignored ->
                Responses.json(ctx, PlayerSummaryView.of(PlayerView.of(Set.of(player), known.join()).getFirst(),
                        new PlayerSummaryView.CountsView(total.join(), open.join(), confirmed.join(),
                                falseAlarms.join())))));
    }

    private void skin(Context ctx) {
        UUID player = player(ctx);
        ctx.future(() -> skin(ctx, vistructum.players(), handoff, player));
    }

    private void face(Context ctx) {
        UUID player = player(ctx);
        ctx.future(() -> handoff.off(vistructum.players().face(player)).thenAccept(face -> {
            if (!face.hasSkin()) {
                throw ApiError.notFound();
            }
            int[] rgb = face.pixels().stream().mapToInt(Integer::intValue).toArray();
            Responses.png(ctx, Pngs.scaled(8, 8, rgb, FACE_SCALE));
        }));
    }

    static CompletableFuture<Void> skin(Context ctx, Players players, Handoff handoff, UUID player) {
        return handoff.off(players.skin(player)).thenAccept(found -> {
            var skin = found.orElseThrow(ApiError::notFound);
            Responses.png(ctx, skin.png());
            ctx.header("X-Skin-Model", skin.slim() ? "slim" : "classic");
        });
    }

    private static UUID player(Context ctx) {
        return Params.uuid(ctx.pathParam("uuid"));
    }
}
