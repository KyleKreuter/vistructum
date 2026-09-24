package de.kylekreuter.vistructum.api;

import java.util.List;

/**
 * Constants of the contract between the core plugin and the models served by the inference sidecar.
 *
 * <p>The core plugin compares the model versions reported by the sidecar at startup with the versions declared
 * here and logs a mismatch.
 */
public final class ModelContract {

    /**
     * Model version expected for {@link Source#MASK}.
     */
    public static final String MASK_MODEL_VERSION = "bf-mask-1";

    /**
     * Model version expected for {@link Source#FULLSCAN}.
     */
    public static final String SCAN_MODEL_VERSION = "bf-scan-2";

    /**
     * Version of the feature specification that describes the scene channels sent to both models.
     */
    public static final String FEATURE_SPEC = "fs-1";

    /**
     * Class labels of both models, ordered by output index.
     */
    public static final List<String> LABELS = List.of("ok", "hakenkreuz");

    /**
     * Edge length, in blocks, of the square window each model classifies.
     */
    public static final int GRID_SIZE = 64;

    /**
     * Distance, in blocks, between the origins of neighbouring windows.
     */
    public static final int WINDOW_STRIDE = 24;

    private ModelContract() {
    }
}
