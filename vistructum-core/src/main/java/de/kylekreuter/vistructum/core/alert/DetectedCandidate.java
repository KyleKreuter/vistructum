package de.kylekreuter.vistructum.core.alert;

import de.kylekreuter.vistructum.api.FindingCandidate;

import java.util.Objects;

public record DetectedCandidate(FindingCandidate candidate, ModelInput input) {

    public DetectedCandidate {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(input, "input");
    }
}
