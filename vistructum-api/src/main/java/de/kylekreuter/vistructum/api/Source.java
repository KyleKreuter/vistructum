package de.kylekreuter.vistructum.api;

/**
 * Detection path that produced a finding.
 */
public enum Source {

    /**
     * Live path: clusters of recent player block changes, examined as binary change masks along three axes.
     */
    MASK("mask"),

    /**
     * Scan path: the world surface, examined tile by tile during a {@link ScanJob}.
     */
    FULLSCAN("fullscan");

    private final String modelKind;

    Source(String modelKind) {
        this.modelKind = modelKind;
    }

    /**
     * Returns the identifier of the sidecar model that serves this detection path.
     *
     * @return the model kind, also used as key in {@link SidecarStatus#models()}
     */
    public String modelKind() {
        return modelKind;
    }
}
