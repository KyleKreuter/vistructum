package de.kylekreuter.vistructum.ui.web.logging;

import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.LegacyAbstractLogger;
import org.slf4j.helpers.MessageFormatter;

import java.io.Serial;
import java.util.logging.Logger;

final class PluginLogger extends LegacyAbstractLogger {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient Logger target;

    PluginLogger(String name) {
        this.name = name;
        this.target = target(name);
    }

    @Override
    public boolean isTraceEnabled() {
        return false;
    }

    @Override
    public boolean isDebugEnabled() {
        return false;
    }

    @Override
    public boolean isInfoEnabled() {
        return false;
    }

    @Override
    public boolean isWarnEnabled() {
        return target.isLoggable(java.util.logging.Level.WARNING);
    }

    @Override
    public boolean isErrorEnabled() {
        return target.isLoggable(java.util.logging.Level.SEVERE);
    }

    @Override
    protected String getFullyQualifiedCallerName() {
        return null;
    }

    @Override
    protected void handleNormalizedLoggingCall(Level level, Marker marker, String message, Object[] arguments,
                                               Throwable error) {
        java.util.logging.Level mapped = level == Level.ERROR ? java.util.logging.Level.SEVERE
                : java.util.logging.Level.WARNING;
        target.log(mapped, MessageFormatter.basicArrayFormat(message, arguments), error);
    }

    private static Logger target(String name) {
        try {
            return JavaPlugin.getProvidingPlugin(PluginLogger.class).getLogger();
        } catch (IllegalArgumentException | IllegalStateException notAPlugin) {
            return Logger.getLogger(name);
        }
    }
}
