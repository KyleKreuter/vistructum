package de.kylekreuter.vistructum.ui.integration.punishment;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PunishmentLogs {

    private PunishmentLogs() {
    }

    public static PunishmentLog connect(PluginManager plugins, Executor mainThread, Executor queries, Clock clock,
                                        Logger logger) {
        try {
            Optional<PunishmentLog> log = plugin(plugins, mainThread, queries, clock);
            if (log.isPresent()) {
                return log.get();
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            logger.log(Level.WARNING, "the punishment plugin cannot be read, the web app shows Minecraft bans", e);
        }
        return new MinecraftBanLog(mainThread, Optional.empty(), clock);
    }

    private static Optional<PunishmentLog> plugin(PluginManager plugins, Executor mainThread, Executor queries,
                                                  Clock clock) throws ReflectiveOperationException {
        if (plugins.isPluginEnabled("LiteBans")) {
            return Optional.of(new LiteBansLog(queries, clock));
        }
        if (plugins.isPluginEnabled("LibertyBans")) {
            return LibertyBansLog.connect();
        }
        Plugin advancedBan = plugins.getPlugin("AdvancedBan");
        if (advancedBan != null && advancedBan.isEnabled()) {
            return Optional.of(AdvancedBanLog.connect(advancedBan.getClass().getClassLoader(), queries));
        }
        Plugin essentials = plugins.getPlugin("Essentials");
        if (essentials != null && essentials.isEnabled()) {
            return Optional.of(new MinecraftBanLog(mainThread, Optional.of(new EssentialsMutes(essentials)), clock));
        }
        return Optional.empty();
    }
}
