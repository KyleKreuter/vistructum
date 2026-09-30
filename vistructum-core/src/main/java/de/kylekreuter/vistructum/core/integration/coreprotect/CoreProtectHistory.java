package de.kylekreuter.vistructum.core.integration.coreprotect;

import de.kylekreuter.vistructum.api.BlockAction;
import de.kylekreuter.vistructum.api.BlockBox;
import de.kylekreuter.vistructum.core.history.BlockHistory;
import de.kylekreuter.vistructum.core.history.HistoryEntry;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public final class CoreProtectHistory implements BlockHistory {

    private static final int MIN_API_VERSION = 10;
    private static final int REMOVED = 0;
    private static final int PLACED = 1;
    private static final List<Integer> BLOCK_ACTIONS = List.of(REMOVED, PLACED);
    private static final String NON_PLAYER_PREFIX = "#";

    private final CoreProtectAPI api;
    private final Duration window;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "vistructum-coreprotect"));

    private CoreProtectHistory(CoreProtectAPI api, Duration window) {
        this.api = api;
        this.window = window;
    }

    public static Optional<BlockHistory> connect(Plugin plugin, Duration window, Logger logger) {
        Objects.requireNonNull(window, "window");
        if (!(plugin instanceof CoreProtect coreProtect)) {
            logger.warning("the plugin named CoreProtect is not CoreProtect, the integration stays off");
            return Optional.empty();
        }
        CoreProtectAPI api = coreProtect.getAPI();
        if (!api.isEnabled()) {
            logger.warning("the CoreProtect API is disabled in the CoreProtect config, the integration stays off");
            return Optional.empty();
        }
        if (api.APIVersion() < MIN_API_VERSION) {
            logger.warning("CoreProtect API " + api.APIVersion() + " is older than " + MIN_API_VERSION
                    + ", the integration stays off");
            return Optional.empty();
        }
        return Optional.of(new CoreProtectHistory(api, window));
    }

    @Override
    public CompletableFuture<List<HistoryEntry>> lookup(String world, BlockBox region, Instant until) {
        return CompletableFuture.supplyAsync(() -> entries(world, region, until.toEpochMilli()), executor);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private List<HistoryEntry> entries(String worldName, BlockBox region, long until) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return List.of();
        }
        Location center = new Location(world, region.centerX() + 0.5, Math.floorDiv(region.minY() + region.maxY(), 2),
                region.centerZ() + 0.5);
        List<String[]> rows = api.performLookup(Math.toIntExact(window.toSeconds()), null, null, null, null,
                BLOCK_ACTIONS, radius(region), center);
        if (rows == null) {
            return List.of();
        }
        Map<String, Optional<UUID>> players = new HashMap<>();
        return rows.stream()
                .map(api::parseResult)
                .filter(row -> worldName.equals(row.worldName()))
                .filter(row -> region.contains(row.getX(), row.getY(), row.getZ()))
                .filter(row -> !row.isRolledBack())
                .filter(row -> row.getTimestamp() <= until)
                .filter(row -> row.getPlayer() != null && !row.getPlayer().startsWith(NON_PLAYER_PREFIX))
                .filter(row -> row.getActionId() == REMOVED || row.getActionId() == PLACED)
                .filter(row -> row.getBlockData() != null)
                .map(row -> new HistoryEntry(row.getX(), row.getY(), row.getZ(), row.getPlayer(),
                        players.computeIfAbsent(row.getPlayer(), CoreProtectHistory::uuid),
                        row.getActionId() == PLACED ? BlockAction.PLACE : BlockAction.BREAK,
                        row.getBlockData().getAsString(), row.getTimestamp()))
                .toList()
                .reversed();
    }

    static int radius(BlockBox region) {
        int extent = Math.max(region.maxX() - region.minX(), Math.max(region.maxY() - region.minY(),
                region.maxZ() - region.minZ()));
        return extent / 2 + 1;
    }

    private static Optional<UUID> uuid(String name) {
        OfflinePlayer player = Bukkit.getOfflinePlayerIfCached(name);
        return player == null ? Optional.empty() : Optional.of(player.getUniqueId());
    }
}
