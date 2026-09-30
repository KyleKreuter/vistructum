package de.kylekreuter.vistructum.core.history;

import de.kylekreuter.vistructum.api.BlockBox;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface BlockHistory extends AutoCloseable {

    CompletableFuture<List<HistoryEntry>> lookup(String world, BlockBox region, Instant until);

    CompletableFuture<Integer> rollback(String world, BlockBox region, Set<String> players, Duration since);

    @Override
    void close();
}
