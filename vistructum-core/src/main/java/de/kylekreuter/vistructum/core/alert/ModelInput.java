package de.kylekreuter.vistructum.core.alert;

import de.kylekreuter.vistructum.inference.Detection;
import de.kylekreuter.vistructum.inference.ModelKind;
import de.kylekreuter.vistructum.inference.SurfaceScene;

import java.util.Objects;

public record ModelInput(ModelKind kind, SurfaceScene scene, int top, int left, int bottom, int right) {

    public ModelInput {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(scene, "scene");
        if (bottom <= top || right <= left) {
            throw new IllegalArgumentException("empty window " + top + "," + left + " to " + bottom + "," + right);
        }
    }

    public static ModelInput of(ModelKind kind, SurfaceScene scene, Detection detection) {
        return new ModelInput(kind, scene, detection.top(), detection.left(), detection.bottom(), detection.right());
    }
}
