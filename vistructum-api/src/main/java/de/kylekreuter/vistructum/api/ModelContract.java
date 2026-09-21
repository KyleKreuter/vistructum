package de.kylekreuter.vistructum.api;

import java.util.List;

public final class ModelContract {

    public static final String MODEL_VERSION = "bf-bin-1";
    public static final List<String> LABELS = List.of("ok", "hakenkreuz");
    public static final int GRID_SIZE = 64;

    private ModelContract() {
    }
}
