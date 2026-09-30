package de.kylekreuter.vistructum.ui.web;

import de.kylekreuter.vistructum.api.Activity;
import de.kylekreuter.vistructum.api.ActivityKind;
import de.kylekreuter.vistructum.api.BlockLog;
import de.kylekreuter.vistructum.api.Evidence;
import de.kylekreuter.vistructum.api.EvidenceShare;
import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.FindingQuery;
import de.kylekreuter.vistructum.api.FindingScene;
import de.kylekreuter.vistructum.api.FindingTerrain;
import de.kylekreuter.vistructum.api.FindingStats;
import de.kylekreuter.vistructum.api.Findings;
import de.kylekreuter.vistructum.api.Heatmap;
import de.kylekreuter.vistructum.api.InferenceMode;
import de.kylekreuter.vistructum.api.InferenceStatus;
import de.kylekreuter.vistructum.api.IssuedSession;
import de.kylekreuter.vistructum.api.Page;
import de.kylekreuter.vistructum.api.PlayerFace;
import de.kylekreuter.vistructum.api.PlayerSkin;
import de.kylekreuter.vistructum.api.Players;
import de.kylekreuter.vistructum.api.Preview;
import de.kylekreuter.vistructum.api.Review;
import de.kylekreuter.vistructum.api.RollbackResult;
import de.kylekreuter.vistructum.api.ReviewState;
import de.kylekreuter.vistructum.api.ScanJob;
import de.kylekreuter.vistructum.api.Scans;
import de.kylekreuter.vistructum.api.SourcePrecision;
import de.kylekreuter.vistructum.api.Thumbnail;
import de.kylekreuter.vistructum.api.TrainingExport;
import de.kylekreuter.vistructum.api.Verdict;
import de.kylekreuter.vistructum.api.Vistructum;
import de.kylekreuter.vistructum.api.VistructumStatus;
import de.kylekreuter.vistructum.api.WebAccess;
import de.kylekreuter.vistructum.api.WebSession;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.concurrent.CompletableFuture.completedFuture;

public final class FakeVistructum implements Vistructum, Findings, Scans, Players, WebAccess, BlockLog {

    public final Map<Long, Finding> findings = new TreeMap<>();
    public final Map<Long, Evidence> evidence = new ConcurrentHashMap<>();
    public final Map<Long, FindingTerrain> terrain = new ConcurrentHashMap<>();
    public final Map<String, IssuedSession> logins = new ConcurrentHashMap<>();
    public final Map<String, WebSession> sessions = new ConcurrentHashMap<>();
    public final Map<String, Long> shares = new ConcurrentHashMap<>();
    public final Map<UUID, PlayerSkin> skins = new ConcurrentHashMap<>();
    public final Map<Long, Set<UUID>> builders = new ConcurrentHashMap<>();
    public final Map<Long, String> rollbacks = new ConcurrentHashMap<>();
    public volatile boolean blockLogAvailable = true;
    final Instant now;

    public FakeVistructum(Instant now) {
        this.now = now;
    }

    @Override
    public Findings findings() {
        return this;
    }

    @Override
    public Scans scans() {
        return this;
    }

    @Override
    public Players players() {
        return this;
    }

    @Override
    public WebAccess web() {
        return this;
    }

    @Override
    public BlockLog blockLog() {
        return this;
    }

    @Override
    public boolean available() {
        return blockLogAvailable;
    }

    @Override
    public synchronized CompletableFuture<Optional<Finding>> attribute(long findingId, String actor) {
        Finding finding = findings.get(findingId);
        Set<UUID> players = builders.getOrDefault(findingId, Set.of());
        if (finding == null || players.isEmpty()) {
            return completedFuture(Optional.ofNullable(finding));
        }
        Finding attributed = new Finding(finding.id(), finding.source(), finding.world(), finding.box(),
                finding.score(), finding.votes(), players, finding.detail(), finding.modelVersion(),
                finding.createdAt(), finding.review());
        findings.put(findingId, attributed);
        return completedFuture(Optional.of(attributed));
    }

    @Override
    public synchronized CompletableFuture<Optional<RollbackResult>> rollback(long findingId, String actor) {
        if (!findings.containsKey(findingId)) {
            return completedFuture(Optional.empty());
        }
        rollbacks.put(findingId, actor);
        return completedFuture(Optional.of(new RollbackResult(12, 2)));
    }

    @Override
    public CompletableFuture<Optional<Activity>> lastRollback(long findingId) {
        return completedFuture(Optional.ofNullable(rollbacks.get(findingId))
                .map(actor -> new Activity(now, actor, ActivityKind.ROLLED_BACK, findingId)));
    }

    @Override
    public CompletableFuture<VistructumStatus> status() {
        return completedFuture(new VistructumStatus(3, 1, List.of(),
                new InferenceStatus(InferenceMode.LOCAL, true, Map.of("mask", "bf-mask-1"), Optional.empty()), true));
    }

    @Override
    public synchronized CompletableFuture<Optional<Finding>> get(long id) {
        return completedFuture(Optional.ofNullable(findings.get(id)));
    }

    @Override
    public synchronized CompletableFuture<Page<Finding>> find(FindingQuery query) {
        List<Finding> matches = matching(query).stream()
                .filter(finding -> query.beforeId().isEmpty() || finding.id() < query.beforeId().getAsLong())
                .toList();
        List<Finding> items = matches.stream().skip(query.offset()).limit(query.limit()).toList();
        boolean more = matches.size() > query.offset() + items.size();
        return completedFuture(new ListPage(items, more));
    }

    @Override
    public synchronized CompletableFuture<Long> count(FindingQuery query) {
        return completedFuture((long) matching(query).size());
    }

    private List<Finding> matching(FindingQuery query) {
        List<Finding> matches = new ArrayList<>(findings.values().stream()
                .filter(finding -> query.world().map(finding.world()::equals).orElse(true))
                .filter(finding -> query.source().map(source -> finding.source() == source).orElse(true))
                .filter(finding -> query.player().map(finding.players()::contains).orElse(true))
                .filter(finding -> state(finding, query.state()))
                .toList());
        matches.sort((a, b) -> Long.compare(b.id(), a.id()));
        return matches;
    }

    private static boolean state(Finding finding, ReviewState state) {
        Optional<Verdict> verdict = finding.review().map(Review::verdict);
        return switch (state) {
            case ANY -> true;
            case OPEN -> verdict.isEmpty();
            case REVIEWED -> verdict.isPresent();
            case CONFIRMED -> verdict.filter(Verdict.CONFIRMED::equals).isPresent();
            case FALSE_ALARM -> verdict.filter(Verdict.FALSE_ALARM::equals).isPresent();
        };
    }

    @Override
    public synchronized CompletableFuture<Optional<Finding>> review(long id, Verdict verdict, String reviewer) {
        Finding finding = findings.get(id);
        if (finding == null) {
            return completedFuture(Optional.empty());
        }
        Finding reviewed = new Finding(finding.id(), finding.source(), finding.world(), finding.box(), finding.score(),
                finding.votes(), finding.players(), finding.detail(), finding.modelVersion(), finding.createdAt(),
                Optional.of(new Review(verdict, reviewer, now)));
        findings.put(id, reviewed);
        return completedFuture(Optional.of(reviewed));
    }

    @Override
    public CompletableFuture<Optional<Preview>> preview(long id) {
        return completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<Optional<byte[]>> previewPng(long id) {
        return completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<Optional<Thumbnail>> thumbnail(long id) {
        return completedFuture(findings.containsKey(id)
                ? Optional.of(new Thumbnail(2, 2, new int[]{0xFF0000, 0x00FF00, 0x0000FF, 0xFFFFFF}))
                : Optional.empty());
    }

    @Override
    public CompletableFuture<Boolean> storeThumbnail(long id, Thumbnail thumbnail) {
        return completedFuture(false);
    }

    @Override
    public CompletableFuture<Optional<String>> reference(long id, String system) {
        return completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<Boolean> storeReference(long id, String system, String reference) {
        return completedFuture(false);
    }

    @Override
    public CompletableFuture<List<Finding>> withoutThumbnail(long afterId, int limit) {
        return completedFuture(List.of());
    }

    @Override
    public CompletableFuture<List<SourcePrecision>> precision() {
        return completedFuture(List.of());
    }

    @Override
    public CompletableFuture<TrainingExport> exportTraining() {
        return completedFuture(new TrainingExport(Path.of("export"), Map.of(), 0));
    }

    @Override
    public CompletableFuture<Optional<FindingScene>> scene(long id) {
        return completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<Optional<Heatmap>> heatmap(long id) {
        return completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<Boolean> hasTerrain(long id) {
        return completedFuture(terrain.containsKey(id));
    }

    @Override
    public CompletableFuture<Optional<FindingTerrain>> terrain(long id) {
        return completedFuture(Optional.ofNullable(terrain.get(id)));
    }

    @Override
    public CompletableFuture<Boolean> hasEvidence(long id) {
        return completedFuture(evidence.containsKey(id));
    }

    @Override
    public CompletableFuture<Optional<Evidence>> evidence(long id) {
        return completedFuture(Optional.ofNullable(evidence.get(id)));
    }

    @Override
    public CompletableFuture<FindingStats> stats(Instant from, Instant to) {
        return completedFuture(new FindingStats(List.of(), List.of(), 0, Optional.empty()));
    }

    @Override
    public CompletableFuture<List<Activity>> activity(Instant before, int limit) {
        return completedFuture(List.of());
    }

    @Override
    public CompletableFuture<Optional<ScanJob>> request(String world) {
        return completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<List<ScanJob>> active() {
        return completedFuture(List.of());
    }

    @Override
    public CompletableFuture<List<ScanJob>> cancelAll() {
        return completedFuture(List.of());
    }

    @Override
    public CompletableFuture<PlayerFace> face(UUID player) {
        return completedFuture(new PlayerFace(player, Optional.empty(), List.of()));
    }

    @Override
    public CompletableFuture<Optional<PlayerSkin>> skin(UUID player) {
        return completedFuture(Optional.ofNullable(skins.get(player)));
    }

    @Override
    public CompletableFuture<String> issueLogin(UUID player, String playerName) {
        String token = "login-" + logins.size();
        logins.put(token, new IssuedSession("session-" + token, new WebSession(player, playerName, now.plusSeconds(3600))));
        return completedFuture(token);
    }

    @Override
    public CompletableFuture<Optional<IssuedSession>> redeemLogin(String loginToken) {
        IssuedSession issued = logins.remove(loginToken);
        if (issued != null) {
            sessions.put(issued.token(), issued.session());
        }
        return completedFuture(Optional.ofNullable(issued));
    }

    @Override
    public CompletableFuture<Optional<WebSession>> session(String sessionToken) {
        return completedFuture(Optional.ofNullable(sessions.get(sessionToken)));
    }

    @Override
    public CompletableFuture<Void> endSession(String sessionToken) {
        sessions.remove(sessionToken);
        return completedFuture(null);
    }

    @Override
    public synchronized CompletableFuture<Optional<String>> share(long findingId, String actor) {
        if (shares.containsValue(findingId)) {
            return completedFuture(Optional.of("share-" + findingId));
        }
        Finding finding = findings.get(findingId);
        boolean confirmed = finding != null && finding.review().map(Review::verdict).orElse(null) == Verdict.CONFIRMED;
        if (!confirmed || !evidence.containsKey(findingId)) {
            return completedFuture(Optional.empty());
        }
        String token = "share-" + findingId;
        shares.put(token, findingId);
        return completedFuture(Optional.of(token));
    }

    @Override
    public CompletableFuture<Boolean> unshare(long findingId, String actor) {
        return completedFuture(shares.values().removeIf(id -> id == findingId));
    }

    @Override
    public CompletableFuture<Optional<Long>> sharedFinding(String shareToken) {
        return completedFuture(Optional.ofNullable(shares.get(shareToken)));
    }

    @Override
    public CompletableFuture<Optional<EvidenceShare>> shared(long findingId) {
        return completedFuture(shares.containsValue(findingId)
                ? Optional.of(new EvidenceShare("share-" + findingId, now)) : Optional.empty());
    }

    private record ListPage(List<Finding> items, boolean hasNext) implements Page<Finding> {

        @Override
        public CompletableFuture<Page<Finding>> next() {
            return completedFuture(new ListPage(List.of(), false));
        }
    }
}
