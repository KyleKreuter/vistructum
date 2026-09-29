package de.kylekreuter.vistructum.core.store;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class Packed {

    private static final int CHUNK = 8192;

    private Packed() {
    }

    public static byte[] deflate(byte[] raw) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(raw);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[CHUNK];
            while (!deflater.finished()) {
                out.write(chunk, 0, deflater.deflate(chunk));
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    public static byte[] inflate(byte[] packed) {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(packed);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[CHUNK];
            while (!inflater.finished()) {
                int inflated = inflater.inflate(chunk);
                if (inflated == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw new IllegalArgumentException("packed data is truncated");
                }
                out.write(chunk, 0, inflated);
            }
            return out.toByteArray();
        } catch (DataFormatException e) {
            throw new IllegalArgumentException("packed data is corrupt", e);
        } finally {
            inflater.end();
        }
    }

    public static final class Writer {

        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        public Writer unsigned(long value) {
            long rest = value;
            while ((rest & ~0x7FL) != 0) {
                out.write((int) (rest & 0x7F) | 0x80);
                rest >>>= 7;
            }
            out.write((int) rest);
            return this;
        }

        public Writer signed(long value) {
            return unsigned(value << 1 ^ value >> 63);
        }

        public Writer text(String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            unsigned(bytes.length);
            out.write(bytes, 0, bytes.length);
            return this;
        }

        public byte[] bytes() {
            return out.toByteArray();
        }
    }

    public static final class Reader {

        private final byte[] bytes;
        private int position;

        public Reader(byte[] bytes) {
            this.bytes = bytes;
        }

        public long unsigned() {
            long value = 0;
            for (int shift = 0; shift < Long.SIZE; shift += 7) {
                if (position >= bytes.length) {
                    throw new IllegalArgumentException("packed data ends inside a number");
                }
                int next = bytes[position++];
                value |= (long) (next & 0x7F) << shift;
                if ((next & 0x80) == 0) {
                    return value;
                }
            }
            throw new IllegalArgumentException("packed number is too long");
        }

        public long signed() {
            long raw = unsigned();
            return raw >>> 1 ^ -(raw & 1);
        }

        public int count() {
            return Math.toIntExact(unsigned());
        }

        public String text() {
            int length = count();
            if (length > bytes.length - position) {
                throw new IllegalArgumentException("packed text exceeds the data");
            }
            String value = new String(bytes, position, length, StandardCharsets.UTF_8);
            position += length;
            return value;
        }
    }
}
