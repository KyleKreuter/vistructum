package de.kylekreuter.vistructum.core;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class MainThread {

    private final Plugin plugin;

    public MainThread(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public void run(Runnable action) {
        if (!plugin.isEnabled()) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            action.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, action);
        }
    }

    public <T> CompletableFuture<T> supply(Supplier<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        if (!plugin.isEnabled()) {
            result.completeExceptionally(new IllegalStateException("plugin disabled"));
            return result;
        }
        run(() -> {
            try {
                result.complete(action.get());
            } catch (RuntimeException e) {
                result.completeExceptionally(e);
            }
        });
        return result;
    }

    public Plugin plugin() {
        return plugin;
    }
}
