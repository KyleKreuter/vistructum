package de.kylekreuter.vistructum.api;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface Findings {

    CompletableFuture<Optional<Finding>> get(long id);

    CompletableFuture<Page<Finding>> find(FindingQuery query);

    CompletableFuture<Optional<Finding>> review(long id, Verdict verdict, String reviewer);

    CompletableFuture<Optional<Preview>> preview(long id);

    CompletableFuture<Optional<byte[]>> previewPng(long id);
}
