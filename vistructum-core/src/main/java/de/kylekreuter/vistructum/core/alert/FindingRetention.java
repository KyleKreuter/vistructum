package de.kylekreuter.vistructum.core.alert;

import de.kylekreuter.vistructum.core.MainThread;
import de.kylekreuter.vistructum.core.face.FaceStore;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.logging.Logger;

public final class FindingRetention {

    private static final long CHECK_TICKS = 20L * 60 * 60;

    private final MainThread mainThread;
    private final FindingStore findings;
    private final FaceStore faces;
    private final Duration keepReviewed;
    private final Logger logger;
    private final Clock clock;
    private BukkitTask task;

    public FindingRetention(MainThread mainThread, FindingStore findings, FaceStore faces, Duration keepReviewed,
                            Logger logger, Clock clock) {
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.findings = Objects.requireNonNull(findings, "findings");
        this.faces = Objects.requireNonNull(faces, "faces");
        this.keepReviewed = Objects.requireNonNull(keepReviewed, "keepReviewed");
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

    private void purge() {
        findings.deleteReviewedBefore(clock.instant().minus(keepReviewed))
                .thenCompose(deleted -> faces.deleteUnreferenced().thenAccept(orphans -> {
                    if (deleted > 0 || orphans > 0) {
                        logger.info("retention deleted " + deleted + " reviewed findings and " + orphans + " player faces");
                    }
                }))
                .exceptionally(error -> {
                    logger.warning("retention cleanup failed: " + error.getMessage());
                    return null;
                });
    }
}
