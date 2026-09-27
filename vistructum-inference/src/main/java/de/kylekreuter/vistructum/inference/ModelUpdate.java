package de.kylekreuter.vistructum.inference;

public record ModelUpdate(ModelKind kind, ModelRelease release, ModelRelease.Entry entry, String currentVersion) {

    public String describe() {
        return "a new " + kind.id() + " model is available: " + entry.version() + " from " + release.tag()
                + " (active: " + (currentVersion != null ? currentVersion : "none") + ")";
    }
}
