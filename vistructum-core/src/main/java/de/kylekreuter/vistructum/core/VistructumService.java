package de.kylekreuter.vistructum.core;

import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.Verdict;
import de.kylekreuter.vistructum.api.VistructumApi;
import de.kylekreuter.vistructum.core.alert.FindingStore;
import de.kylekreuter.vistructum.core.alert.PreviewImage;
import de.kylekreuter.vistructum.core.scan.ScanCause;
import de.kylekreuter.vistructum.core.scan.WorldScanner;
import org.bukkit.Bukkit;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class VistructumService implements VistructumApi {

    private static final int PREVIEW_SCALE = 4;

    private final MainThread mainThread;
    private final FindingStore findings;
    private final WorldScanner scanner;
    private final Clock clock;

    public VistructumService(MainThread mainThread, FindingStore findings, WorldScanner scanner, Clock clock) {
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.findings = Objects.requireNonNull(findings, "findings");
        this.scanner = Objects.requireNonNull(scanner, "scanner");
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
        return findings.review(id, verdict, reviewer, clock.instant());
    }

    @Override
    public CompletableFuture<Optional<byte[]>> renderPreview(long id) {
        return findings.preview(id).thenApply(preview -> preview.map(p -> PreviewImage.png(p, PREVIEW_SCALE)));
    }

    @Override
    public CompletableFuture<Boolean> requestScan(String world) {
        return mainThread.supply(() -> Bukkit.getWorld(world) != null).thenCompose(exists -> exists
                ? scanner.enqueue(world, ScanCause.MANUAL).thenApply(Optional::isPresent)
                : CompletableFuture.completedFuture(false));
    }
}
