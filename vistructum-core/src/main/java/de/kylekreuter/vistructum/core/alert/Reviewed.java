package de.kylekreuter.vistructum.core.alert;

import de.kylekreuter.vistructum.api.Finding;

import java.util.Objects;

public record Reviewed(Finding finding, boolean verdictChanged) {

    public Reviewed {
        Objects.requireNonNull(finding, "finding");
    }
}
