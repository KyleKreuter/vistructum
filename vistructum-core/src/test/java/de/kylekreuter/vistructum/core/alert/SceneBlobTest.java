package de.kylekreuter.vistructum.core.alert;

import de.kylekreuter.vistructum.inference.SurfaceScene;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneBlobTest {

    @Test
    void roundTripKeepsEveryChannel() {
        Random random = new Random(7);
        int width = 37;
        int height = 21;
        int count = width * height;
        short[] blocks = new short[count];
        short[] heights = new short[count];
        byte[] luminance = new byte[count];
        byte[] modified = new byte[count];
        for (int i = 0; i < count; i++) {
            blocks[i] = (short) (random.nextInt(Short.MAX_VALUE + 2) - 1);
            heights[i] = (short) (random.nextInt(384) - 64);
            luminance[i] = (byte) random.nextInt(256);
            modified[i] = (byte) random.nextInt(2);
        }
        SurfaceScene scene = new SurfaceScene(width, height, blocks, heights, luminance, modified);

        FindingStoreTest.assertSceneEquals(scene, SceneBlob.decode(width, height, SceneBlob.encode(scene)));
    }

    @Test
    void maskScenesCompressWell() {
        byte[] modified = new byte[256 * 256];
        Arrays.fill(modified, 0, 400, (byte) 1);
        byte[] blob = SceneBlob.encode(SurfaceScene.maskOnly(256, 256, modified));

        assertTrue(blob.length < 4096, "blob has " + blob.length + " bytes");
    }

    @Test
    void wrongDimensionsAreRejected() {
        byte[] blob = SceneBlob.encode(SurfaceScene.maskOnly(4, 4, new byte[16]));

        assertThrows(IllegalArgumentException.class, () -> SceneBlob.decode(4, 5, blob));
        assertThrows(IllegalArgumentException.class, () -> SceneBlob.decode(4, 3, blob));
        assertThrows(IllegalArgumentException.class, () -> SceneBlob.decode(4, 4, new byte[]{1, 2, 3}));
    }
}
