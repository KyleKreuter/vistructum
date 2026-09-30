package de.kylekreuter.vistructum.core.history;

import de.kylekreuter.vistructum.api.BlockBox;
import de.kylekreuter.vistructum.api.RollbackResult;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface BlockHistory extends AutoCloseable {

    CompletableFuture<List<HistoryEntry>> lookup(String world, BlockBox region, Instant until);

    CompletableFuture<RollbackResult> restore(String world, List<BlockRestore> restores);

    @Override
    void close();
}
