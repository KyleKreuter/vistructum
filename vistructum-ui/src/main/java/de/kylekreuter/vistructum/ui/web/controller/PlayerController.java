package de.kylekreuter.vistructum.ui.web.controller;

import de.kylekreuter.vistructum.api.FindingQuery;
import de.kylekreuter.vistructum.api.Findings;
import de.kylekreuter.vistructum.api.Players;
import de.kylekreuter.vistructum.api.ReviewState;
import de.kylekreuter.vistructum.api.Vistructum;
import de.kylekreuter.vistructum.ui.integration.punishment.Punishment;
import de.kylekreuter.vistructum.ui.integration.punishment.PunishmentLog;
import de.kylekreuter.vistructum.ui.web.Handoff;
import de.kylekreuter.vistructum.ui.web.PlayerNames;
import de.kylekreuter.vistructum.ui.web.Responses;
import de.kylekreuter.vistructum.ui.web.WebSettings;
import de.kylekreuter.vistructum.ui.web.assets.Pngs;
import de.kylekreuter.vistructum.ui.web.error.ApiError;
import de.kylekreuter.vistructum.ui.web.view.PlayerSummaryView;
import de.kylekreuter.vistructum.ui.web.view.PlayerView;
import de.kylekreuter.vistructum.ui.web.view.PunishmentsView;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public final class PlayerController {

    private static final String PLAYER = WebSettings.API + "players/{uuid}";
    private static final int FACE_SCALE = 8;
    private static final long PUNISHMENT_TIMEOUT_SECONDS = 10;

    private final Vistructum vistructum;
    private final PlayerNames names;
    private final Handoff handoff;
    private final Optional<PunishmentLog> punishments;

    public PlayerController(Vistructum vistructum, PlayerNames names, Handoff handoff,
                            Optional<PunishmentLog> punishments) {
        this.vistructum = Objects.requireNonNull(vistructum, "vistructum");
        this.names = Objects.requireNonNull(names, "names");
        this.handoff = Objects.requireNonNull(handoff, "handoff");
        this.punishments = Objects.requireNonNull(punishments, "punishments");
    }

    public void register(JavalinConfig config) {
        Routes.read(config, PLAYER, this::summary);
        Routes.read(config, PLAYER + "/skin.png", this::skin);
        Routes.read(config, PLAYER + "/face.png", this::face);
        Routes.read(config, PLAYER + "/punishments", this::punishments);
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

    private void punishments(Context ctx) {
        UUID player = player(ctx);
        PunishmentLog log = punishments.orElseThrow(ApiError::unavailable);
        ctx.future(() -> handoff.off(log.history(player).orTimeout(PUNISHMENT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .exceptionally(PlayerController::unavailableOnTimeout))
                .thenCompose(history -> {
                    List<Punishment> newest = history.stream().sorted(Punishment.NEWEST_FIRST)
                            .limit(PunishmentLog.LIMIT).toList();
                    Set<UUID> operators = newest.stream().filter(punishment -> punishment.operatorName().isEmpty())
                            .flatMap(punishment -> punishment.operatorId().stream()).collect(Collectors.toSet());
                    return names.of(operators).thenAccept(known ->
                            Responses.json(ctx, PunishmentsView.of(log.source(), newest, known)));
                }));
    }

    private static List<Punishment> unavailableOnTimeout(Throwable failure) {
        Throwable cause = failure instanceof CompletionException wrapped && wrapped.getCause() != null
                ? wrapped.getCause() : failure;
        if (cause instanceof TimeoutException) {
            throw ApiError.unavailable();
        }
        throw failure instanceof CompletionException completion ? completion : new CompletionException(failure);
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
