package de.kylekreuter.vistructum.api;

import java.time.Instant;

/**
 * Snapshot of a full world scan.
 *
 * <p>A scan divides the generated area of a world into tiles and examines them one after another. The counters
 * reflect the stored state at the time the snapshot was taken.
 *
 * @param id unique, positive identifier, assigned in ascending order of creation and never reused
 * @param world name of the scanned world
 * @param cause trigger of the scan
 * @param status processing state
 * @param tilesTotal number of planned tiles; {@code 0} while the scan is {@link ScanStatus#QUEUED}
 * @param tilesDone number of processed tiles, including failed tiles
 * @param findings number of findings stored from processed tiles
 * @param failures number of tiles whose processing failed
 * @param createdAt time at which the scan was queued
 */
public record ScanJob(long id, String world, ScanCause cause, ScanStatus status, int tilesTotal, int tilesDone,
                      int findings, int failures, Instant createdAt) {
}
