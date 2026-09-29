package de.kylekreuter.vistructum.ui.web;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

public final class BukkitGameServer implements GameServer {

    private static final String CORE_PLUGIN = "vistructum";

    private final String sharePermission;

    public BukkitGameServer(String sharePermission) {
        this.sharePermission = Objects.requireNonNull(sharePermission, "sharePermission");
    }

    @Override
    public Optional<String> playerName(UUID player) {
        return Optional.ofNullable(Bukkit.getOfflinePlayer(player).getName());
    }

    @Override
    public boolean canShare(UUID player) {
        Player online = Bukkit.getPlayer(player);
        return online != null ? online.hasPermission(sharePermission) : Bukkit.getOfflinePlayer(player).isOp();
    }

    @Override
    public Optional<String> dimension(String world) {
        World loaded = Bukkit.getWorld(world);
        if (loaded == null) {
            return Optional.of(world);
        }
        return loaded.equals(Bukkit.getWorlds().getFirst()) ? Optional.empty() : Optional.of(loaded.getKey().asString());
    }

    @Override
    public boolean recordingEnabled() {
        Plugin core = Bukkit.getPluginManager().getPlugin(CORE_PLUGIN);
        return core != null && core.getConfig().getBoolean("recording.enabled");
    }

    public static Map<String, Integer> palette() {
        Map<String, Integer> palette = new TreeMap<>();
        for (Material material : Material.values()) {
            if (material.isLegacy() || !material.isBlock()) {
                continue;
            }
            int rgb = material.createBlockData().getMapColor().asRGB();
            if (rgb != 0) {
                palette.put(material.getKey().asString(), rgb);
            }
        }
        return palette;
    }
}
