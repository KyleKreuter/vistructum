package de.kylekreuter.vistructum.core;

import de.kylekreuter.vistructum.api.Activity;
import de.kylekreuter.vistructum.api.Evidence;
import de.kylekreuter.vistructum.api.EvidenceShare;
import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.FindingQuery;
import de.kylekreuter.vistructum.api.FindingReviewedEvent;
import de.kylekreuter.vistructum.api.FindingScene;
import de.kylekreuter.vistructum.api.FindingStats;
import de.kylekreuter.vistructum.api.Heatmap;
import de.kylekreuter.vistructum.api.IssuedSession;
import de.kylekreuter.vistructum.api.InferenceStatus;
import de.kylekreuter.vistructum.api.Findings;
import de.kylekreuter.vistructum.api.Page;
import de.kylekreuter.vistructum.api.PlayerFace;
import de.kylekreuter.vistructum.api.PlayerSkin;
import de.kylekreuter.vistructum.api.Players;
import de.kylekreuter.vistructum.api.Preview;
import de.kylekreuter.vistructum.api.ScanCause;
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
import de.kylekreuter.vistructum.core.alert.FindingExporter;
import de.kylekreuter.vistructum.core.alert.FindingHeatmaps;
import de.kylekreuter.vistructum.core.alert.FindingSlice;
import de.kylekreuter.vistructum.core.alert.FindingStore;
import de.kylekreuter.vistructum.core.alert.MaterialKeys;
import de.kylekreuter.vistructum.core.alert.PreviewImage;
import de.kylekreuter.vistructum.core.alert.SceneView;
import de.kylekreuter.vistructum.core.evidence.EvidenceStore;
import de.kylekreuter.vistructum.core.skin.SkinCache;
import de.kylekreuter.vistructum.core.web.WebStore;
import de.kylekreuter.vistructum.core.scan.ScanStore;
import de.kylekreuter.vistructum.core.scan.WorldScanner;
import de.kylekreuter.vistructum.core.inference.Inference;
import de.kylekreuter.vistructum.core.tracking.BlockChangeStore;
import org.bukkit.Bukkit;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class VistructumService implements Vistructum {

    private static final int PREVIEW_SCALE = 4;

    private final MainThread mainThread;
    private final BlockChangeStore changes;
    private final FindingStore findingStore;
    private final FindingExporter exporter;
    private final ScanStore scanStore;
    private final WorldScanner scanner;
    private final Inference inference;
    private final SkinCache skins;
    private final FindingHeatmaps heatmaps;
    private final EvidenceStore evidenceStore;
    private final WebStore webStore;
    private final boolean recordingEnabled;
    private final Clock clock;
    private final Findings findings = new StoredFindings();
    private final Scans scans = new ScheduledScans();
    private final Players players = new CachedPlayers();
    private final WebAccess web = new StoredWebAccess();

    public VistructumService(MainThread mainThread, BlockChangeStore changes, FindingStore findingStore,
                             FindingExporter exporter, ScanStore scanStore, WorldScanner scanner, Inference inference,
                             SkinCache skins, FindingHeatmaps heatmaps, EvidenceStore evidenceStore, WebStore webStore,
                             boolean recordingEnabled, Clock clock) {
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.changes = Objects.requireNonNull(changes, "changes");
        this.findingStore = Objects.requireNonNull(findingStore, "findingStore");
        this.exporter = Objects.requireNonNull(exporter, "exporter");
        this.scanStore = Objects.requireNonNull(scanStore, "scanStore");
        this.scanner = Objects.requireNonNull(scanner, "scanner");
        this.inference = Objects.requireNonNull(inference, "inference");
        this.skins = Objects.requireNonNull(skins, "skins");
        this.heatmaps = Objects.requireNonNull(heatmaps, "heatmaps");
        this.evidenceStore = Objects.requireNonNull(evidenceStore, "evidenceStore");
        this.webStore = Objects.requireNonNull(webStore, "webStore");
        this.recordingEnabled = recordingEnabled;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Findings findings() {
        return findings;
    }

    @Override
    public Scans scans() {
        return scans;
    }

    @Override
    public Players players() {
        return players;
    }

    @Override
    public WebAccess web() {
        return web;
    }

    @Override
    public CompletableFuture<VistructumStatus> status() {
        CompletableFuture<InferenceStatus> models = inference.status();
        CompletableFuture<Integer> tracked = changes.count();
        CompletableFuture<Long> open = findingStore.count(FindingQuery.open());
        CompletableFuture<List<ScanJob>> active = scanStore.active();
        return mainThread.handOff(CompletableFuture.allOf(tracked, open, active, models).thenApply(ignored ->
                new VistructumStatus(tracked.join(), Math.toIntExact(open.join()), active.join(), models.join(),
                        recordingEnabled)));
    }

    private final class StoredFindings implements Findings {

        @Override
        public CompletableFuture<Optional<Finding>> get(long id) {
            return mainThread.handOff(findingStore.find(id));
        }

        @Override
        public CompletableFuture<Page<Finding>> find(FindingQuery query) {
            Objects.requireNonNull(query, "query");
            return mainThread.handOff(findingStore.query(query).thenApply(slice -> new FindingPage(query, slice)));
        }

        @Override
        public CompletableFuture<Long> count(FindingQuery query) {
            Objects.requireNonNull(query, "query");
            return mainThread.handOff(findingStore.count(query));
        }

        @Override
        public CompletableFuture<Optional<Finding>> review(long id, Verdict verdict, String reviewer) {
            Objects.requireNonNull(verdict, "verdict");
            Objects.requireNonNull(reviewer, "reviewer");
            return mainThread.handOff(findingStore.review(id, verdict, reviewer, clock.instant())
                    .thenCompose(reviewed -> mainThread.supply(() -> {
                        reviewed.ifPresent(finding ->
                                Bukkit.getPluginManager().callEvent(new FindingReviewedEvent(finding)));
                        return reviewed;
                    })));
        }

        @Override
        public CompletableFuture<Optional<Preview>> preview(long id) {
            return mainThread.handOff(findingStore.preview(id));
        }

        @Override
        public CompletableFuture<Optional<byte[]>> previewPng(long id) {
            return mainThread.handOff(findingStore.preview(id)
                    .thenApply(preview -> preview.map(p -> PreviewImage.png(p, PREVIEW_SCALE))));
        }

        @Override
        public CompletableFuture<Optional<Thumbnail>> thumbnail(long id) {
            return mainThread.handOff(findingStore.thumbnail(id));
        }

        @Override
        public CompletableFuture<Boolean> storeThumbnail(long id, Thumbnail thumbnail) {
            Objects.requireNonNull(thumbnail, "thumbnail");
            return mainThread.handOff(findingStore.storeThumbnail(id, thumbnail));
        }

        @Override
        public CompletableFuture<List<Finding>> withoutThumbnail(long afterId, int limit) {
            if (limit < 1) {
                throw new IllegalArgumentException("limit " + limit);
            }
            return mainThread.handOff(findingStore.withoutThumbnail(afterId, limit));
        }

        @Override
        public CompletableFuture<List<SourcePrecision>> precision() {
            return mainThread.handOff(findingStore.precision());
        }

        @Override
        public CompletableFuture<TrainingExport> exportTraining() {
            return mainThread.handOff(exporter.export());
        }

        @Override
        public CompletableFuture<Optional<FindingScene>> scene(long id) {
            return mainThread.handOff(findingStore.scene(id)
                    .thenApply(stored -> stored.map(scene -> SceneView.of(scene, MaterialKeys::byOrdinal))));
        }

        @Override
        public CompletableFuture<Optional<Heatmap>> heatmap(long id) {
            return mainThread.handOff(heatmaps.heatmap(id));
        }

        @Override
        public CompletableFuture<Boolean> hasEvidence(long id) {
            return mainThread.handOff(evidenceStore.hasEvidence(id));
        }

        @Override
        public CompletableFuture<Optional<Evidence>> evidence(long id) {
            return mainThread.handOff(evidenceStore.evidence(id));
        }

        @Override
        public CompletableFuture<FindingStats> stats(Instant from, Instant to) {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            if (to.isBefore(from)) {
                throw new IllegalArgumentException("range ends before it starts: " + from + " to " + to);
            }
            return mainThread.handOff(findingStore.stats(from, to, clock.getZone()));
        }

        @Override
        public CompletableFuture<List<Activity>> activity(Instant before, int limit) {
            Objects.requireNonNull(before, "before");
            if (limit < 1 || limit > FindingQuery.MAX_LIMIT) {
                throw new IllegalArgumentException("limit must be in 1.." + FindingQuery.MAX_LIMIT + ", got " + limit);
            }
            return mainThread.handOff(findingStore.activity(before, limit));
        }
    }

    private final class FindingPage implements Page<Finding> {

        private final FindingQuery query;
        private final FindingSlice slice;

        private FindingPage(FindingQuery query, FindingSlice slice) {
            this.query = query;
            this.slice = slice;
        }

        @Override
        public List<Finding> items() {
            return slice.findings();
        }

        @Override
        public boolean hasNext() {
            return slice.more();
        }

        @Override
        public CompletableFuture<Page<Finding>> next() {
            if (!slice.more()) {
                return CompletableFuture.failedFuture(new IllegalStateException("no further page"));
            }
            return findings.find(query.before(slice.findings().getLast().id()));
        }
    }

    private final class ScheduledScans implements Scans {

        @Override
        public CompletableFuture<Optional<ScanJob>> request(String world) {
            Objects.requireNonNull(world, "world");
            return mainThread.handOff(mainThread.supply(() -> Bukkit.getWorld(world) != null).thenCompose(exists -> {
                if (!exists) {
                    throw new CompletionException(new IllegalArgumentException("unknown world " + world));
                }
                return scanner.enqueue(world, ScanCause.MANUAL);
            }));
        }

        @Override
        public CompletableFuture<List<ScanJob>> active() {
            return mainThread.handOff(scanStore.active().thenApply(List::copyOf));
        }

        @Override
        public CompletableFuture<List<ScanJob>> cancelAll() {
            return mainThread.handOff(mainThread.supply(scanner::cancel).thenCompose(cancelled -> cancelled).thenApply(List::copyOf));
        }
    }

    private final class CachedPlayers implements Players {

        @Override
        public CompletableFuture<PlayerFace> face(UUID player) {
            Objects.requireNonNull(player, "player");
            return mainThread.handOff(knownName(player).thenCompose(name -> skins.face(player, name)));
        }

        @Override
        public CompletableFuture<Optional<PlayerSkin>> skin(UUID player) {
            Objects.requireNonNull(player, "player");
            return mainThread.handOff(knownName(player).thenCompose(name -> skins.skin(player, name)));
        }

        private CompletableFuture<Optional<String>> knownName(UUID player) {
            return mainThread.supply(() -> Optional.ofNullable(Bukkit.getOfflinePlayer(player).getName()));
        }
    }

    private final class StoredWebAccess implements WebAccess {

        @Override
        public CompletableFuture<String> issueLogin(UUID player, String playerName) {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(playerName, "playerName");
            return mainThread.handOff(webStore.issueLogin(player, playerName, clock.instant()));
        }

        @Override
        public CompletableFuture<Optional<IssuedSession>> redeemLogin(String loginToken) {
            Objects.requireNonNull(loginToken, "loginToken");
            return mainThread.handOff(webStore.redeemLogin(loginToken, clock.instant()));
        }

        @Override
        public CompletableFuture<Optional<WebSession>> session(String sessionToken) {
            Objects.requireNonNull(sessionToken, "sessionToken");
            return mainThread.handOff(webStore.session(sessionToken, clock.instant()));
        }

        @Override
        public CompletableFuture<Void> endSession(String sessionToken) {
            Objects.requireNonNull(sessionToken, "sessionToken");
            return mainThread.handOff(webStore.endSession(sessionToken));
        }

        @Override
        public CompletableFuture<Optional<String>> share(long findingId, String actor) {
            Objects.requireNonNull(actor, "actor");
            return mainThread.handOff(webStore.share(findingId, actor, clock.instant()));
        }

        @Override
        public CompletableFuture<Boolean> unshare(long findingId, String actor) {
            Objects.requireNonNull(actor, "actor");
            return mainThread.handOff(webStore.unshare(findingId, actor, clock.instant()));
        }

        @Override
        public CompletableFuture<Optional<Long>> sharedFinding(String shareToken) {
            Objects.requireNonNull(shareToken, "shareToken");
            return mainThread.handOff(webStore.sharedFinding(shareToken));
        }

        @Override
        public CompletableFuture<Optional<EvidenceShare>> shared(long findingId) {
            return mainThread.handOff(webStore.shared(findingId));
        }
    }
}
