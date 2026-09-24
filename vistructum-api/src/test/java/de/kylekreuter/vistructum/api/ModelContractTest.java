package de.kylekreuter.vistructum.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelContractTest {

    @Test
    void startLabelSet() {
        assertEquals("bf-mask-1", ModelContract.MASK_MODEL_VERSION);
        assertEquals("bf-scan-2", ModelContract.SCAN_MODEL_VERSION);
        assertEquals("fs-1", ModelContract.FEATURE_SPEC);
        assertEquals(2, ModelContract.LABELS.size());
        assertTrue(ModelContract.LABELS.containsAll(java.util.List.of("ok", "hakenkreuz")));
        assertEquals(64, ModelContract.GRID_SIZE);
        assertEquals(24, ModelContract.WINDOW_STRIDE);
    }
}
