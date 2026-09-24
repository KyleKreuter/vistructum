package de.kylekreuter.vistructum.core.alert;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.kylekreuter.vistructum.core.scene.WorldBox;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Tells online staff about findings and appends them to a JSONL log; main thread only.
 *
 * <p>A finding that overlaps one reported within the dedupe window is dropped: the daily scan sees an old build
 * again every day, and the three projections of one cluster can all fire.
 */
public final class DetectionReporter implements AutoCloseable {

    private final Logger logger;
    private final String permission;
    private final Path logFile;
    private final Duration dedupe;
    private final List<Finding> recent = new ArrayList<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> new Thread(r, "vistructum-findings"));
    private final Gson gson = new Gson();

    public DetectionReporter(Logger logger, String permission, Path logFile, Duration dedupe) {
        this.logger = logger;
        this.permission = permission;
        this.logFile = logFile;
        this.dedupe = dedupe;
    }

    /** reloads the findings of the dedupe window from the log, so a restart does not repeat them */
    public void load() {
        if (!Files.isRegularFile(logFile)) {
            return;
        }
        Instant cutoff = Instant.now().minus(dedupe);
        try {
            for (String line : Files.readAllLines(logFile, StandardCharsets.UTF_8)) {
                Finding finding = parse(line);
                if (finding != null && finding.time().isAfter(cutoff)) {
                    recent.add(finding);
                }
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "could not read " + logFile, e);
        }
    }

    /** @return false when the finding repeats a recent one and was dropped */
    public boolean report(Finding finding) {
        Instant cutoff = finding.time().minus(dedupe);
        recent.removeIf(old -> old.time().isBefore(cutoff));
        if (recent.stream().anyMatch(finding::overlaps)) {
            return false;
        }
        recent.add(finding);
        String line = gson.toJson(toJson(finding)) + "\n";
        writer.execute(() -> append(line));
        logger.warning(describe(finding));
        Component message = message(finding);
        Bukkit.getOnlinePlayers().stream().filter(p -> p.hasPermission(permission)).forEach(p -> p.sendMessage(message));
        return true;
    }

    public int recentCount() {
        return recent.size();
    }

    @Override
    public void close() {
        writer.shutdown();
    }

    private void append(String line) {
        try {
            Files.createDirectories(logFile.getParent());
            Files.writeString(logFile, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            logger.log(Level.WARNING, "could not write " + logFile, e);
        }
    }

    private static String describe(Finding f) {
        WorldBox b = f.box();
        return String.format("Verdacht auf Hakenkreuz (%s, Score %.3f, %d Fenster, %s) in %s bei x=%d..%d y=%d..%d z=%d..%d%s",
                f.source(), f.score(), f.votes(), f.detail(), f.world(), b.minX(), b.maxX(), b.minY(), b.maxY(),
                b.minZ(), b.maxZ(), f.players().isEmpty() ? "" : ", Spieler: " + playerNames(f.players()));
    }

    private static Component message(Finding f) {
        World world = Bukkit.getWorld(f.world());
        String dimension = world == null ? "minecraft:overworld" : world.getKey().asString();
        String teleport = "/execute in " + dimension + " run tp @s " + f.centerX() + " " + (f.box().maxY() + 3) + " " + f.centerZ();
        return Component.text("[Vistructum] ", NamedTextColor.RED)
                .append(Component.text(describe(f), NamedTextColor.YELLOW))
                .append(Component.text(" [TP]", NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.runCommand(teleport))
                        .hoverEvent(HoverEvent.showText(Component.text("Zum Fund teleportieren"))));
    }

    private static String playerNames(Set<UUID> players) {
        return players.stream().map(Bukkit::getOfflinePlayer).map(OfflinePlayer::getName).filter(Objects::nonNull)
                .sorted().collect(Collectors.joining(", "));
    }

    private static JsonObject toJson(Finding f) {
        JsonObject json = new JsonObject();
        json.addProperty("time", f.time().toString());
        json.addProperty("source", f.source());
        json.addProperty("world", f.world());
        WorldBox b = f.box();
        json.add("box", new Gson().toJsonTree(b));
        json.addProperty("score", f.score());
        json.addProperty("votes", f.votes());
        json.add("players", new Gson().toJsonTree(f.players().stream().map(UUID::toString).sorted().toList()));
        json.addProperty("detail", f.detail());
        return json;
    }

    private Finding parse(String line) {
        try {
            JsonObject json = gson.fromJson(line, JsonObject.class);
            if (json == null) {
                return null;
            }
            Set<UUID> players = new HashSet<>();
            json.getAsJsonArray("players").forEach(p -> players.add(UUID.fromString(p.getAsString())));
            return new Finding(json.get("source").getAsString(), json.get("world").getAsString(),
                    gson.fromJson(json.get("box"), WorldBox.class), json.get("score").getAsDouble(),
                    json.get("votes").getAsInt(), players, json.get("detail").getAsString(),
                    Instant.parse(json.get("time").getAsString()));
        } catch (RuntimeException e) {
            logger.fine("skipping unreadable finding line: " + e.getMessage());
            return null;
        }
    }
}
