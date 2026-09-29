package de.kylekreuter.vistructum.ui.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import de.kylekreuter.vistructum.api.BlockEvent;
import de.kylekreuter.vistructum.api.BlockVolume;
import de.kylekreuter.vistructum.api.Evidence;
import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.FindingQuery;
import de.kylekreuter.vistructum.api.Findings;
import de.kylekreuter.vistructum.api.Recording;
import de.kylekreuter.vistructum.api.ReviewState;
import de.kylekreuter.vistructum.api.Source;
import de.kylekreuter.vistructum.api.Verdict;
import de.kylekreuter.vistructum.api.Vistructum;
import de.kylekreuter.vistructum.api.WebAccess;
import de.kylekreuter.vistructum.api.WebSession;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

final class ReviewApi {

    static final String SESSION_COOKIE = "vistructum_session";
    static final String CSRF_COOKIE = "vistructum_csrf";
    static final String API = WebSettings.ROOT + "/api/";
    static final int MAX_LIMIT = 100;
    static final int DEFAULT_ACTIVITY_LIMIT = 50;
    static final Duration DEFAULT_STATS_RANGE = Duration.ofDays(30);

    private static final String EXPIRED = WebSettings.HOME + "?login=expired";
    private static final int COOKIE_SECONDS = WebAccess.SESSION_HOURS * 3600;
    private static final int THUMBNAIL_SCALE = 4;
    private static final int FACE_SCALE = 8;

    private final Vistructum vistructum;
    private final WebSettings settings;
    private final GameServer game;
    private final Executor mainThread;
    private final Executor worker;
    private final Clock clock;
    private final Reply palette;
    private final SecureRandom random = new SecureRandom();

    ReviewApi(Vistructum vistructum, WebSettings settings, GameServer game, Map<String, Integer> palette,
              Executor mainThread, Executor worker, Clock clock) {
        this.vistructum = Objects.requireNonNull(vistructum, "vistructum");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.game = Objects.requireNonNull(game, "game");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.worker = Objects.requireNonNull(worker, "worker");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.palette = Reply.json(Views.palette(palette)).with("Cache-Control", "public, max-age=86400");
    }

    CompletableFuture<Reply> login(Request request) {
        String next = request.param("next").filter(ReviewApi::safeNext).orElse(WebSettings.HOME);
        Optional<String> token = request.param("token");
        if (token.isEmpty()) {
            return CompletableFuture.completedFuture(Reply.redirect(EXPIRED));
        }
        return off(vistructum.web().redeemLogin(token.get())).thenApply(issued -> issued
                .map(session -> Reply.redirect(next)
                        .with("Set-Cookie", cookie(SESSION_COOKIE, session.token(), COOKIE_SECONDS, true))
                        .with("Set-Cookie", cookie(CSRF_COOKIE, csrfToken(), COOKIE_SECONDS, false)))
                .orElseGet(() -> Reply.redirect(EXPIRED)));
    }

    CompletableFuture<Reply> route(Request request) {
        List<String> path = segments(request.path());
        if (path.equals(List.of("palette")) && request.get()) {
            return CompletableFuture.completedFuture(palette);
        }
        if (path.size() >= 2 && path.getFirst().equals("public") && request.get()) {
            return publicRoute(path);
        }
        if (path.equals(List.of("logout")) && request.method().equals("POST")) {
            return logout(request);
        }
        return session(request).thenCompose(session -> {
            if (!request.get()) {
                requireCsrf(request);
            }
            return privateRoute(request, session, path);
        });
    }

    private CompletableFuture<Reply> privateRoute(Request request, WebSession session, List<String> path) {
        String head = path.isEmpty() ? "" : path.getFirst();
        boolean get = request.get();
        return switch (head) {
            case "me" -> only(path.size() == 1 && get, () -> me(session));
            case "status" -> only(path.size() == 1 && get, this::status);
            case "findings" -> path.size() == 1 ? only(get, () -> findings(request)) : finding(request, session, path);
            case "stats" -> only(path.size() == 1 && get, () -> stats(request));
            case "activity" -> only(path.size() == 1 && get, () -> activity(request));
            case "players" -> player(request, path);
            default -> CompletableFuture.failedFuture(ApiError.notFound());
        };
    }

    private CompletableFuture<Reply> finding(Request request, WebSession session, List<String> path) {
        long id = id(path.get(1));
        String method = request.method();
        boolean get = request.get();
        if (path.size() == 2) {
            return only(get, () -> detail(id));
        }
        if (path.size() != 3) {
            return CompletableFuture.failedFuture(ApiError.notFound());
        }
        return switch (path.get(2)) {
            case "thumbnail.png" -> only(get, () -> thumbnail(id));
            case "scene" -> only(get, () -> scene(id));
            case "heatmap" -> only(get, () -> heatmap(id));
            case "evidence" -> only(get, () -> evidence(id));
            case "verdict" -> only(method.equals("POST"), () -> verdict(id, request, session));
            case "share" -> method.equals("POST") ? share(id, session)
                    : only(method.equals("DELETE"), () -> unshare(id, session));
            default -> CompletableFuture.failedFuture(ApiError.notFound());
        };
    }

    private CompletableFuture<Reply> player(Request request, List<String> path) {
        if (path.size() < 2 || path.size() > 3 || !request.get()) {
            return CompletableFuture.failedFuture(ApiError.notFound());
        }
        UUID player = uuid(path.get(1));
        if (path.size() == 2) {
            return playerSummary(player);
        }
        return switch (path.get(2)) {
            case "skin.png" -> skin(player);
            case "face.png" -> face(player);
            default -> CompletableFuture.failedFuture(ApiError.notFound());
        };
    }

    private CompletableFuture<Reply> publicRoute(List<String> path) {
        String token = path.get(1);
        if (path.size() == 2) {
            return publicEvidence(token);
        }
        if (path.size() == 4 && path.get(2).equals("skins") && path.get(3).endsWith(".png")) {
            String name = path.get(3);
            return publicSkin(token, uuid(name.substring(0, name.length() - ".png".length())));
        }
        return CompletableFuture.failedFuture(ApiError.notFound());
    }

    private CompletableFuture<Reply> me(WebSession session) {
        return onMain(() -> game.canShare(session.player())).thenApply(canShare -> Reply.json(Views.me(session, canShare)));
    }

    private CompletableFuture<Reply> logout(Request request) {
        requireCsrf(request);
        CompletableFuture<Void> ended = request.cookie(SESSION_COOKIE)
                .map(token -> off(vistructum.web().endSession(token)))
                .orElseGet(() -> CompletableFuture.completedFuture(null));
        return ended.thenApply(ignored -> Reply.noContent()
                .with("Set-Cookie", cookie(SESSION_COOKIE, "", 0, true))
                .with("Set-Cookie", cookie(CSRF_COOKIE, "", 0, false)));
    }

    private CompletableFuture<Reply> status() {
        return off(vistructum.status()).thenApply(status -> Reply.json(Views.status(status)));
    }

    private CompletableFuture<Reply> findings(Request request) {
        FindingQuery query = findingQuery(request);
        CompletableFuture<Long> total = off(vistructum.findings().count(query));
        return off(vistructum.findings().find(query)).thenCompose(page -> {
            List<CompletableFuture<JsonObject>> items = page.items().stream().map(this::summary).toList();
            return CompletableFuture.allOf(items.toArray(CompletableFuture[]::new)).thenCombine(total, (ignored, count) -> {
                JsonObject json = new JsonObject();
                JsonArray array = new JsonArray(items.size());
                items.forEach(item -> array.add(item.join()));
                json.add("items", array);
                json.add("nextBefore", page.hasNext() && !page.items().isEmpty()
                        ? new JsonPrimitive(page.items().getLast().id()) : JsonNull.INSTANCE);
                json.addProperty("total", count);
                return Reply.json(json);
            });
        });
    }

    private CompletableFuture<Reply> detail(long id) {
        return existing(id).thenCompose(finding -> summary(finding)
                .thenCombine(onMain(() -> game.dimension(finding.world())), (json, dimension) -> {
                    json.addProperty("teleport", Views.teleport(finding.box(), dimension));
                    return Reply.json(json);
                }));
    }

    private CompletableFuture<Reply> thumbnail(long id) {
        return off(vistructum.findings().thumbnail(id)).thenApply(found -> found
                .map(thumbnail -> Reply.png(Pngs.scaled(thumbnail.width(), thumbnail.height(), thumbnail.pixels(),
                        THUMBNAIL_SCALE)))
                .orElseThrow(ApiError::notFound));
    }

    private CompletableFuture<Reply> scene(long id) {
        return off(vistructum.findings().scene(id)).thenApply(found -> Reply.json(Views.scene(
                found.orElseThrow(ApiError::notFound))));
    }

    private CompletableFuture<Reply> heatmap(long id) {
        return existing(id).thenCompose(finding -> off(vistructum.findings().heatmap(id)).handle((heatmap, error) -> {
            if (error != null || heatmap.isEmpty()) {
                throw ApiError.unavailable();
            }
            return Reply.json(Views.heatmap(heatmap.get()));
        }));
    }

    private CompletableFuture<Reply> evidence(long id) {
        return off(vistructum.findings().evidence(id)).thenApply(found -> Reply.json(Views.evidence(
                found.orElseThrow(ApiError::notFound), 0, 0, 0)));
    }

    private CompletableFuture<Reply> verdict(long id, Request request, WebSession session) {
        Verdict verdict = verdictOf(request.body());
        return off(vistructum.findings().review(id, verdict, session.playerName())).thenCompose(found -> summary(
                found.orElseThrow(ApiError::notFound))).thenApply(Reply::json);
    }

    private CompletableFuture<Reply> share(long id, WebSession session) {
        return permitted(session).thenCompose(ignored -> existing(id))
                .thenCompose(finding -> off(vistructum.web().share(id, session.playerName())))
                .thenCompose(token -> {
                    String shareToken = token.orElseThrow(ApiError::notShareable);
                    return off(vistructum.web().sharedSince(id)).thenApply(since -> {
                        JsonObject json = new JsonObject();
                        json.addProperty("url", settings.shareLink(shareToken));
                        json.addProperty("sharedSince", since.orElseGet(clock::instant).toString());
                        return Reply.json(json);
                    });
                });
    }

    private CompletableFuture<Reply> unshare(long id, WebSession session) {
        return permitted(session).thenCompose(ignored -> existing(id))
                .thenCompose(finding -> off(vistructum.web().unshare(id, session.playerName())))
                .thenApply(revoked -> Reply.noContent());
    }

    private CompletableFuture<Reply> stats(Request request) {
        Instant to = request.param("to").map(ReviewApi::instant).orElseGet(clock::instant);
        Instant from = request.param("from").map(ReviewApi::instant).orElseGet(() -> to.minus(DEFAULT_STATS_RANGE));
        if (to.isBefore(from)) {
            throw ApiError.badRequest();
        }
        return off(vistructum.findings().stats(from, to)).thenCombine(off(vistructum.findings().precision()),
                (stats, precision) -> Reply.json(Views.stats(stats, precision)));
    }

    private CompletableFuture<Reply> activity(Request request) {
        Instant before = request.param("before").map(ReviewApi::instant).orElseGet(clock::instant);
        int limit = request.param("limit").map(ReviewApi::limit).orElse(DEFAULT_ACTIVITY_LIMIT);
        return off(vistructum.findings().activity(before, limit)).thenApply(items -> Reply.json(Views.activity(items)));
    }

    private CompletableFuture<Reply> playerSummary(UUID player) {
        Findings findings = vistructum.findings();
        FindingQuery all = FindingQuery.all().player(player);
        CompletableFuture<Long> total = off(findings.count(all));
        CompletableFuture<Long> open = off(findings.count(all.state(ReviewState.OPEN)));
        CompletableFuture<Long> confirmed = off(findings.count(all.state(ReviewState.CONFIRMED)));
        CompletableFuture<Long> falseAlarms = off(findings.count(all.state(ReviewState.FALSE_ALARM)));
        CompletableFuture<Map<UUID, Optional<String>>> names = names(Set.of(player));
        return CompletableFuture.allOf(total, open, confirmed, falseAlarms, names).thenApply(ignored -> {
            JsonObject counts = new JsonObject();
            counts.addProperty("total", total.join());
            counts.addProperty("open", open.join());
            counts.addProperty("confirmed", confirmed.join());
            counts.addProperty("falseAlarms", falseAlarms.join());
            JsonObject json = Views.players(Set.of(player), names.join()).get(0).getAsJsonObject();
            json.add("findings", counts);
            return Reply.json(json);
        });
    }

    private CompletableFuture<Reply> skin(UUID player) {
        return off(vistructum.players().skin(player)).thenApply(found -> found
                .map(skin -> Reply.png(skin.png()).with("X-Skin-Model", skin.slim() ? "slim" : "classic"))
                .orElseThrow(ApiError::notFound));
    }

    private CompletableFuture<Reply> face(UUID player) {
        return off(vistructum.players().face(player)).thenApply(face -> {
            if (!face.hasSkin()) {
                throw ApiError.notFound();
            }
            int[] rgb = face.pixels().stream().mapToInt(Integer::intValue).toArray();
            return Reply.png(Pngs.scaled(8, 8, rgb, FACE_SCALE));
        });
    }

    private CompletableFuture<Reply> publicEvidence(String token) {
        return shared(token).thenCompose(shared -> names(shared.finding().players()).thenApply(names -> {
            BlockVolume before = shared.evidence().before();
            JsonObject json = new JsonObject();
            json.add("finding", Views.publicFinding(shared.finding(), names));
            json.add("evidence", Views.evidence(shared.evidence(), before.minX(), before.minY(), before.minZ()));
            return Reply.json(json);
        }));
    }

    private CompletableFuture<Reply> publicSkin(String token, UUID player) {
        return shared(token).thenCompose(shared -> {
            if (!participants(shared.evidence()).contains(player)) {
                throw ApiError.notFound();
            }
            return skin(player);
        });
    }

    private CompletableFuture<Shared> shared(String token) {
        return off(vistructum.web().sharedFinding(token)).thenCompose(found -> {
            long id = found.orElseThrow(ApiError::notFound);
            CompletableFuture<Optional<Evidence>> evidence = off(vistructum.findings().evidence(id));
            return off(vistructum.findings().get(id)).thenCombine(evidence, (finding, secured) -> {
                Finding confirmed = finding.filter(candidate -> candidate.review()
                        .filter(review -> review.verdict() == Verdict.CONFIRMED).isPresent())
                        .orElseThrow(ApiError::notFound);
                return new Shared(confirmed, secured.orElseThrow(ApiError::notFound));
            });
        });
    }

    private static Set<UUID> participants(Evidence evidence) {
        Set<UUID> players = new HashSet<>();
        evidence.changes().stream().map(BlockEvent::player).forEach(players::add);
        evidence.recordings().stream().map(Recording::player).forEach(players::add);
        return players;
    }

    private CompletableFuture<JsonObject> summary(Finding finding) {
        CompletableFuture<Boolean> evidence = off(vistructum.findings().hasEvidence(finding.id()));
        CompletableFuture<Optional<Instant>> shared = off(vistructum.web().sharedSince(finding.id()));
        CompletableFuture<Map<UUID, Optional<String>>> names = names(finding.players());
        return CompletableFuture.allOf(evidence, shared, names).thenApply(ignored ->
                Views.finding(finding, names.join(), evidence.join(), shared.join()));
    }

    private CompletableFuture<Finding> existing(long id) {
        return off(vistructum.findings().get(id)).thenApply(found -> found.orElseThrow(ApiError::notFound));
    }

    private CompletableFuture<Void> permitted(WebSession session) {
        return onMain(() -> game.canShare(session.player())).thenAccept(allowed -> {
            if (!allowed) {
                throw ApiError.forbidden();
            }
        });
    }

    private CompletableFuture<WebSession> session(Request request) {
        Optional<String> token = request.cookie(SESSION_COOKIE);
        if (token.isEmpty()) {
            return CompletableFuture.failedFuture(ApiError.unauthorized());
        }
        return off(vistructum.web().session(token.get())).thenApply(found -> found.orElseThrow(ApiError::unauthorized));
    }

    private CompletableFuture<Map<UUID, Optional<String>>> names(Collection<UUID> players) {
        if (players.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }
        return onMain(() -> {
            Map<UUID, Optional<String>> names = new HashMap<>();
            players.forEach(player -> names.put(player, game.playerName(player)));
            return names;
        });
    }

    private <T> CompletableFuture<T> off(CompletableFuture<T> future) {
        return future.thenApplyAsync(Function.identity(), worker);
    }

    private <T> CompletableFuture<T> onMain(Supplier<T> task) {
        return off(CompletableFuture.supplyAsync(task, mainThread));
    }

    private static CompletableFuture<Reply> only(boolean matches, Supplier<CompletableFuture<Reply>> route) {
        return matches ? route.get() : CompletableFuture.failedFuture(ApiError.notFound());
    }

    private static void requireCsrf(Request request) {
        Optional<String> cookie = request.cookie(CSRF_COOKIE);
        Optional<String> header = request.csrfHeader().filter(value -> !value.isEmpty());
        if (cookie.isEmpty() || header.isEmpty() || !MessageDigest.isEqual(
                cookie.get().getBytes(StandardCharsets.UTF_8), header.get().getBytes(StandardCharsets.UTF_8))) {
            throw ApiError.csrf();
        }
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

    static FindingQuery findingQuery(Request request) {
        FindingQuery query = FindingQuery.all().state(request.param("state").map(ReviewApi::state).orElse(ReviewState.ANY))
                .limit(request.param("limit").map(ReviewApi::limit).orElse(FindingQuery.DEFAULT_LIMIT));
        Optional<String> world = request.param("world");
        if (world.isPresent()) {
            query = query.world(world.get());
        }
        Optional<String> source = request.param("source");
        if (source.isPresent()) {
            query = query.source(source(source.get()));
        }
        Optional<String> player = request.param("player");
        if (player.isPresent()) {
            query = query.player(uuid(player.get()));
        }
        Optional<String> since = request.param("since");
        if (since.isPresent()) {
            query = query.since(instant(since.get()));
        }
        Optional<String> before = request.param("before");
        if (before.isPresent()) {
            query = query.before(id(before.get()));
        }
        return query;
    }

    private static ReviewState state(String value) {
        return Arrays.stream(ReviewState.values()).filter(state -> state.name().toLowerCase(Locale.ROOT).equals(value))
                .findFirst().orElseThrow(ApiError::badRequest);
    }

    private static Source source(String value) {
        return Arrays.stream(Source.values()).filter(source -> source.modelKind().equals(value)).findFirst()
                .orElseThrow(ApiError::badRequest);
    }

    private static Verdict verdictOf(byte[] body) {
        try {
            JsonElement json = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
            String verdict = json.getAsJsonObject().get("verdict").getAsString();
            return Verdict.valueOf(verdict);
        } catch (JsonParseException | IllegalStateException | IllegalArgumentException | NullPointerException
                 | UnsupportedOperationException e) {
            throw ApiError.badRequest();
        }
    }

    private static int limit(String value) {
        long limit = id(value);
        if (limit < 1 || limit > MAX_LIMIT) {
            throw ApiError.badRequest();
        }
        return (int) limit;
    }

    private static long id(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw ApiError.badRequest();
        }
    }

    private static UUID uuid(String value) {
        try {
            UUID uuid = UUID.fromString(value);
            if (!uuid.toString().equalsIgnoreCase(value)) {
                throw ApiError.badRequest();
            }
            return uuid;
        } catch (IllegalArgumentException e) {
            throw ApiError.badRequest();
        }
    }

    private static Instant instant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw ApiError.badRequest();
        }
    }

    private static List<String> segments(String path) {
        String rest = path.length() > API.length() ? path.substring(API.length()) : "";
        return rest.isEmpty() ? List.of() : List.of(rest.split("/", -1));
    }

    private record Shared(Finding finding, Evidence evidence) {
    }
}
