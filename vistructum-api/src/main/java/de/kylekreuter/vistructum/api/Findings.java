package de.kylekreuter.vistructum.api;

import java.time.Instant;
import java.util.List;
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
     * <p>The first page contains at most {@link FindingQuery#limit()} findings. It starts after the first
     * {@link FindingQuery#offset()} findings that match the criteria and lie below the paging cursor
     * {@link FindingQuery#beforeId()}. Further pages are obtained through {@link Page#next()}, which continues
     * strictly below the smallest identifier of the current page with an offset of {@code 0}. Findings created
     * after the first page was loaded therefore neither shift nor duplicate entries of later pages. Numbered pages
     * are addressed by the offset alone; findings created between two such requests shift their entries.
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
     * size {@link FindingQuery#limit()}, the paging cursor {@link FindingQuery#beforeId()} and the offset
     * {@link FindingQuery#offset()} are ignored, so the result is the total over all pages.
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

    /**
     * Loads the thumbnail stored for a finding.
     *
     * @param id identifier of the finding
     * @return a future completing with the thumbnail, or with an empty {@link Optional} if no finding has this
     *         identifier or no thumbnail has been stored for it yet
     * @see #storeThumbnail(long, Thumbnail)
     */
    CompletableFuture<Optional<Thumbnail>> thumbnail(long id);

    /**
     * Stores the thumbnail of a finding, replacing a thumbnail stored earlier.
     *
     * <p>The thumbnail is deleted together with its finding.
     *
     * @param id identifier of the finding
     * @param thumbnail thumbnail to store
     * @return a future completing with {@code true} once the thumbnail is stored, or with {@code false} if no
     *         finding has this identifier
     * @throws NullPointerException if {@code thumbnail} is {@code null}
     */
    CompletableFuture<Boolean> storeThumbnail(long id, Thumbnail thumbnail);

    /**
     * Selects findings for which no thumbnail has been stored, ordered by ascending identifier.
     *
     * @param afterId only findings with an identifier greater than this value are selected; {@code 0} selects from
     *                the first finding on
     * @param limit maximum number of findings to return, at least {@code 1}
     * @return a future completing with at most {@code limit} findings without thumbnail; the list is unmodifiable
     *         and empty if there are none
     * @throws IllegalArgumentException if {@code limit} is smaller than {@code 1}
     */
    CompletableFuture<List<Finding>> withoutThumbnail(long afterId, int limit);

    /**
     * Counts the verdicts of all reviewed findings per detection path.
     *
     * @return a future completing with one entry per {@link Source}, in declaration order of the enum; paths without
     *         reviewed findings are included with zero counts
     * @see SourcePrecision
     */
    CompletableFuture<List<SourcePrecision>> precision();

    /**
     * Writes all reviewed findings as training data into a new directory below the data folder of the core plugin.
     *
     * <p>Every finding with a verdict and a stored model input scene is written, ordered by ascending identifier.
     * The directory name is derived from the server clock. Files are written off the main thread; the returned
     * future completes once all files are closed.
     *
     * @return a future completing with the location and size of the export
     * @see TrainingExport
     */
    CompletableFuture<TrainingExport> exportTraining();

    /**
     * Loads the raster the detection model saw for a finding.
     *
     * @param id identifier of the finding
     * @return a future completing with the scene, or with an empty {@link Optional} if no finding has this
     *         identifier or no scene was stored for it
     */
    CompletableFuture<Optional<FindingScene>> scene(long id);

    /**
     * Loads the heatmap of a finding, computing and storing it first if it does not exist yet.
     *
     * <p>Computing a heatmap runs the detection model several hundred times and takes seconds of processor time.
     * It runs off the main thread; a stored heatmap is returned without computation.
     *
     * @param id identifier of the finding
     * @return a future completing with the heatmap, or with an empty {@link Optional} if no finding has this
     *         identifier or no scene was stored for it
     * @see #scene(long)
     */
    CompletableFuture<Optional<Heatmap>> heatmap(long id);

    /**
     * Reports whether a terrain volume was stored for a finding.
     *
     * @param id identifier of the finding
     * @return a future completing with {@code true} if {@link #terrain(long)} would return a value
     */
    CompletableFuture<Boolean> hasTerrain(long id);

    /**
     * Loads the blocks around a full scan finding as the world scan read them.
     *
     * @param id identifier of the finding
     * @return a future completing with the terrain, or with an empty {@link Optional} if no finding has this
     *         identifier or no terrain was stored for it
     * @see FindingTerrain
     */
    CompletableFuture<Optional<FindingTerrain>> terrain(long id);

    /**
     * Reports whether evidence was secured for a finding.
     *
     * @param id identifier of the finding
     * @return a future completing with {@code true} if {@link #evidence(long)} would return a value
     */
    CompletableFuture<Boolean> hasEvidence(long id);

    /**
     * Loads the evidence secured when a finding was created.
     *
     * @param id identifier of the finding
     * @return a future completing with the evidence, or with an empty {@link Optional} if no finding has this
     *         identifier or no evidence was secured for it
     * @see Evidence
     */
    CompletableFuture<Optional<Evidence>> evidence(long id);

    /**
     * Aggregates finding and review counts for a time range.
     *
     * @param from start of the range, inclusive
     * @param to end of the range, exclusive
     * @return a future completing with the counts
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if {@code to} is before {@code from}
     */
    CompletableFuture<FindingStats> stats(Instant from, Instant to);

    /**
     * Loads the activity log of reviews and evidence links, newest first.
     *
     * @param before exclusive upper bound for the time of the returned entries, used as the paging cursor
     * @param limit maximum number of entries, between {@code 1} and {@link FindingQuery#MAX_LIMIT} inclusive
     * @return a future completing with at most {@code limit} entries; the list is unmodifiable
     * @throws NullPointerException if {@code before} is {@code null}
     * @throws IllegalArgumentException if {@code limit} lies outside the permitted range
     */
    CompletableFuture<List<Activity>> activity(Instant before, int limit);
}
