package de.kylekreuter.vistructum.core.alert;

import de.kylekreuter.vistructum.api.BlockBox;
import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.FindingCandidate;
import de.kylekreuter.vistructum.api.FindingCreateEvent;
import de.kylekreuter.vistructum.api.FindingCreatedEvent;
import de.kylekreuter.vistructum.core.MainThread;
import org.bukkit.Bukkit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public final class FindingReporter {

    private final MainThread mainThread;
    private final FindingStore store;
    private final Logger logger;
    private final Duration dedupe;
    private final Clock clock;

    public FindingReporter(MainThread mainThread, FindingStore store, Logger logger, Duration dedupe,
                           Clock clock) {
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.store = Objects.requireNonNull(store, "store");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.dedupe = Objects.requireNonNull(dedupe, "dedupe");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CompletableFuture<Optional<Finding>> report(DetectedCandidate detected) {
        FindingCandidate candidate = detected.candidate();
        Instant now = clock.instant();
        return store.isDuplicate(candidate, now, dedupe)
                .thenCompose(duplicate -> duplicate
                        ? CompletableFuture.completedFuture(false)
                        : mainThread.supply(() -> admitted(candidate)))
                .thenCompose(admitted -> admitted
                        ? store.insertUnlessDuplicate(detected, now, dedupe)
                        : CompletableFuture.completedFuture(Optional.<Finding>empty()))
                .thenCompose(stored -> stored.isEmpty()
                        ? CompletableFuture.completedFuture(stored)
                        : mainThread.supply(() -> {
                            announce(stored.get());
                            return stored;
                        }));
    }

    public CompletableFuture<Integer> reportAll(List<DetectedCandidate> candidates) {
        CompletableFuture<Integer> reported = CompletableFuture.completedFuture(0);
        for (DetectedCandidate candidate : candidates) {
            reported = reported.thenCompose(count -> report(candidate)
                    .thenApply(stored -> count + (stored.isPresent() ? 1 : 0)));
        }
        return reported;
    }

    private boolean admitted(FindingCandidate candidate) {
        FindingCreateEvent event = new FindingCreateEvent(candidate);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            logger.info(String.format(Locale.ROOT, "%s candidate in %s at %s cancelled by a listener",
                    candidate.source().modelKind(), candidate.world(), describe(candidate.box())));
        }
        return !event.isCancelled();
    }

    private void announce(Finding finding) {
        logger.warning(String.format(Locale.ROOT, "finding #%d: %s score %.3f votes %d in %s %s",
                finding.id(), finding.source().modelKind(), finding.score(), finding.votes(), finding.world(),
                describe(finding.box())));
        Bukkit.getPluginManager().callEvent(new FindingCreatedEvent(finding));
    }

    private static String describe(BlockBox box) {
        return String.format(Locale.ROOT, "x=%d..%d y=%d..%d z=%d..%d",
                box.minX(), box.maxX(), box.minY(), box.maxY(), box.minZ(), box.maxZ());
    }
}
