package de.kylekreuter.vistructum.inference;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SceneCodecTest {

    @Test
    void roundTripsAllFourArrays() {
        short[] blocks = {-1, 0, 7, -5, 42, 12};
        short[] heights = {-300, 0, 260, 12000, -32000, 5};
        byte[] luminance = {0, (byte) 200, 127, (byte) 255, 64, 1};
        byte[] modified = {0, 1, 0, 0, 1, 1};
        SurfaceScene scene = new SurfaceScene(3, 2, blocks, heights, luminance, modified);

        JsonObject json = SceneCodec.encode(ModelKind.FULLSCAN, scene);

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

        JsonObject json = SceneCodec.encode(ModelKind.MASK, scene);

        assertEquals("mask", json.get("kind").getAsString());
        assertArrayEquals(scene.blocks(), decodeInt16(json, "blocks", 4));
        assertArrayEquals(scene.heights(), decodeInt16(json, "heights", 4));
        assertArrayEquals(scene.luminance(), decodeUint8(json, "luminance"));
        assertArrayEquals(scene.modified(), decodeUint8(json, "modified"));
    }

    @Test
    void decodesWhatItEncodes() {
        short[] blocks = {-1, 0, 7, -5, 42, 12};
        short[] heights = {-300, 0, 260, 12000, -32000, 5};
        byte[] luminance = {0, (byte) 200, 127, (byte) 255, 64, 1};
        byte[] modified = {0, 1, 0, 0, 1, 1};
        SurfaceScene scene = new SurfaceScene(3, 2, blocks, heights, luminance, modified);

        SurfaceScene decoded = SceneCodec.decode(ModelKind.FULLSCAN, SceneCodec.encode(ModelKind.FULLSCAN, scene));

        assertArrayEquals(blocks, decoded.blocks());
        assertArrayEquals(heights, decoded.heights());
        assertArrayEquals(luminance, decoded.luminance());
        assertArrayEquals(modified, decoded.modified());
    }

    @Test
    void rejectsInvalidRequests() {
        SurfaceScene scene = SurfaceScene.maskOnly(2, 2, new byte[]{0, 1, 1, 0});
        JsonObject missing = SceneCodec.encode(ModelKind.MASK, scene);
        missing.remove("modified");
        JsonObject wrongLength = SceneCodec.encode(ModelKind.MASK, scene);
        wrongLength.addProperty("width", 3);
        JsonObject notBinary = SceneCodec.encode(ModelKind.MASK, SurfaceScene.maskOnly(2, 2, new byte[]{0, 2, 1, 0}));
        JsonObject tooLarge = SceneCodec.encode(ModelKind.MASK, scene);
        tooLarge.addProperty("height", 513);

        for (JsonObject json : new JsonObject[]{missing, wrongLength, notBinary, tooLarge}) {
            assertThrows(IllegalArgumentException.class, () -> SceneCodec.decode(ModelKind.MASK, json));
        }
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
