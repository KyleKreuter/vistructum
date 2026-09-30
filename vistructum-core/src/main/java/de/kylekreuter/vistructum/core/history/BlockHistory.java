package de.kylekreuter.vistructum.core.history;

import de.kylekreuter.vistructum.api.BlockBox;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface BlockHistory extends AutoCloseable {

    CompletableFuture<List<HistoryEntry>> lookup(String world, BlockBox region, Instant until);

    @Override
    void close();
}
