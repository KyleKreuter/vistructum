package de.kylekreuter.vistructum.api;

import java.util.List;
import java.util.Objects;

public record VistructumStatus(int trackedChanges, int openFindings, List<ScanJob> activeScans, SidecarStatus sidecar) {

    public VistructumStatus {
        activeScans = List.copyOf(activeScans);
        Objects.requireNonNull(sidecar, "sidecar");
    }
}
