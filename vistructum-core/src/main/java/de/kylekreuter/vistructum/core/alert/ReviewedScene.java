package de.kylekreuter.vistructum.core.alert;

import de.kylekreuter.vistructum.api.Source;
import de.kylekreuter.vistructum.api.Verdict;

import java.util.Objects;

public record ReviewedScene(long findingId, Source source, Verdict verdict, String modelVersion, ModelInput input) {

    public ReviewedScene {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(modelVersion, "modelVersion");
        Objects.requireNonNull(input, "input");
    }
}
