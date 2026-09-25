package de.kylekreuter.vistructum.ui;

import de.kylekreuter.vistructum.ui.text.Message;
import de.kylekreuter.vistructum.ui.text.Messages;
import org.bukkit.command.CommandSender;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

public final class Replies {

    private Replies() {
    }

    public static <T> void when(CommandSender sender, Messages messages, CompletableFuture<T> future, Consumer<T> then) {
        future.whenComplete((value, error) -> {
            if (error != null) {
                sender.sendMessage(messages.chat(Message.COMMAND_FAILED, Messages.text("reason", reason(error))));
            } else {
                then.accept(value);
            }
        });
    }

    private static String reason(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        return Objects.requireNonNullElse(cause.getMessage(), cause.getClass().getSimpleName());
    }
}
