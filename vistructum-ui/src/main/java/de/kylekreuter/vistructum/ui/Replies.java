package de.kylekreuter.vistructum.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

public final class Replies {

    private Replies() {
    }

    public static <T> void when(CommandSender sender, CompletableFuture<T> future, Consumer<T> then) {
        future.whenComplete((value, error) -> {
            if (error != null) {
                error(sender, "Fehler: " + message(error));
            } else {
                then.accept(value);
            }
        });
    }

    public static void info(CommandSender sender, String text) {
        sender.sendMessage(ChatViews.prefix().append(Component.text(text, NamedTextColor.GRAY)));
    }

    public static void error(CommandSender sender, String text) {
        sender.sendMessage(ChatViews.prefix().append(Component.text(text, NamedTextColor.RED)));
    }

    private static String message(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        return Objects.requireNonNullElse(cause.getMessage(), cause.getClass().getSimpleName());
    }
}
