package de.kylekreuter.vistructum.api;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * One page of an ordered, cursor-based result set.
 *
 * <p>A page is an immutable snapshot. Its items reflect the stored state at the time the page was loaded; later
 * changes to the underlying records are not reflected.
 *
 * @param <T> type of the items
 * @see Findings#find(FindingQuery)
 */
public interface Page<T> {

    /**
     * Returns the items of this page in result order.
     *
     * @return an unmodifiable list containing at most the page size requested by the originating query
     */
    List<T> items();

    /**
     * Reports whether further items followed this page at the time it was loaded.
     *
     * @return {@code true} if at least one further item existed when this page was loaded
     */
    boolean hasNext();

    /**
     * Loads the page that follows this page, using the same selection criteria and page size.
     *
     * <p>The following page starts strictly after the last item of this page. Because records may be removed or
     * change state in the meantime, the following page can be empty even though {@link #hasNext()} returned
     * {@code true}.
     *
     * @return a future completing on the main thread with the following page; if {@link #hasNext()} returns
     *         {@code false}, the returned future is already completed exceptionally with an
     *         {@link IllegalStateException}
     */
    CompletableFuture<Page<T>> next();
}
