package de.kylekreuter.vistructum.core.region;

import net.jpountz.lz4.LZ4BlockInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public final class RegionFile {

    public static final int SIDE = 32;

    private static final Pattern NAME = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    private static final int SECTOR = 4096;
    private static final int GZIP = 1;
    private static final int ZLIB = 2;
    private static final int NONE = 3;
    private static final int LZ4 = 4;
    private static final int EXTERNAL = 128;

    private RegionFile() {
    }

    public static Optional<int[]> coordinates(Path file) {
        Matcher m = NAME.matcher(file.getFileName().toString());
        return m.matches() ? Optional.of(new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))})
                : Optional.empty();
    }

    public static List<RegionChunk> read(Path file) throws IOException {
        int[] region = coordinates(file).orElseThrow(() -> new IOException("not a region file: " + file));
        byte[] bytes = Files.readAllBytes(file);
        List<RegionChunk> chunks = new ArrayList<>();
        if (bytes.length < SECTOR) {
            return chunks;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        for (int index = 0; index < SIDE * SIDE; index++) {
            int location = buffer.getInt(index * 4);
            if (location == 0) {
                continue;
            }
            int chunkX = region[0] * SIDE + (index & 31);
            int chunkZ = region[1] * SIDE + (index >> 5);
            chunks.add(new RegionChunk(chunkX, chunkZ, payload(file, buffer, location, chunkX, chunkZ)));
        }
        return chunks;
    }

    private static Optional<byte[]> payload(Path file, ByteBuffer buffer, int location, int chunkX, int chunkZ) {
        int start = (location >>> 8) * SECTOR;
        if (start + 5 > buffer.capacity()) {
            return Optional.empty();
        }
        int length = buffer.getInt(start);
        int type = buffer.get(start + 4) & 0xff;
        try {
            if ((type & EXTERNAL) != 0) {
                Path external = file.resolveSibling("c." + chunkX + "." + chunkZ + ".mcc");
                return decompress(type & ~EXTERNAL, Files.readAllBytes(external), 0, (int) Files.size(external));
            }
            if (length < 1 || start + 4 + length > buffer.capacity()) {
                return Optional.empty();
            }
            return decompress(type, buffer.array(), start + 5, length - 1);
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static Optional<byte[]> decompress(int type, byte[] data, int offset, int length) throws IOException {
        ByteArrayInputStream raw = new ByteArrayInputStream(data, offset, length);
        InputStream stream = switch (type) {
            case GZIP -> new GZIPInputStream(raw);
            case ZLIB -> new InflaterInputStream(raw);
            case NONE -> raw;
            case LZ4 -> new LZ4BlockInputStream(raw);
            default -> null;
        };
        if (stream == null) {
            return Optional.empty();
        }
        try (stream) {
            return Optional.of(stream.readAllBytes());
        }
    }
}
