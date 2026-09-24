package de.kylekreuter.vistructum.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface VistructumApi {

    CompletableFuture<Optional<Finding>> finding(long id);

    CompletableFuture<List<Finding>> openFindings(int limit);

    CompletableFuture<List<Finding>> findingsSince(Instant since, int limit);

    CompletableFuture<Optional<Finding>> review(long id, Verdict verdict, String reviewer);

    CompletableFuture<Optional<Preview>> preview(long id);

    CompletableFuture<Optional<byte[]>> renderPreview(long id);

    CompletableFuture<Optional<ScanJob>> requestScan(String world);

    CompletableFuture<List<ScanJob>> activeScans();

    CompletableFuture<Integer> cancelScans();

    CompletableFuture<VistructumStatus> status();
}
