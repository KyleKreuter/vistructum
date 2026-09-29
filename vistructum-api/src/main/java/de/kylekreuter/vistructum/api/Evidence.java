package de.kylekreuter.vistructum.api;

import java.util.List;
import java.util.Objects;

/**
 * Proof of how the structure of a finding was built, secured when the finding was created.
 *
 * <p>Evidence exists only for findings of the live check and only while recording is enabled on the server. It
 * covers the block changes still tracked at that time, which reach back at most as far as the tracking window.
 *
 * @param findingId identifier of the finding the evidence belongs to
 * @param before block states of the box of the finding and its margin before the first recorded change
 * @param changes block changes inside {@code before} in ascending time order; the list is unmodifiable
 * @param recordings movement of every player that was near the box while it was built; the list is unmodifiable
 */
public record Evidence(long findingId, BlockVolume before, List<BlockEvent> changes, List<Recording> recordings) {

    /**
     * Validates the components and copies the lists.
     *
     * @throws NullPointerException if a component or a list entry is {@code null}
     */
    public Evidence {
        Objects.requireNonNull(before, "before");
        changes = List.copyOf(changes);
        recordings = List.copyOf(recordings);
    }
}
