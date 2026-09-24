package de.kylekreuter.vistructum.api;

import java.util.List;

public final class ModelContract {

    public static final String MASK_MODEL_VERSION = "bf-mask-1";
    public static final String SCAN_MODEL_VERSION = "bf-scan-2";
    public static final String FEATURE_SPEC = "fs-1";
    public static final List<String> LABELS = List.of("ok", "hakenkreuz");
    public static final int GRID_SIZE = 64;
    public static final int WINDOW_STRIDE = 24;

    private ModelContract() {
    }
}
