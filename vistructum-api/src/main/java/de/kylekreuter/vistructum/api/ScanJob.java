package de.kylekreuter.vistructum.api;

import java.time.Instant;

public record ScanJob(long id, String world, ScanCause cause, ScanStatus status, int tilesTotal, int tilesDone,
                      int findings, int failures, Instant createdAt) {
}
