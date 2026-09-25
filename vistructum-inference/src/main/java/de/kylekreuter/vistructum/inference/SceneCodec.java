package de.kylekreuter.vistructum.inference;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.List;

public final class SceneCodec {

    private SceneCodec() {
    }

    public static JsonObject encode(ModelKind kind, SurfaceScene scene) {
        JsonObject json = new JsonObject();
        json.addProperty("kind", kind.id());
        json.addProperty("width", scene.width());
        json.addProperty("height", scene.height());
        json.addProperty("blocks", encodeInt16(scene.blocks()));
        json.addProperty("heights", encodeInt16(scene.heights()));
        json.addProperty("luminance", encodeUint8(scene.luminance()));
        json.addProperty("modified", encodeUint8(scene.modified()));
        return json;
    }

    public static SurfaceScene decode(ModelKind kind, JsonObject json) {
        int width = dimension(json, "width");
        int height = dimension(json, "height");
        int count = width * height;
        for (String field : required(kind)) {
            if (!json.has(field) || json.get(field).isJsonNull()) {
                throw new IllegalArgumentException("'" + field + "' is required for kind '" + kind.id() + "'");
            }
        }
        byte[] modified = decodeUint8(json, "modified", count);
        for (byte value : modified) {
            if (value != 0 && value != 1) {
                throw new IllegalArgumentException("modified must contain only 0 or 1");
            }
        }
        return new SurfaceScene(width, height, decodeInt16(json, "blocks", count), decodeInt16(json, "heights", count),
                decodeUint8(json, "luminance", count), modified);
    }

    private static List<String> required(ModelKind kind) {
        return switch (kind) {
            case MASK -> List.of("modified");
            case FULLSCAN -> List.of("blocks", "heights", "luminance");
        };
    }

    private static int dimension(JsonObject json, String field) {
        JsonElement value = json.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("'" + field + "' must be a number");
        }
        int dimension = value.getAsInt();
        if (dimension < 1 || dimension > SurfaceScene.MAX_SIDE) {
            throw new IllegalArgumentException("'" + field + "' " + dimension + " outside 1.." + SurfaceScene.MAX_SIDE);
        }
        return dimension;
    }

    private static byte[] bytes(JsonObject json, String field) {
        JsonElement value = json.get(field);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(value.getAsString());
        } catch (IllegalArgumentException | UnsupportedOperationException | IllegalStateException e) {
            throw new IllegalArgumentException("'" + field + "' is not base64", e);
        }
    }

    private static short[] decodeInt16(JsonObject json, String field, int count) {
        byte[] raw = bytes(json, field);
        if (raw == null) {
            return new short[count];
        }
        if (raw.length != count * 2) {
            throw new IllegalArgumentException(field + " has " + raw.length / 2 + " int16 values, expected " + count);
        }
        short[] values = new short[count];
        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(values);
        return values;
    }

    private static byte[] decodeUint8(JsonObject json, String field, int count) {
        byte[] raw = bytes(json, field);
        if (raw == null) {
            return new byte[count];
        }
        if (raw.length != count) {
            throw new IllegalArgumentException(field + " has " + raw.length + " uint8 values, expected " + count);
        }
        return raw;
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
