package de.kylekreuter.vistructum.core;

import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.Preview;
import de.kylekreuter.vistructum.api.ScanCause;
import de.kylekreuter.vistructum.api.ScanJob;
import de.kylekreuter.vistructum.api.SidecarStatus;
import de.kylekreuter.vistructum.api.Verdict;
import de.kylekreuter.vistructum.api.VistructumApi;
import de.kylekreuter.vistructum.api.VistructumStatus;
import de.kylekreuter.vistructum.core.alert.FindingStore;
import de.kylekreuter.vistructum.core.alert.PreviewImage;
import de.kylekreuter.vistructum.core.scan.ScanStore;
import de.kylekreuter.vistructum.core.scan.WorldScanner;
import de.kylekreuter.vistructum.core.sidecar.SidecarClient;
import de.kylekreuter.vistructum.core.tracking.BlockChangeStore;
import org.bukkit.Bukkit;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class VistructumService implements VistructumApi {

    private static final int PREVIEW_SCALE = 4;

    private final MainThread mainThread;
    private final BlockChangeStore changes;
    private final FindingStore findings;
    private final ScanStore scans;
    private final WorldScanner scanner;
    private final SidecarClient client;
    private final Clock clock;

    public VistructumService(MainThread mainThread, BlockChangeStore changes, FindingStore findings, ScanStore scans,
                             WorldScanner scanner, SidecarClient client, Clock clock) {
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.changes = Objects.requireNonNull(changes, "changes");
        this.findings = Objects.requireNonNull(findings, "findings");
        this.scans = Objects.requireNonNull(scans, "scans");
        this.scanner = Objects.requireNonNull(scanner, "scanner");
        this.client = Objects.requireNonNull(client, "client");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CompletableFuture<Optional<Finding>> finding(long id) {
        return findings.find(id);
    }

    @Override
    public CompletableFuture<List<Finding>> openFindings(int limit) {
        return findings.open(limit);
    }

    @Override
    public CompletableFuture<List<Finding>> findingsSince(Instant since, int limit) {
        return findings.since(since, limit);
    }

    @Override
    public CompletableFuture<Optional<Finding>> review(long id, Verdict verdict, String reviewer) {
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(reviewer, "reviewer");
        return findings.review(id, verdict, reviewer, clock.instant());
    }

    @Override
    public CompletableFuture<Optional<Preview>> preview(long id) {
        return findings.preview(id);
    }

    @Override
    public CompletableFuture<Optional<byte[]>> renderPreview(long id) {
        return findings.preview(id).thenApply(preview -> preview.map(p -> PreviewImage.png(p, PREVIEW_SCALE)));
    }

    @Override
    public CompletableFuture<Optional<ScanJob>> requestScan(String world) {
        Objects.requireNonNull(world, "world");
        return mainThread.supply(() -> Bukkit.getWorld(world) != null).thenCompose(exists -> {
            if (!exists) {
                throw new CompletionException(new IllegalArgumentException("unknown world " + world));
            }
            return scanner.enqueue(world, ScanCause.MANUAL);
        });
    }

    @Override
    public CompletableFuture<List<ScanJob>> activeScans() {
        return scans.active();
    }

    @Override
    public CompletableFuture<Integer> cancelScans() {
        return scanner.cancel();
    }

    @Override
    public CompletableFuture<VistructumStatus> status() {
        CompletableFuture<SidecarStatus> sidecar = client.health()
                .thenApply(health -> new SidecarStatus(true, health.status(), health.models(), Optional.empty()))
                .exceptionally(error -> SidecarStatus.unreachable(rootMessage(error)));
        CompletableFuture<Integer> tracked = changes.count();
        CompletableFuture<Integer> open = findings.countOpen();
        CompletableFuture<List<ScanJob>> active = scans.active();
        return CompletableFuture.allOf(tracked, open, active, sidecar).thenApply(ignored ->
                new VistructumStatus(tracked.join(), open.join(), active.join(), sidecar.join()));
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        return Objects.requireNonNullElse(cause.getMessage(), cause.getClass().getSimpleName());
    }
}
