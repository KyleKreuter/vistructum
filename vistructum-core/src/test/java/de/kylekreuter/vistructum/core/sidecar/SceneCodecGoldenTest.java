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

/**
 * Cross-language golden fixture: {@code SceneCodec.encode} must produce exactly
 * {@code vistructum-core/src/test/resources/infer-request-golden.json}, so a Python test can assert the same
 * values after decoding the fixture through {@code build_scene}.
 *
 * <p>Fixture: a 5 (width) x 3 (height) scene, row-major, {@code index = row * 5 + col}, {@code kind = "fullscan"}.
 * <ul>
 *   <li>{@code blocks[i] = i - 3}: -3, -2, -1 (UNKNOWN), 0, 1, ..., 11 &mdash; covers negative ids and UNKNOWN</li>
 *   <li>{@code heights[i] = (i - 7) * 100}: -700, -600, ..., 0, ..., 700 &mdash; covers negative and &gt;255 values</li>
 *   <li>{@code luminance[i] = (i * 17) % 256}: 0, 17, ..., 238 &mdash; several values &gt;127 exercise unsigned bytes</li>
 *   <li>{@code modified[i] = 1} at indices 2, 5, 11, else 0</li>
 * </ul>
 */
class SceneCodecGoldenTest {

    private static final int WIDTH = 5;
    private static final int HEIGHT = 3;

    @Test
    void encodesToTheCommittedGoldenFixture() throws IOException {
        SurfaceScene scene = goldenScene();
        JsonObject actual = SceneCodec.encode("fullscan", scene);
        JsonObject expected = readGoldenFixture();

        assertEquals(expected, actual);

        // decode the golden file's own bytes independently of SceneCodec, so a mismatch that happens to survive
        // on both sides still gets caught against the source arrays.
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
