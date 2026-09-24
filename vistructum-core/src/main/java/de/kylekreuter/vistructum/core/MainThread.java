package de.kylekreuter.vistructum.core;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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

    public <T> CompletableFuture<T> handOff(CompletableFuture<T> source) {
        CompletableFuture<T> result = new CompletableFuture<>();
        source.whenComplete((value, error) -> {
            Runnable complete = () -> {
                if (error != null) {
                    result.completeExceptionally(unwrap(error));
                } else {
                    result.complete(value);
                }
            };
            if (Bukkit.isPrimaryThread() || !plugin.isEnabled()) {
                complete.run();
                return;
            }
            try {
                Bukkit.getScheduler().runTask(plugin, complete);
            } catch (IllegalStateException e) {
                complete.run();
            }
        });
        return result;
    }

    public Plugin plugin() {
        return plugin;
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }
}
