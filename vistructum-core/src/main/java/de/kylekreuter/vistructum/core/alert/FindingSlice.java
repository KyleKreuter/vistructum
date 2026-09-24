package de.kylekreuter.vistructum.core.alert;

import de.kylekreuter.vistructum.api.Finding;

import java.util.List;

public record FindingSlice(List<Finding> findings, boolean more) {

    public FindingSlice {
        findings = List.copyOf(findings);
    }
}
