package de.kylekreuter.vistructum.inference;

import java.util.List;

public final class Contract {

    public static final int GRID = 64;
    public static final int STRIDE = 24;
    public static final String FEATURE_SPEC = "fs-1";
    public static final List<String> LABELS = List.of("ok", "hakenkreuz");
    public static final int POSITIVE = 1;
    public static final String INPUT_NAME = "features";
    public static final String OUTPUT_NAME = "scores";
    public static final int MAX_DETECTIONS = 20;

    static final int HEIGHT_CLIP = 4;
    static final int HEIGHT_CONTEXT = 31;
    static final int HEIGHT_SAMPLE_STEP = 5;
    static final int LUMINANCE_PAD = 128;

    static final String META_VERSION = "vistructum.model_version";
    static final String META_KIND = "vistructum.kind";
    static final String META_LABELS = "vistructum.labels";
    static final String META_FEATURE_SPEC = "vistructum.feature_spec";
    static final String META_THRESHOLD = "vistructum.threshold";
    static final String META_COMMIT = "vistructum.commit";
    static final String META_MIN_VOTES = "vistructum.min_votes";
    static final String META_TTA = "vistructum.tta";
    static final String META_PREFILTER = "vistructum.prefilter";
    static final List<String> REQUIRED_META = List.of(META_VERSION, META_KIND, META_LABELS, META_FEATURE_SPEC,
            META_THRESHOLD);

    private Contract() {
    }
}
