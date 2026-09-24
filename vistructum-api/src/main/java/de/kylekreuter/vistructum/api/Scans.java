package de.kylekreuter.vistructum.api;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Control of full world scans.
 *
 * <p>A scan examines the surface of every generated chunk of a world with the fullscan model and reports
 * detections as findings. At most one scan per world is active, that is {@link ScanStatus#QUEUED} or
 * {@link ScanStatus#RUNNING}, at any time. Active scans are processed one after another in ascending order of
 * their identifiers and resume at the next unprocessed tile after a server restart.
 *
 * <p>All returned futures complete on the server main thread as specified by {@link Vistructum}.
 *
 * @see Vistructum#scans()
 * @see ScanStartedEvent
 * @see ScanProgressEvent
 * @see ScanFinishedEvent
 */
public interface Scans {

    /**
     * Queues a scan of a world with the cause {@link ScanCause#MANUAL}.
     *
     * @param world name of a loaded world
     * @return a future completing with the queued scan in state {@link ScanStatus#QUEUED}, or with an empty
     *         {@link Optional} if a scan of this world is already active; the future completes exceptionally
     *         with an {@link IllegalArgumentException} if no loaded world has this name
     * @throws NullPointerException if {@code world} is {@code null}
     */
    CompletableFuture<Optional<ScanJob>> request(String world);

    /**
     * Lists all active scans.
     *
     * @return a future completing with every scan in state {@link ScanStatus#QUEUED} or {@link ScanStatus#RUNNING},
     *         ordered by ascending identifier; the list is unmodifiable and empty if no scan is active
     */
    CompletableFuture<List<ScanJob>> active();

    /**
     * Cancels every active scan.
     *
     * <p>The scan in progress stops processing further tiles immediately and its progress is no longer recorded.
     * Detections of a tile whose inference is in flight at the time of cancellation may still be stored as
     * findings. Findings that were already stored remain. A {@link ScanFinishedEvent} is fired on the main thread for every cancelled scan
     * before the returned future completes.
     *
     * @return a future completing with the cancelled scans in state {@link ScanStatus#CANCELLED}, ordered by
     *         ascending identifier; the list is unmodifiable and empty if no scan was active
     */
    CompletableFuture<List<ScanJob>> cancelAll();
}
