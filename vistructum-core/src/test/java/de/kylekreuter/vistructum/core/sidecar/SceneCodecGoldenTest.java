package de.kylekreuter.vistructum.core.sidecar;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.kylekreuter.vistructum.core.scene.SurfaceScene;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SceneCodecGoldenTest {

    private static final int WIDTH = 5;
    private static final int HEIGHT = 3;

    @Test
    void encodesToTheCommittedGoldenFixture() throws IOException {
        SurfaceScene scene = goldenScene();
        JsonObject actual = SceneCodec.encode("fullscan", scene);
        JsonObject expected = readGoldenFixture();

        assertEquals(expected, actual);

        assertArrayEquals(scene.blocks(), SceneCodecTest.decodeInt16(expected, "blocks", WIDTH * HEIGHT));
        assertArrayEquals(scene.heights(), SceneCodecTest.decodeInt16(expected, "heights", WIDTH * HEIGHT));
        assertArrayEquals(scene.luminance(), SceneCodecTest.decodeUint8(expected, "luminance"));
        assertArrayEquals(scene.modified(), SceneCodecTest.decodeUint8(expected, "modified"));
    }

    static SurfaceScene goldenScene() {
        int count = WIDTH * HEIGHT;
        short[] blocks = new short[count];
        short[] heights = new short[count];
        byte[] luminance = new byte[count];
        byte[] modified = new byte[count];
        for (int i = 0; i < count; i++) {
            blocks[i] = (short) (i - 3);
            heights[i] = (short) ((i - 7) * 100);
            luminance[i] = (byte) ((i * 17) % 256);
        }
        modified[2] = 1;
        modified[5] = 1;
        modified[11] = 1;
        return new SurfaceScene(WIDTH, HEIGHT, blocks, heights, luminance, modified);
    }

    private static JsonObject readGoldenFixture() throws IOException {
        try (Reader reader = new InputStreamReader(
                SceneCodecGoldenTest.class.getResourceAsStream("/infer-request-golden.json"), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
