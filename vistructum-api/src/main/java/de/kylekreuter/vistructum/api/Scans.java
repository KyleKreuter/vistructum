package de.kylekreuter.vistructum.api;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface Scans {

    CompletableFuture<Optional<ScanJob>> request(String world);

    CompletableFuture<List<ScanJob>> active();

    CompletableFuture<List<ScanJob>> cancelAll();
}
