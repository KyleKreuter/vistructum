package de.kylekreuter.vistructum.api;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface Page<T> {

    List<T> items();

    boolean hasNext();

    CompletableFuture<Page<T>> next();
}
