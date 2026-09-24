package de.kylekreuter.vistructum.core.alert;

import de.kylekreuter.vistructum.api.BlockBox;
import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.SuspiciousBuildEvent;
import de.kylekreuter.vistructum.core.MainThread;
import org.bukkit.Bukkit;

import java.time.Clock;
import java.time.Duration;
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

    public CompletableFuture<Optional<Finding>> report(FindingDraft draft) {
        return store.insertUnlessDuplicate(draft, clock.instant(), dedupe).thenApply(stored -> {
            stored.ifPresent(finding -> mainThread.run(() -> announce(finding)));
            return stored;
        });
    }

    public CompletableFuture<Integer> reportAll(List<FindingDraft> drafts) {
        CompletableFuture<Integer> reported = CompletableFuture.completedFuture(0);
        for (FindingDraft draft : drafts) {
            reported = reported.thenCompose(count -> report(draft).thenApply(stored -> count + (stored.isPresent() ? 1 : 0)));
        }
        return reported;
    }

    private void announce(Finding finding) {
        Bukkit.getPluginManager().callEvent(new SuspiciousBuildEvent(finding));
        BlockBox box = finding.box();
        logger.warning(String.format(Locale.ROOT, "finding #%d: %s score %.3f votes %d in %s x=%d..%d y=%d..%d z=%d..%d",
                finding.id(), finding.source().modelKind(), finding.score(), finding.votes(), finding.world(),
                box.minX(), box.maxX(), box.minY(), box.maxY(), box.minZ(), box.maxZ()));
    }
}
