package de.kylekreuter.vistructum.api;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Access to persisted findings.
 *
 * <p>All returned futures complete on the server main thread as specified by {@link Vistructum}. Identifiers are
 * the values of {@link Finding#id()}; they are positive, assigned in ascending order of creation and never reused.
 *
 * @see Vistructum#findings()
 */
public interface Findings {

    /**
     * Loads a single finding.
     *
     * @param id identifier of the finding
     * @return a future completing with the finding, or with an empty {@link Optional} if no finding has this
     *         identifier
     */
    CompletableFuture<Optional<Finding>> get(long id);

    /**
     * Selects findings that match the given query, ordered by descending identifier.
     *
     * <p>The first page contains at most {@link FindingQuery#limit()} findings. Further pages are obtained through
     * {@link Page#next()}, which continues strictly below the smallest identifier of the current page. Findings
     * created after the first page was loaded therefore neither shift nor duplicate entries of later pages.
     *
     * @param query selection criteria and page size
     * @return a future completing with the first page of matching findings; the page is empty if nothing matches
     * @throws NullPointerException if {@code query} is {@code null}
     */
    CompletableFuture<Page<Finding>> find(FindingQuery query);

    /**
     * Counts the findings that match the given query.
     *
     * <p>World, source, review state and creation time are applied as in {@link #find(FindingQuery)}. The page
     * size {@link FindingQuery#limit()} and the paging cursor {@link FindingQuery#beforeId()} are ignored, so the
     * result is the total over all pages.
     *
     * @param query selection criteria
     * @return a future completing with the number of matching findings
     * @throws NullPointerException if {@code query} is {@code null}
     */
    CompletableFuture<Long> count(FindingQuery query);

    /**
     * Records a verdict for a finding.
     *
     * <p>An existing verdict is replaced. The review time is taken from the server clock. After the verdict has
     * been stored, a {@link FindingReviewedEvent} is fired on the main thread before the returned future
     * completes.
     *
     * @param id identifier of the finding
     * @param verdict verdict to record
     * @param reviewer name of the reviewing party as it is to be stored and displayed
     * @return a future completing with the reviewed finding, or with an empty {@link Optional} if no finding has
     *         this identifier; no event is fired in the latter case
     * @throws NullPointerException if {@code verdict} or {@code reviewer} is {@code null}
     */
    CompletableFuture<Optional<Finding>> review(long id, Verdict verdict, String reviewer);

    /**
     * Loads the greyscale preview stored with a finding.
     *
     * @param id identifier of the finding
     * @return a future completing with the preview, or with an empty {@link Optional} if no finding has this
     *         identifier
     * @see Preview
     */
    CompletableFuture<Optional<Preview>> preview(long id);

    /**
     * Renders the preview of a finding as a PNG image.
     *
     * <p>The image is an 8-bit greyscale PNG. Each preview pixel is scaled to a square of four by four image
     * pixels without interpolation, so the image measures four times the preview width by four times the
     * preview height.
     *
     * @param id identifier of the finding
     * @return a future completing with the encoded PNG bytes, or with an empty {@link Optional} if no finding has
     *         this identifier; the returned array is owned by the caller
     */
    CompletableFuture<Optional<byte[]>> previewPng(long id);
}
