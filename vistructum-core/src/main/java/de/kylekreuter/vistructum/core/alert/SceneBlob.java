package de.kylekreuter.vistructum.core.alert;

import de.kylekreuter.vistructum.inference.SurfaceScene;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class SceneBlob {

    private static final int BYTES_PER_CELL = 6;

    private SceneBlob() {
    }

    public static byte[] encode(SurfaceScene scene) {
        int count = scene.width() * scene.height();
        ByteBuffer raw = ByteBuffer.allocate(count * BYTES_PER_CELL).order(ByteOrder.LITTLE_ENDIAN);
        for (short block : scene.blocks()) {
            raw.putShort(block);
        }
        for (short height : scene.heights()) {
            raw.putShort(height);
        }
        raw.put(scene.luminance());
        raw.put(scene.modified());
        Deflater deflater = new Deflater();
        try {
            deflater.setInput(raw.array());
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            while (!deflater.finished()) {
                out.write(chunk, 0, deflater.deflate(chunk));
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    public static SurfaceScene decode(int width, int height, byte[] blob) {
        int count = width * height;
        byte[] raw = new byte[count * BYTES_PER_CELL];
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(blob);
            int read = 0;
            while (read < raw.length && !inflater.finished()) {
                int inflated = inflater.inflate(raw, read, raw.length - read);
                if (inflated == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
                read += inflated;
            }
            if (read != raw.length || !inflater.finished()) {
                throw new IllegalArgumentException("scene blob does not hold " + width + "x" + height + " cells");
            }
        } catch (DataFormatException e) {
            throw new IllegalArgumentException("scene blob is corrupt", e);
        } finally {
            inflater.end();
        }
        ByteBuffer buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        short[] blocks = new short[count];
        short[] heights = new short[count];
        buffer.asShortBuffer().get(blocks);
        buffer.position(count * 2);
        buffer.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(heights);
        byte[] luminance = new byte[count];
        byte[] modified = new byte[count];
        buffer.position(count * 4);
        buffer.get(luminance);
        buffer.get(modified);
        return new SurfaceScene(width, height, blocks, heights, luminance, modified);
    }
}
