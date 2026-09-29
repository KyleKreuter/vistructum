package de.kylekreuter.vistructum.core.alert;

import de.kylekreuter.vistructum.api.Source;

import java.util.Objects;

public record StoredScene(long findingId, Source source, ModelInput input) {

    public StoredScene {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(input, "input");
    }
}
