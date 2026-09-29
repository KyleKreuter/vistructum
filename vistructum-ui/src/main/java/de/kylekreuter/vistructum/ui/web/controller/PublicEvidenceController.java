package de.kylekreuter.vistructum.ui.web.controller;

import de.kylekreuter.vistructum.api.BlockEvent;
import de.kylekreuter.vistructum.api.Evidence;
import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.Recording;
import de.kylekreuter.vistructum.api.Vistructum;
import de.kylekreuter.vistructum.ui.web.Handoff;
import de.kylekreuter.vistructum.ui.web.PlayerNames;
import de.kylekreuter.vistructum.ui.web.Responses;
import de.kylekreuter.vistructum.ui.web.WebSettings;
import de.kylekreuter.vistructum.ui.web.error.ApiError;
import de.kylekreuter.vistructum.ui.web.filter.Access;
import de.kylekreuter.vistructum.ui.web.view.PublicEvidenceView;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PublicEvidenceController {

    private static final String PUBLIC = WebSettings.API + "public";
    private static final String SKIN_SUFFIX = ".png";

    private final Vistructum vistructum;
    private final PlayerNames names;
    private final Handoff handoff;

    public PublicEvidenceController(Vistructum vistructum, PlayerNames names, Handoff handoff) {
        this.vistructum = Objects.requireNonNull(vistructum, "vistructum");
        this.names = Objects.requireNonNull(names, "names");
        this.handoff = Objects.requireNonNull(handoff, "handoff");
    }

    public void register(JavalinConfig config) {
        Routes.read(config, PUBLIC + "/{token}", this::evidence, Access.PUBLIC);
        Routes.read(config, PUBLIC + "/{token}/skins/{file}", this::skin, Access.PUBLIC);
        Routes.read(config, PUBLIC + "/*", ctx -> {
            throw ApiError.notFound();
        }, Access.PUBLIC);
    }

    private void evidence(Context ctx) {
        String token = ctx.pathParam("token");
        ctx.future(() -> shared(token).thenCompose(shared -> names.of(shared.finding().players())
                .thenAccept(known -> Responses.json(ctx,
                        PublicEvidenceView.of(shared.finding(), shared.evidence(), known)))));
    }

    private void skin(Context ctx) {
        String file = ctx.pathParam("file");
        if (!file.endsWith(SKIN_SUFFIX)) {
            throw ApiError.notFound();
        }
        UUID player = Params.uuid(file.substring(0, file.length() - SKIN_SUFFIX.length()));
        String token = ctx.pathParam("token");
        ctx.future(() -> shared(token).thenCompose(shared -> {
            if (!participants(shared.evidence()).contains(player)) {
                throw ApiError.notFound();
            }
            return PlayerController.skin(ctx, vistructum.players(), handoff, player);
        }));
    }

    private CompletableFuture<Shared> shared(String token) {
        return handoff.off(vistructum.web().sharedFinding(token)).thenCompose(found -> {
            long id = found.orElseThrow(ApiError::notFound);
            CompletableFuture<Optional<Evidence>> evidence = handoff.off(vistructum.findings().evidence(id));
            return handoff.off(vistructum.findings().get(id)).thenCombine(evidence, (finding, secured) ->
                    new Shared(finding.orElseThrow(ApiError::notFound), secured.orElseThrow(ApiError::notFound)));
        });
    }

    private static Set<UUID> participants(Evidence evidence) {
        Set<UUID> players = new HashSet<>();
        evidence.changes().stream().map(BlockEvent::player).forEach(players::add);
        evidence.recordings().stream().map(Recording::player).forEach(players::add);
        return players;
    }

    private record Shared(Finding finding, Evidence evidence) {
    }
}
