package de.kylekreuter.vistructum.ui.web;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Function;
import java.util.function.Supplier;

public final class Handoff {

    private final Executor worker;
    private final Executor mainThread;

    public Handoff(Executor worker, Executor mainThread) {
        Objects.requireNonNull(worker, "worker");
        this.worker = task -> {
            try {
                worker.execute(task);
            } catch (RejectedExecutionException stopped) {
                task.run();
            }
        };
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
    }

    public <T> CompletableFuture<T> off(CompletableFuture<T> future) {
        return future.thenApplyAsync(Function.identity(), worker);
    }

    public <T> CompletableFuture<T> onMain(Supplier<T> task) {
        return off(CompletableFuture.supplyAsync(task, mainThread));
    }
}
