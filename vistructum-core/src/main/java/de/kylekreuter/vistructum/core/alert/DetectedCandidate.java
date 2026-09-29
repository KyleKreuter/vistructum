package de.kylekreuter.vistructum.core.alert;

import de.kylekreuter.vistructum.api.FindingCandidate;
import de.kylekreuter.vistructum.api.FindingTerrain;

import java.util.Objects;
import java.util.Optional;

public record DetectedCandidate(FindingCandidate candidate, ModelInput input, Optional<FindingTerrain> terrain) {

    public DetectedCandidate {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(terrain, "terrain");
    }

    public DetectedCandidate(FindingCandidate candidate, ModelInput input) {
        this(candidate, input, Optional.empty());
    }

    public DetectedCandidate withTerrain(Optional<FindingTerrain> cut) {
        return new DetectedCandidate(candidate, input, cut);
    }
}
