package de.kylekreuter.vistructum.inference;

import ai.onnxruntime.OrtEnvironment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcclusionTest {

    @Test
    void searchOriginsCoverTheWindowAndStayInsideTheFeatures() {
        assertEquals(List.of(0, 8, 16, 24), Occlusion.origins(0, 88, 100));
        assertEquals(List.of(36), Occlusion.origins(40, 104, 100));
        assertEquals(List.of(0), Occlusion.origins(-2, 62, 64));
        assertEquals(List.of(10, 18, 20), Occlusion.origins(10, 84, 90));
    }

    @Test
    void mapCoversTheSceneAndOnlyTheModelWindow() throws Exception {
        int width = 96;
        int height = 70;
        byte[] modified = new byte[width * height];
        for (int row = 20; row < 50; row++) {
            modified[row * width + 40] = 1;
            modified[35 * width + row + 20] = 1;
        }
        SurfaceScene scene = SurfaceScene.maskOnly(width, height, modified);
        try (Model model = Model.load(OrtEnvironment.getEnvironment(), TestModels.bundled(ModelKind.MASK), 1)) {
            OcclusionMap map = model.occlusion(scene, 0, 8, 64, 88);

            assertEquals(width, map.width());
            assertEquals(height, map.height());
            assertEquals(model.info().version(), map.modelVersion());
            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    int value = map.values()[row * width + col];
                    assertTrue(value >= 0 && value <= Occlusion.MAX_VALUE);
                    if (row >= Contract.GRID || col < 8 || col >= 8 + Contract.GRID + 24) {
                        assertEquals(0, value, row + "," + col);
                    }
                }
            }
        }
    }
}
