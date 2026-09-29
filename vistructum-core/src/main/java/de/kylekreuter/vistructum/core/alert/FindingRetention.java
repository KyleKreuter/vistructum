package de.kylekreuter.vistructum.core.alert;

import de.kylekreuter.vistructum.api.Verdict;
import de.kylekreuter.vistructum.core.MainThread;
import de.kylekreuter.vistructum.core.skin.SkinStore;
import de.kylekreuter.vistructum.core.web.WebStore;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public final class FindingRetention {

    private static final long CHECK_TICKS = 20L * 60 * 60;

    private final MainThread mainThread;
    private final FindingStore findings;
    private final SkinStore skins;
    private final WebStore web;
    private final Map<Verdict, Duration> keep;
    private final Logger logger;
    private final Clock clock;
    private BukkitTask task;

    public FindingRetention(MainThread mainThread, FindingStore findings, SkinStore skins, WebStore web,
                            Map<Verdict, Duration> keep, Logger logger, Clock clock) {
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.findings = Objects.requireNonNull(findings, "findings");
        this.skins = Objects.requireNonNull(skins, "skins");
        this.web = Objects.requireNonNull(web, "web");
        this.keep = Map.copyOf(keep);
        this.logger = Objects.requireNonNull(logger, "logger");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(mainThread.plugin(), this::purge, 0L, CHECK_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    public CompletableFuture<Integer> purge() {
        Instant now = clock.instant();
        CompletableFuture<Integer> deleted = CompletableFuture.completedFuture(0);
        for (Map.Entry<Verdict, Duration> entry : keep.entrySet()) {
            deleted = deleted.thenCompose(count -> findings.deleteReviewedBefore(entry.getKey(),
                    now.minus(entry.getValue())).thenApply(more -> count + more));
        }
        return deleted.thenCompose(findingCount -> skins.deleteUnreferenced()
                        .thenCombine(web.deleteExpired(now), (skinCount, grantCount) -> {
                            if (findingCount > 0 || skinCount > 0) {
                                logger.info("retention deleted " + findingCount + " reviewed findings and " + skinCount
                                        + " player skins");
                            }
                            return findingCount;
                        }))
                .exceptionally(error -> {
                    logger.warning("retention cleanup failed: " + error.getMessage());
                    return 0;
                });
    }
}
