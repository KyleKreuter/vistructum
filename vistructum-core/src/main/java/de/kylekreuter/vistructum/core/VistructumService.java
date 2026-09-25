package de.kylekreuter.vistructum.core;

import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.FindingQuery;
import de.kylekreuter.vistructum.api.FindingReviewedEvent;
import de.kylekreuter.vistructum.api.InferenceStatus;
import de.kylekreuter.vistructum.api.Findings;
import de.kylekreuter.vistructum.api.Page;
import de.kylekreuter.vistructum.api.Preview;
import de.kylekreuter.vistructum.api.ScanCause;
import de.kylekreuter.vistructum.api.ScanJob;
import de.kylekreuter.vistructum.api.Scans;
import de.kylekreuter.vistructum.api.Verdict;
import de.kylekreuter.vistructum.api.Vistructum;
import de.kylekreuter.vistructum.api.VistructumStatus;
import de.kylekreuter.vistructum.core.alert.FindingSlice;
import de.kylekreuter.vistructum.core.alert.FindingStore;
import de.kylekreuter.vistructum.core.alert.PreviewImage;
import de.kylekreuter.vistructum.core.scan.ScanStore;
import de.kylekreuter.vistructum.core.scan.WorldScanner;
import de.kylekreuter.vistructum.core.inference.Inference;
import de.kylekreuter.vistructum.core.tracking.BlockChangeStore;
import org.bukkit.Bukkit;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class VistructumService implements Vistructum {

    private static final int PREVIEW_SCALE = 4;

    private final MainThread mainThread;
    private final BlockChangeStore changes;
    private final FindingStore findingStore;
    private final ScanStore scanStore;
    private final WorldScanner scanner;
    private final Inference inference;
    private final Clock clock;
    private final Findings findings = new StoredFindings();
    private final Scans scans = new ScheduledScans();

    public VistructumService(MainThread mainThread, BlockChangeStore changes, FindingStore findingStore,
                             ScanStore scanStore, WorldScanner scanner, Inference inference, Clock clock) {
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.changes = Objects.requireNonNull(changes, "changes");
        this.findingStore = Objects.requireNonNull(findingStore, "findingStore");
        this.scanStore = Objects.requireNonNull(scanStore, "scanStore");
        this.scanner = Objects.requireNonNull(scanner, "scanner");
        this.inference = Objects.requireNonNull(inference, "inference");
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
    public CompletableFuture<VistructumStatus> status() {
        CompletableFuture<InferenceStatus> models = inference.status();
        CompletableFuture<Integer> tracked = changes.count();
        CompletableFuture<Long> open = findingStore.count(FindingQuery.open());
        CompletableFuture<List<ScanJob>> active = scanStore.active();
        return mainThread.handOff(CompletableFuture.allOf(tracked, open, active, models).thenApply(ignored ->
                new VistructumStatus(tracked.join(), Math.toIntExact(open.join()), active.join(), models.join())));
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
}
