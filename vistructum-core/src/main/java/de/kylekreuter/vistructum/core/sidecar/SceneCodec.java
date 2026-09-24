package de.kylekreuter.vistructum.core.sidecar;

import com.google.gson.JsonObject;
import de.kylekreuter.vistructum.core.scene.SurfaceScene;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

public final class SceneCodec {

    private SceneCodec() {
    }

    public static JsonObject encode(String kind, SurfaceScene scene) {
        JsonObject json = new JsonObject();
        json.addProperty("kind", kind);
        json.addProperty("width", scene.width());
        json.addProperty("height", scene.height());
        json.addProperty("blocks", encodeInt16(scene.blocks()));
        json.addProperty("heights", encodeInt16(scene.heights()));
        json.addProperty("luminance", encodeUint8(scene.luminance()));
        json.addProperty("modified", encodeUint8(scene.modified()));
        return json;
    }

    private static String encodeInt16(short[] values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short value : values) {
            buffer.putShort(value);
        }
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    private static String encodeUint8(byte[] values) {
        return Base64.getEncoder().encodeToString(values);
    }
}
