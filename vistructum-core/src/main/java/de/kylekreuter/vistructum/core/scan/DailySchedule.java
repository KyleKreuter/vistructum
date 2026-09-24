package de.kylekreuter.vistructum.core.scan;

import de.kylekreuter.vistructum.core.MainThread;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

public final class DailySchedule {

    private static final long CHECK_TICKS = 20L * 60;

    private final MainThread mainThread;
    private final ScanStore scans;
    private final WorldScanner scanner;
    private final LocalTime at;
    private final List<String> worlds;
    private final Clock clock;
    private BukkitTask task;

    public DailySchedule(MainThread mainThread, ScanStore scans, WorldScanner scanner, LocalTime at, List<String> worlds,
                         Clock clock) {
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.scans = Objects.requireNonNull(scans, "scans");
        this.scanner = Objects.requireNonNull(scanner, "scanner");
        this.at = Objects.requireNonNull(at, "at");
        this.worlds = List.copyOf(worlds);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(mainThread.plugin(), this::check, CHECK_TICKS, CHECK_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    private void check() {
        LocalDateTime now = LocalDateTime.now(clock);
        if (now.toLocalTime().isBefore(at)) {
            return;
        }
        List<String> known = worlds.stream().filter(name -> Bukkit.getWorld(name) != null).toList();
        scans.enqueueDaily(now.toLocalDate(), known, clock.instant()).thenAccept(enqueued -> enqueued.ifPresent(jobs ->
                mainThread.run(() -> {
                    worlds.stream().filter(name -> !known.contains(name)).forEach(name ->
                            mainThread.plugin().getLogger().warning("scan.worlds lists unknown world " + name));
                    if (!jobs.isEmpty()) {
                        scanner.start();
                    }
                })));
    }
}
