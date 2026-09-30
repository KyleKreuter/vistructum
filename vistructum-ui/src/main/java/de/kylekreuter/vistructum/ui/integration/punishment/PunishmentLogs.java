package de.kylekreuter.vistructum.ui.integration.punishment;

import org.bukkit.plugin.PluginManager;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PunishmentLogs {

    private PunishmentLogs() {
    }

    public static Optional<PunishmentLog> connect(PluginManager plugins, Logger logger) {
        if (!plugins.isPluginEnabled("LibertyBans")) {
            return Optional.empty();
        }
        try {
            return LibertyBansLog.connect();
        } catch (LinkageError | RuntimeException e) {
            logger.log(Level.WARNING, "LibertyBans cannot be read, the web app shows no punishments", e);
            return Optional.empty();
        }
    }
}
