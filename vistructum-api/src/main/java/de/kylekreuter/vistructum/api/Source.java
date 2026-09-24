package de.kylekreuter.vistructum.api;

public enum Source {

    MASK("mask"),
    FULLSCAN("fullscan");

    private final String modelKind;

    Source(String modelKind) {
        this.modelKind = modelKind;
    }

    public String modelKind() {
        return modelKind;
    }
}
