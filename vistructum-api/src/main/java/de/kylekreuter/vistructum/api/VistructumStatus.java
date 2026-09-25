package de.kylekreuter.vistructum.api;

import java.util.List;
import java.util.Objects;

/**
 * Aggregated operational state, returned by {@link Vistructum#status()}.
 *
 * @param trackedChanges number of recorded block changes within the observation window of the live path; each
 *                       combination of world, block position and player counts once
 * @param openFindings number of findings without a verdict
 * @param activeScans unmodifiable list of the scans in state {@link ScanStatus#QUEUED} or
 *                    {@link ScanStatus#RUNNING}, ordered by ascending identifier
 * @param inference state of the inference path that serves detections
 */
public record VistructumStatus(int trackedChanges, int openFindings, List<ScanJob> activeScans, InferenceStatus inference) {

    /**
     * Validates the components and copies {@code activeScans} into an unmodifiable list.
     *
     * @throws NullPointerException if {@code activeScans} or {@code inference} is {@code null}, or
     *                              {@code activeScans} contains {@code null}
     */
    public VistructumStatus {
        activeScans = List.copyOf(activeScans);
        Objects.requireNonNull(inference, "inference");
    }
}
