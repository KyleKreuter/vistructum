package de.kylekreuter.vistructum.ui.web.controller;

import de.kylekreuter.vistructum.api.Vistructum;
import de.kylekreuter.vistructum.ui.web.Handoff;
import de.kylekreuter.vistructum.ui.web.Requests;
import de.kylekreuter.vistructum.ui.web.Responses;
import de.kylekreuter.vistructum.ui.web.WebSettings;
import de.kylekreuter.vistructum.ui.web.assets.GameAssets;
import de.kylekreuter.vistructum.ui.web.error.ApiError;
import de.kylekreuter.vistructum.ui.web.view.ActivityView;
import de.kylekreuter.vistructum.ui.web.view.StatsView;
import de.kylekreuter.vistructum.ui.web.view.StatusView;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class StatusController {

    static final int DEFAULT_ACTIVITY_PAGE_SIZE = 25;
    static final Duration DEFAULT_STATS_RANGE = Duration.ofDays(30);

    private final Vistructum vistructum;
    private final GameAssets assets;
    private final Handoff handoff;
    private final Clock clock;

    public StatusController(Vistructum vistructum, GameAssets assets, Handoff handoff, Clock clock) {
        this.vistructum = Objects.requireNonNull(vistructum, "vistructum");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.handoff = Objects.requireNonNull(handoff, "handoff");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void register(JavalinConfig config) {
        Routes.read(config, WebSettings.API + "status", this::status);
        Routes.read(config, WebSettings.API + "stats", this::stats);
        Routes.read(config, WebSettings.API + "activity", this::activity);
    }

    private void status(Context ctx) {
        ctx.future(() -> handoff.off(vistructum.status())
                .thenAccept(status -> Responses.json(ctx, StatusView.of(status, assets))));
    }

    private void stats(Context ctx) {
        Instant to = Requests.param(ctx, "to").map(Params::instant).orElseGet(clock::instant);
        Instant from = Requests.param(ctx, "from").map(Params::instant).orElseGet(() -> to.minus(DEFAULT_STATS_RANGE));
        if (to.isBefore(from)) {
            throw ApiError.badRequest();
        }
        ctx.future(() -> handoff.off(vistructum.findings().stats(from, to))
                .thenCombine(handoff.off(vistructum.findings().precision()),
                        (stats, precision) -> StatsView.of(stats, precision))
                .thenAccept(view -> Responses.json(ctx, view)));
    }

    private void activity(Context ctx) {
        int pageSize = Requests.param(ctx, "pageSize").map(Params::limit).orElse(DEFAULT_ACTIVITY_PAGE_SIZE);
        int page = Requests.param(ctx, "page").map(value -> Params.page(value, pageSize)).orElse(1);
        CompletableFuture<Long> total = handoff.off(vistructum.findings().activityCount());
        ctx.future(() -> handoff.off(vistructum.findings().activity((page - 1) * pageSize, pageSize))
                .thenCombine(total, (items, count) -> ActivityView.of(items, count, page, pageSize))
                .thenAccept(view -> Responses.json(ctx, view)));
    }
}
