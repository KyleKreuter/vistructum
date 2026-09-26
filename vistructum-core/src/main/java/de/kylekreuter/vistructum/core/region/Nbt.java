package de.kylekreuter.vistructum.core.region;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Nbt {

    private static final int END = 0;
    private static final int BYTE = 1;
    private static final int SHORT = 2;
    private static final int INT = 3;
    private static final int LONG = 4;
    private static final int FLOAT = 5;
    private static final int DOUBLE = 6;
    private static final int BYTE_ARRAY = 7;
    private static final int STRING = 8;
    private static final int LIST = 9;
    private static final int COMPOUND = 10;
    private static final int INT_ARRAY = 11;
    private static final int LONG_ARRAY = 12;

    private final DataInputStream in;
    private final Set<String> skipped;

    private Nbt(byte[] data, Set<String> skipped) {
        this.in = new DataInputStream(new ByteArrayInputStream(data));
        this.skipped = skipped;
    }

    public static Map<String, Object> read(byte[] data, Set<String> skipped) throws IOException {
        Nbt nbt = new Nbt(data, skipped);
        int type = nbt.in.readUnsignedByte();
        if (type != COMPOUND) {
            throw new IOException("root tag is " + type + ", expected a compound");
        }
        nbt.in.skipBytes(nbt.in.readUnsignedShort());
        return nbt.compound();
    }

    private Map<String, Object> compound() throws IOException {
        Map<String, Object> values = new HashMap<>();
        while (true) {
            int type = in.readUnsignedByte();
            if (type == END) {
                return values;
            }
            String name = in.readUTF();
            if (skipped.contains(name)) {
                skip(type);
            } else {
                values.put(name, value(type));
            }
        }
    }

    private Object value(int type) throws IOException {
        return switch (type) {
            case BYTE -> in.readByte();
            case SHORT -> in.readShort();
            case INT -> in.readInt();
            case LONG -> in.readLong();
            case FLOAT -> in.readFloat();
            case DOUBLE -> in.readDouble();
            case BYTE_ARRAY -> {
                byte[] array = new byte[length()];
                in.readFully(array);
                yield array;
            }
            case STRING -> in.readUTF();
            case LIST -> {
                int elementType = in.readUnsignedByte();
                int size = length();
                List<Object> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    list.add(value(elementType));
                }
                yield list;
            }
            case COMPOUND -> compound();
            case INT_ARRAY -> {
                int[] array = new int[length()];
                for (int i = 0; i < array.length; i++) {
                    array[i] = in.readInt();
                }
                yield array;
            }
            case LONG_ARRAY -> {
                long[] array = new long[length()];
                for (int i = 0; i < array.length; i++) {
                    array[i] = in.readLong();
                }
                yield array;
            }
            default -> throw new IOException("unknown tag type " + type);
        };
    }

    private void skip(int type) throws IOException {
        switch (type) {
            case BYTE -> in.skipBytes(1);
            case SHORT -> in.skipBytes(2);
            case INT, FLOAT -> in.skipBytes(4);
            case LONG, DOUBLE -> in.skipBytes(8);
            case BYTE_ARRAY -> in.skipBytes(length());
            case STRING -> in.skipBytes(in.readUnsignedShort());
            case LIST -> {
                int elementType = in.readUnsignedByte();
                int size = length();
                for (int i = 0; i < size; i++) {
                    skip(elementType);
                }
            }
            case COMPOUND -> {
                int nested;
                while ((nested = in.readUnsignedByte()) != END) {
                    in.skipBytes(in.readUnsignedShort());
                    skip(nested);
                }
            }
            case INT_ARRAY -> in.skipBytes(4 * length());
            case LONG_ARRAY -> in.skipBytes(8 * length());
            default -> throw new IOException("unknown tag type " + type);
        }
    }

    private int length() throws IOException {
        int length = in.readInt();
        if (length < 0) {
            throw new IOException("negative length " + length);
        }
        return length;
    }
}
