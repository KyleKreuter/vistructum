package de.kylekreuter.vistructum.core.region;

import net.jpountz.lz4.LZ4BlockInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
        return read(file, (chunkX, chunkZ) -> true);
    }

    public static List<int[]> present(Path file) throws IOException {
        int[] region = region(file);
        List<int[]> chunks = new ArrayList<>();
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            ByteBuffer header = header(channel);
            for (int index = 0; index < SIDE * SIDE; index++) {
                if (header.getInt(index * 4) != 0) {
                    chunks.add(new int[]{chunkX(region, index), chunkZ(region, index)});
                }
            }
        }
        return chunks;
    }

    public static List<RegionChunk> read(Path file, ChunkFilter wanted) throws IOException {
        int[] region = region(file);
        List<RegionChunk> chunks = new ArrayList<>();
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            ByteBuffer header = header(channel);
            long size = channel.size();
            for (int index = 0; index < SIDE * SIDE; index++) {
                int location = header.getInt(index * 4);
                int chunkX = chunkX(region, index);
                int chunkZ = chunkZ(region, index);
                if (location == 0 || !wanted.test(chunkX, chunkZ)) {
                    continue;
                }
                chunks.add(new RegionChunk(chunkX, chunkZ, payload(file, channel, size, location, chunkX, chunkZ)));
            }
        }
        return chunks;
    }

    @FunctionalInterface
    public interface ChunkFilter {

        boolean test(int chunkX, int chunkZ);
    }

    private static int[] region(Path file) throws IOException {
        return coordinates(file).orElseThrow(() -> new IOException("not a region file: " + file));
    }

    private static int chunkX(int[] region, int index) {
        return region[0] * SIDE + (index & 31);
    }

    private static int chunkZ(int[] region, int index) {
        return region[1] * SIDE + (index >> 5);
    }

    private static ByteBuffer header(FileChannel channel) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(SECTOR);
        if (channel.size() >= SECTOR) {
            readFully(channel, header, 0);
        }
        return header;
    }

    private static Optional<byte[]> payload(Path file, FileChannel channel, long size, int location, int chunkX,
                                            int chunkZ) {
        long start = (long) (location >>> 8) * SECTOR;
        int sectors = location & 0xff;
        try {
            if (start + 5 > size) {
                return Optional.empty();
            }
            ByteBuffer frame = ByteBuffer.allocate(5);
            readFully(channel, frame, start);
            int length = frame.getInt(0);
            int type = frame.get(4) & 0xff;
            if ((type & EXTERNAL) != 0) {
                Path external = file.resolveSibling("c." + chunkX + "." + chunkZ + ".mcc");
                byte[] data = Files.readAllBytes(external);
                return decompress(type & ~EXTERNAL, data, 0, data.length);
            }
            if (length < 1 || start + 4 + length > size || 4L + length > (long) sectors * SECTOR) {
                return Optional.empty();
            }
            ByteBuffer body = ByteBuffer.allocate(length - 1);
            readFully(channel, body, start + 5);
            return decompress(type, body.array(), 0, length - 1);
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static void readFully(FileChannel channel, ByteBuffer buffer, long position) throws IOException {
        long offset = position;
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, offset);
            if (read < 0) {
                throw new IOException("unexpected end of region file");
            }
            offset += read;
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
