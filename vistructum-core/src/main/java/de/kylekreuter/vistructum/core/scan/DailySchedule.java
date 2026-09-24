package de.kylekreuter.vistructum.core.scan;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.logging.Level;

/** starts the fullscan of the configured worlds once a day after a set local time; remembers the last run on disk */
public final class DailySchedule {

    private static final long CHECK_TICKS = 20L * 60;

    private final Plugin plugin;
    private final WorldScanner scanner;
    private final LocalTime at;
    private final List<String> worlds;
    private final Path stateFile;
    private BukkitTask task;

    public DailySchedule(Plugin plugin, WorldScanner scanner, LocalTime at, List<String> worlds, Path stateFile) {
        this.plugin = plugin;
        this.scanner = scanner;
        this.at = at;
        this.worlds = List.copyOf(worlds);
        this.stateFile = stateFile;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::check, CHECK_TICKS, CHECK_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    private void check() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        if (now.toLocalTime().isBefore(at) || today.equals(lastRun()) || scanner.running()) {
            return;
        }
        for (String name : worlds) {
            World world = Bukkit.getWorld(name);
            if (world == null) {
                plugin.getLogger().warning("scan.worlds lists unknown world " + name);
            } else {
                scanner.enqueue(world);
            }
        }
        remember(today);
    }

    private LocalDate lastRun() {
        try {
            return Files.isRegularFile(stateFile) ? LocalDate.parse(Files.readString(stateFile).strip()) : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private void remember(LocalDate day) {
        try {
            Files.createDirectories(stateFile.getParent());
            Files.writeString(stateFile, day.toString());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "could not write " + stateFile, e);
        }
    }
}
