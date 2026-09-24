package de.kylekreuter.vistructum.core.sidecar;

import com.google.gson.JsonObject;
import de.kylekreuter.vistructum.core.scene.SurfaceScene;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SceneCodecTest {

    @Test
    void roundTripsAllFourArrays() {
        short[] blocks = {-1, 0, 7, -5, 42, 12};
        short[] heights = {-300, 0, 260, 12000, -32000, 5};
        byte[] luminance = {0, (byte) 200, 127, (byte) 255, 64, 1};
        byte[] modified = {0, 1, 0, 0, 1, 1};
        SurfaceScene scene = new SurfaceScene(3, 2, blocks, heights, luminance, modified);

        JsonObject json = SceneCodec.encode("fullscan", scene);

        assertEquals("fullscan", json.get("kind").getAsString());
        assertEquals(3, json.get("width").getAsInt());
        assertEquals(2, json.get("height").getAsInt());
        assertArrayEquals(blocks, decodeInt16(json, "blocks", 6));
        assertArrayEquals(heights, decodeInt16(json, "heights", 6));
        assertArrayEquals(luminance, decodeUint8(json, "luminance"));
        assertArrayEquals(modified, decodeUint8(json, "modified"));
    }

    @Test
    void alwaysSendsAllFourArraysEvenForMaskOnlyScenes() {
        SurfaceScene scene = SurfaceScene.maskOnly(2, 2, new byte[] {0, 1, 1, 0});

        JsonObject json = SceneCodec.encode("mask", scene);

        assertEquals("mask", json.get("kind").getAsString());
        assertArrayEquals(scene.blocks(), decodeInt16(json, "blocks", 4));
        assertArrayEquals(scene.heights(), decodeInt16(json, "heights", 4));
        assertArrayEquals(scene.luminance(), decodeUint8(json, "luminance"));
        assertArrayEquals(scene.modified(), decodeUint8(json, "modified"));
    }

    static short[] decodeInt16(JsonObject json, String field, int count) {
        ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(json.get(field).getAsString()))
                .order(ByteOrder.LITTLE_ENDIAN);
        short[] out = new short[count];
        for (int i = 0; i < count; i++) {
            out[i] = buffer.getShort();
        }
        return out;
    }

    static byte[] decodeUint8(JsonObject json, String field) {
        return Base64.getDecoder().decode(json.get(field).getAsString());
    }
}
