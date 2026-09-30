package de.kylekreuter.vistructum.ui.web.controller;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import de.kylekreuter.vistructum.api.Activity;
import de.kylekreuter.vistructum.api.EvidenceShare;
import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.FindingQuery;
import de.kylekreuter.vistructum.api.ReviewState;
import de.kylekreuter.vistructum.api.Source;
import de.kylekreuter.vistructum.api.Verdict;
import de.kylekreuter.vistructum.api.Vistructum;
import de.kylekreuter.vistructum.api.WebSession;
import de.kylekreuter.vistructum.ui.web.GameServer;
import de.kylekreuter.vistructum.ui.web.Handoff;
import de.kylekreuter.vistructum.ui.web.PlayerNames;
import de.kylekreuter.vistructum.ui.web.Requests;
import de.kylekreuter.vistructum.ui.web.Responses;
import de.kylekreuter.vistructum.ui.web.WebSettings;
import de.kylekreuter.vistructum.ui.web.assets.Pngs;
import de.kylekreuter.vistructum.ui.web.error.ApiError;
import de.kylekreuter.vistructum.ui.web.filter.SessionFilter;
import de.kylekreuter.vistructum.ui.web.view.EvidenceView;
import de.kylekreuter.vistructum.ui.web.view.FindingPageView;
import de.kylekreuter.vistructum.ui.web.view.FindingView;
import de.kylekreuter.vistructum.ui.web.view.HeatmapView;
import de.kylekreuter.vistructum.ui.web.view.SceneView;
import de.kylekreuter.vistructum.ui.web.view.ShareView;
import de.kylekreuter.vistructum.ui.web.view.TerrainView;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class FindingController {

    static final int DEFAULT_PAGE_SIZE = 25;

    private static final String FINDINGS = WebSettings.API + "findings";
    private static final String FINDING = FINDINGS + "/{id}";
    private static final int THUMBNAIL_SCALE = 4;

    private final Vistructum vistructum;
    private final WebSettings settings;
    private final GameServer game;
    private final PlayerNames names;
    private final Handoff handoff;

    public FindingController(Vistructum vistructum, WebSettings settings, GameServer game, PlayerNames names,
                             Handoff handoff) {
        this.vistructum = Objects.requireNonNull(vistructum, "vistructum");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.game = Objects.requireNonNull(game, "game");
        this.names = Objects.requireNonNull(names, "names");
        this.handoff = Objects.requireNonNull(handoff, "handoff");
    }

    public void register(JavalinConfig config) {
        Routes.read(config, FINDINGS, this::list);
        Routes.read(config, FINDING, this::detail);
        Routes.read(config, FINDING + "/thumbnail.png", this::thumbnail);
        Routes.read(config, FINDING + "/scene", this::scene);
        Routes.read(config, FINDING + "/heatmap", this::heatmap);
        Routes.read(config, FINDING + "/evidence", this::evidence);
        Routes.read(config, FINDING + "/terrain", this::terrain);
        config.routes.post(FINDING + "/verdict", this::verdict);
        Routes.writing(config, FINDING, FindingController::unknown);
        Routes.any(config, FINDING + "/<rest>", FindingController::unknown);
    }

    private void list(Context ctx) {
        int pageSize = Requests.param(ctx, "pageSize").map(Params::limit).orElse(DEFAULT_PAGE_SIZE);
        int page = Requests.param(ctx, "page").map(value -> pageNumber(value, pageSize)).orElse(1);
        FindingQuery query = query(ctx).limit(pageSize).offset((page - 1) * pageSize);
        CompletableFuture<Long> total = handoff.off(vistructum.findings().count(query));
        ctx.future(() -> handoff.off(vistructum.findings().find(query)).thenCompose(found -> {
            List<CompletableFuture<FindingView>> items = found.items().stream().map(this::summary).toList();
            return CompletableFuture.allOf(items.toArray(CompletableFuture[]::new)).thenCombine(total, (ignored, count) ->
                    new FindingPageView(items.stream().map(CompletableFuture::join).toList(), count, page, pageSize));
        }).thenAccept(view -> Responses.json(ctx, view)));
    }

    private void detail(Context ctx) {
        long id = id(ctx);
        ctx.future(() -> existing(id).thenCompose(finding -> summary(finding)
                        .thenCombine(handoff.onMain(() -> game.dimension(finding.world())),
                                (view, dimension) -> view.withTeleport(finding.box(), dimension)))
                .thenAccept(view -> Responses.json(ctx, view)));
    }

    private void thumbnail(Context ctx) {
        long id = id(ctx);
        ctx.future(() -> handoff.off(vistructum.findings().thumbnail(id)).thenAccept(found -> {
            var thumbnail = found.orElseThrow(ApiError::notFound);
            Responses.png(ctx, Pngs.scaled(thumbnail.width(), thumbnail.height(), thumbnail.pixels(),
                    THUMBNAIL_SCALE));
        }));
    }

    private void scene(Context ctx) {
        long id = id(ctx);
        ctx.future(() -> handoff.off(vistructum.findings().scene(id)).thenAccept(found ->
                Responses.json(ctx, SceneView.of(found.orElseThrow(ApiError::notFound)))));
    }

    private void heatmap(Context ctx) {
        long id = id(ctx);
        ctx.future(() -> existing(id).thenCompose(finding -> handoff.off(vistructum.findings().heatmap(id))
                .handle((heatmap, error) -> {
                    if (error != null || heatmap.isEmpty()) {
                        throw ApiError.unavailable();
                    }
                    return HeatmapView.of(heatmap.get());
                })).thenAccept(view -> Responses.json(ctx, view)));
    }

    private void evidence(Context ctx) {
        long id = id(ctx);
        ctx.future(() -> handoff.off(vistructum.findings().evidence(id)).thenAccept(found ->
                Responses.json(ctx, EvidenceView.of(found.orElseThrow(ApiError::notFound)))));
    }

    private void terrain(Context ctx) {
        long id = id(ctx);
        ctx.future(() -> handoff.off(vistructum.findings().terrain(id)).thenAccept(found ->
                Responses.json(ctx, TerrainView.of(found.orElseThrow(ApiError::notFound)))));
    }

    private void verdict(Context ctx) {
        long id = id(ctx);
        Verdict verdict = verdictOf(body(ctx));
        WebSession session = SessionFilter.session(ctx);
        ctx.future(() -> handoff.off(vistructum.findings().review(id, verdict, session.playerName()))
                .thenCompose(found -> summary(found.orElseThrow(ApiError::notFound)))
                .thenAccept(view -> Responses.json(ctx, view)));
    }

    private static void unknown(Context ctx) {
        id(ctx);
        throw ApiError.notFound();
    }

    CompletableFuture<FindingView> summary(Finding finding) {
        CompletableFuture<Boolean> evidence = handoff.off(vistructum.findings().hasEvidence(finding.id()));
        CompletableFuture<Boolean> terrain = handoff.off(vistructum.findings().hasTerrain(finding.id()));
        CompletableFuture<Optional<EvidenceShare>> shared = handoff.off(vistructum.web().shared(finding.id()));
        CompletableFuture<Optional<Activity>> rollback = handoff.off(vistructum.blockLog().lastRollback(finding.id()));
        CompletableFuture<Map<UUID, Optional<String>>> players = names.of(finding.players());
        return CompletableFuture.allOf(evidence, terrain, shared, rollback, players).thenApply(ignored ->
                FindingView.of(finding, players.join(), evidence.join(), terrain.join(), shared.join()
                        .map(link -> ShareView.of(settings.shareLink(link.token()), link.sharedSince())),
                        rollback.join()));
    }

    private CompletableFuture<Finding> existing(long id) {
        return handoff.off(vistructum.findings().get(id)).thenApply(found -> found.orElseThrow(ApiError::notFound));
    }

    static long id(Context ctx) {
        return Params.id(ctx.pathParam("id"));
    }

    private static FindingQuery query(Context ctx) {
        FindingQuery query = FindingQuery.all().state(Requests.param(ctx, "state").map(FindingController::state)
                .orElse(ReviewState.ANY));
        Optional<String> world = Requests.param(ctx, "world");
        if (world.isPresent()) {
            query = query.world(world.get());
        }
        Optional<String> source = Requests.param(ctx, "source");
        if (source.isPresent()) {
            query = query.source(source(source.get()));
        }
        Optional<String> player = Requests.param(ctx, "player");
        if (player.isPresent()) {
            query = query.player(Params.uuid(player.get()));
        }
        Optional<String> since = Requests.param(ctx, "since");
        if (since.isPresent()) {
            query = query.since(Params.instant(since.get()));
        }
        return query;
    }

    private static int pageNumber(String value, int pageSize) {
        long page = Params.id(value);
        if (page < 1 || (page - 1) * pageSize > Integer.MAX_VALUE) {
            throw ApiError.badRequest();
        }
        return (int) page;
    }

    private static ReviewState state(String value) {
        return Arrays.stream(ReviewState.values()).filter(state -> state.name().toLowerCase(Locale.ROOT).equals(value))
                .findFirst().orElseThrow(ApiError::badRequest);
    }

    private static Source source(String value) {
        return Arrays.stream(Source.values()).filter(source -> source.modelKind().equals(value)).findFirst()
                .orElseThrow(ApiError::badRequest);
    }

    private static byte[] body(Context ctx) {
        try (InputStream in = ctx.req().getInputStream()) {
            byte[] body = in.readNBytes(Requests.MAX_BODY + 1);
            if (body.length > Requests.MAX_BODY) {
                throw ApiError.badRequest();
            }
            return body;
        } catch (IOException e) {
            throw ApiError.badRequest();
        }
    }

    private static Verdict verdictOf(byte[] body) {
        try {
            String verdict = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject()
                    .get("verdict").getAsString();
            return Verdict.valueOf(verdict);
        } catch (JsonParseException | IllegalStateException | IllegalArgumentException | NullPointerException
                 | UnsupportedOperationException e) {
            throw ApiError.badRequest();
        }
    }
}
