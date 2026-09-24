package de.kylekreuter.vistructum.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

public final class Replies {

    private final Plugin plugin;

    public Replies(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public <T> void when(CommandSender sender, CompletableFuture<T> future, Consumer<T> then) {
        future.whenComplete((value, error) -> onMainThread(() -> {
            if (error != null) {
                error(sender, "Fehler: " + cause(error).getMessage());
            } else {
                then.accept(value);
            }
        }));
    }

    public static void info(CommandSender sender, String text) {
        sender.sendMessage(ChatViews.prefix().append(Component.text(text, NamedTextColor.GRAY)));
    }

    public static void error(CommandSender sender, String text) {
        sender.sendMessage(ChatViews.prefix().append(Component.text(text, NamedTextColor.RED)));
    }

    private void onMainThread(Runnable action) {
        if (!plugin.isEnabled()) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            action.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, action);
        }
    }

    private static Throwable cause(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }
}
