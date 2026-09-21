package de.kylekreuter.vistructum.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelContractTest {

    @Test
    void startLabelSet() {
        assertEquals("bf-bin-1", ModelContract.MODEL_VERSION);
        assertEquals(2, ModelContract.LABELS.size());
        assertTrue(ModelContract.LABELS.containsAll(java.util.List.of("ok", "hakenkreuz")));
        assertEquals(64, ModelContract.GRID_SIZE);
    }
}
