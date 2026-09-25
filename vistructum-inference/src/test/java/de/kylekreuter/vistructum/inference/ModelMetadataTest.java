package de.kylekreuter.vistructum.inference;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelMetadataTest {

    private static Map<String, String> valid() {
        Map<String, String> meta = new HashMap<>();
        meta.put("vistructum.model_version", "bf-mask-9");
        meta.put("vistructum.kind", "mask");
        meta.put("vistructum.labels", "[\"ok\", \"hakenkreuz\"]");
        meta.put("vistructum.feature_spec", "fs-1");
        meta.put("vistructum.threshold", "0.9600");
        return meta;
    }

    @Test
    void acceptsAnyModelVersionWithTheSupportedFeatureSpec() throws ModelException {
        ModelInfo info = ModelMetadata.validate(valid(), "abc");

        assertEquals("bf-mask-9", info.version());
        assertEquals(0.96, info.threshold());
        assertEquals(1, info.minVotes());
        assertEquals(List.of("ok", "hakenkreuz"), info.labels());
        assertNull(info.prefilter());
    }

    @Test
    void readsTtaAndPrefilter() throws ModelException {
        Map<String, String> meta = valid();
        meta.put("vistructum.tta", "1");
        meta.put("vistructum.prefilter", "0.37");
        meta.put("vistructum.min_votes", "2");

        ModelInfo info = ModelMetadata.validate(meta, "abc");

        assertTrue(info.tta());
        assertEquals(0.37, info.prefilter());
        assertEquals(2, info.minVotes());
    }

    @Test
    void rejectsInvalidMetadata() {
        assertRejected("vistructum.feature_spec", "fs-2", "feature spec");
        assertRejected("vistructum.kind", "other", "unknown model kind");
        assertRejected("vistructum.labels", "[\"a\", \"b\"]", "labels");
        assertRejected("vistructum.labels", "nope", "labels");
        assertRejected("vistructum.threshold", "1.0", "threshold");
        assertRejected("vistructum.min_votes", "0", "min_votes");
        assertRejected("vistructum.tta", "yes", "tta");
        assertRejected("vistructum.prefilter", "0.5", "prefilter needs tta");
        assertRejected("vistructum.model_version", null, "missing");
    }

    private static void assertRejected(String key, String value, String message) {
        Map<String, String> meta = valid();
        if (value == null) {
            meta.remove(key);
        } else {
            meta.put(key, value);
        }
        ModelException error = assertThrows(ModelException.class, () -> ModelMetadata.validate(meta, "abc"));
        assertTrue(error.getMessage().contains(message), error.getMessage());
    }
}
