package de.kylekreuter.vistructum.ui.web;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

public final class BukkitGameServer implements GameServer {

    private final String sharePermission;
    private final String rollbackPermission;

    public BukkitGameServer(String sharePermission, String rollbackPermission) {
        this.sharePermission = Objects.requireNonNull(sharePermission, "sharePermission");
        this.rollbackPermission = Objects.requireNonNull(rollbackPermission, "rollbackPermission");
    }

    @Override
    public Optional<String> playerName(UUID player) {
        return Optional.ofNullable(Bukkit.getOfflinePlayer(player).getName());
    }

    @Override
    public boolean canShare(UUID player) {
        return permitted(player, sharePermission);
    }

    @Override
    public boolean canRollback(UUID player) {
        return permitted(player, rollbackPermission);
    }

    @Override
    public Optional<String> dimension(String world) {
        World loaded = Bukkit.getWorld(world);
        if (loaded == null) {
            return Optional.of(world);
        }
        return loaded.equals(Bukkit.getWorlds().getFirst()) ? Optional.empty() : Optional.of(loaded.getKey().asString());
    }

    private static boolean permitted(UUID player, String permission) {
        Player online = Bukkit.getPlayer(player);
        return online != null ? online.hasPermission(permission) : Bukkit.getOfflinePlayer(player).isOp();
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
