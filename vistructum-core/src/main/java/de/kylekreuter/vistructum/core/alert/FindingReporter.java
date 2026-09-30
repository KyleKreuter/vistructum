package de.kylekreuter.vistructum.core.alert;

import de.kylekreuter.vistructum.api.BlockBox;
import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.FindingCandidate;
import de.kylekreuter.vistructum.api.FindingCreateEvent;
import de.kylekreuter.vistructum.api.FindingCreatedEvent;
import de.kylekreuter.vistructum.core.MainThread;
import de.kylekreuter.vistructum.core.history.BlockHistory;
import de.kylekreuter.vistructum.core.history.Builders;
import org.bukkit.Bukkit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

public final class FindingReporter {

    private final MainThread mainThread;
    private final FindingStore store;
    private final Logger logger;
    private final Duration dedupe;
    private final Optional<BlockHistory> history;
    private final Duration historyTimeout;
    private final Consumer<Finding> created;
    private final Clock clock;

    public FindingReporter(MainThread mainThread, FindingStore store, Logger logger, Duration dedupe,
                           Optional<BlockHistory> history, Duration historyTimeout, Consumer<Finding> created,
                           Clock clock) {
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.store = Objects.requireNonNull(store, "store");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.dedupe = Objects.requireNonNull(dedupe, "dedupe");
        this.history = Objects.requireNonNull(history, "history");
        this.historyTimeout = Objects.requireNonNull(historyTimeout, "historyTimeout");
        this.created = Objects.requireNonNull(created, "created");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CompletableFuture<Optional<Finding>> report(DetectedCandidate detected) {
        Instant now = clock.instant();
        return store.isDuplicate(detected.candidate(), now, dedupe)
                .thenCompose(duplicate -> duplicate
                        ? CompletableFuture.completedFuture(Optional.<DetectedCandidate>empty())
                        : attributed(detected, now).thenCompose(this::admitted))
                .thenCompose(admitted -> admitted
                        .map(candidate -> store.insertUnlessDuplicate(candidate, now, dedupe))
                        .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty())))
                .thenCompose(stored -> stored.isEmpty()
                        ? CompletableFuture.completedFuture(stored)
                        : mainThread.supply(() -> {
                            announce(stored.get());
                            created.accept(stored.get());
                            return stored;
                        }));
    }

    public CompletableFuture<List<Finding>> reportAll(List<DetectedCandidate> candidates) {
        CompletableFuture<List<Finding>> reported = CompletableFuture.completedFuture(List.of());
        for (DetectedCandidate candidate : candidates) {
            reported = reported.thenCompose(findings -> report(candidate).thenApply(stored -> {
                if (stored.isEmpty()) {
                    return findings;
                }
                List<Finding> all = new ArrayList<>(findings);
                all.add(stored.get());
                return List.copyOf(all);
            }));
        }
        return reported;
    }

    private CompletableFuture<DetectedCandidate> attributed(DetectedCandidate detected, Instant now) {
        FindingCandidate candidate = detected.candidate();
        if (!candidate.players().isEmpty() || history.isEmpty()) {
            return CompletableFuture.completedFuture(detected);
        }
        return history.get().lookup(candidate.world(), candidate.box(), now)
                .orTimeout(historyTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .thenApply(entries -> withPlayers(detected, Builders.of(entries, candidate.box())))
                .exceptionally(error -> {
                    logger.warning("block history lookup for a " + candidate.source().modelKind() + " candidate in "
                            + candidate.world() + " at " + describe(candidate.box()) + " failed: " + error);
                    return detected;
                });
    }

    private static DetectedCandidate withPlayers(DetectedCandidate detected, Set<UUID> players) {
        if (players.isEmpty()) {
            return detected;
        }
        FindingCandidate c = detected.candidate();
        return new DetectedCandidate(new FindingCandidate(c.source(), c.world(), c.box(), c.score(), c.votes(), players,
                c.detail(), c.modelVersion(), c.preview()), detected.input(), detected.terrain());
    }

    private CompletableFuture<Optional<DetectedCandidate>> admitted(DetectedCandidate detected) {
        return mainThread.supply(() -> admitted(detected.candidate()) ? Optional.of(detected) : Optional.empty());
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
