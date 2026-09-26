package de.kylekreuter.vistructum.core.region;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

final class NbtWriter {

    private NbtWriter() {
    }

    static byte[] root(Map<String, Object> values) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(10);
            out.writeUTF("");
            compound(out, values);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static byte[] chunk(int dataVersion, String status, List<Map<String, Object>> sections) {
        return root(Map.of("DataVersion", dataVersion, "Status", status, "sections", sections,
                "structures", Map.of("References", Map.of()), "block_entities", List.of()));
    }

    static Map<String, Object> section(int y, List<String> palette, long[] data) {
        List<Map<String, Object>> entries = palette.stream().<Map<String, Object>>map(name -> Map.of("Name", name)).toList();
        Map<String, Object> states = data.length == 0 ? Map.of("palette", entries) : Map.of("palette", entries, "data", data);
        return Map.of("Y", (byte) y, "block_states", states);
    }

    static long[] pack(int[] indices, int paletteSize) {
        int bits = Math.max(4, Integer.SIZE - Integer.numberOfLeadingZeros(paletteSize - 1));
        int perLong = Long.SIZE / bits;
        long[] data = new long[(indices.length + perLong - 1) / perLong];
        for (int i = 0; i < indices.length; i++) {
            data[i / perLong] |= (long) indices[i] << ((i % perLong) * bits);
        }
        return data;
    }

    private static void compound(DataOutputStream out, Map<String, Object> values) throws IOException {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            out.writeByte(type(entry.getValue()));
            out.writeUTF(entry.getKey());
            value(out, entry.getValue());
        }
        out.writeByte(0);
    }

    @SuppressWarnings("unchecked")
    private static void value(DataOutputStream out, Object value) throws IOException {
        switch (value) {
            case Byte b -> out.writeByte(b);
            case Integer i -> out.writeInt(i);
            case Long l -> out.writeLong(l);
            case String s -> out.writeUTF(s);
            case long[] array -> {
                out.writeInt(array.length);
                for (long l : array) {
                    out.writeLong(l);
                }
            }
            case List<?> list -> {
                out.writeByte(list.isEmpty() ? 0 : type(list.getFirst()));
                out.writeInt(list.size());
                for (Object element : list) {
                    value(out, element);
                }
            }
            case Map<?, ?> map -> compound(out, (Map<String, Object>) map);
            default -> throw new IllegalArgumentException("unsupported " + value.getClass());
        }
    }

    private static int type(Object value) {
        return switch (value) {
            case Byte b -> 1;
            case Integer i -> 3;
            case Long l -> 4;
            case String s -> 8;
            case List<?> l -> 9;
            case Map<?, ?> m -> 10;
            case long[] a -> 12;
            default -> throw new IllegalArgumentException("unsupported " + value.getClass());
        };
    }
}
