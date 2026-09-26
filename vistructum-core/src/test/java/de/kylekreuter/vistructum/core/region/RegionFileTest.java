package de.kylekreuter.vistructum.core.region;

import net.jpountz.lz4.LZ4BlockOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionFileTest {

    @TempDir
    Path dir;

    @Test
    void readsEveryCompressionAndComputesChunkCoordinates() throws IOException {
        byte[] nbt = NbtWriter.root(Map.of("DataVersion", 4189));
        Path file = dir.resolve("r.-1.2.mca");
        writeRegion(file, Map.of(0, frame(2, deflate(nbt)), 1, frame(1, gzip(nbt)), 32, frame(3, nbt), 33, frame(4, lz4(nbt)),
                34, frame(127, nbt)));
        List<RegionChunk> chunks = RegionFile.read(file);
        assertEquals(5, chunks.size());
        for (RegionChunk chunk : chunks.subList(0, 4)) {
            assertArrayEquals(nbt, chunk.nbt().orElseThrow());
        }
        assertTrue(chunks.get(4).nbt().isEmpty());
        assertEquals(-32, chunks.get(0).chunkX());
        assertEquals(64, chunks.get(0).chunkZ());
        assertEquals(-31, chunks.get(1).chunkX());
        assertEquals(65, chunks.get(2).chunkZ());
    }

    @Test
    void readsExternalChunkFiles() throws IOException {
        byte[] nbt = NbtWriter.root(Map.of("DataVersion", 4189));
        Path file = dir.resolve("r.0.0.mca");
        writeRegion(file, Map.of(3, frame(2 | 128, new byte[0])));
        Files.write(dir.resolve("c.3.0.mcc"), deflate(nbt));
        assertArrayEquals(nbt, RegionFile.read(file).getFirst().nbt().orElseThrow());
    }

    @Test
    void truncatedPayloadIsUnreadable() throws IOException {
        Path file = dir.resolve("r.0.0.mca");
        byte[] frame = frame(2, deflate(new byte[]{1, 2, 3}));
        ByteBuffer.wrap(frame).putInt(0, 1_000_000);
        writeRegion(file, Map.of(0, frame));
        assertTrue(RegionFile.read(file).getFirst().nbt().isEmpty());
    }

    @Test
    void decodesChunkColumns() throws IOException {
        int[] indices = new int[Section.VOLUME];
        indices[Section.index(1, 2, 3)] = 1;
        byte[] nbt = NbtWriter.chunk(4189, ChunkColumn.FULL, List.of(
                NbtWriter.section(-1, List.of("minecraft:stone"), new long[0]),
                NbtWriter.section(4, List.of("minecraft:air", "minecraft:red_wool"), NbtWriter.pack(indices, 2))));
        ChunkColumn column = ChunkColumn.decode(5, 6, nbt);
        assertTrue(column.full());
        assertEquals(4189, column.dataVersion());
        assertEquals(2, column.sections().size());
        assertEquals(1, column.sections().get(1).indices()[Section.index(1, 2, 3)]);
    }

    private static void writeRegion(Path file, Map<Integer, byte[]> frames) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(8192);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int sector = 2;
        for (int index = 0; index < 1024; index++) {
            byte[] frame = frames.get(index);
            if (frame == null) {
                continue;
            }
            int sectors = (frame.length + 4095) / 4096;
            header.putInt(index * 4, sector << 8 | sectors);
            byte[] padded = new byte[sectors * 4096];
            System.arraycopy(frame, 0, padded, 0, frame.length);
            body.write(padded);
            sector += sectors;
        }
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        all.write(header.array());
        all.write(body.toByteArray());
        Files.write(file, all.toByteArray());
    }

    private static byte[] frame(int type, byte[] payload) {
        ByteBuffer frame = ByteBuffer.allocate(5 + payload.length);
        frame.putInt(payload.length + 1).put((byte) type).put(payload);
        return frame.array();
    }

    private static byte[] deflate(byte[] data) throws IOException {
        return compress(data, DeflaterOutputStream::new);
    }

    private static byte[] gzip(byte[] data) throws IOException {
        return compress(data, GZIPOutputStream::new);
    }

    private static byte[] lz4(byte[] data) throws IOException {
        return compress(data, LZ4BlockOutputStream::new);
    }

    private static byte[] compress(byte[] data, Wrapper wrapper) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (OutputStream out = wrapper.wrap(bytes)) {
            out.write(data);
        }
        return bytes.toByteArray();
    }

    private interface Wrapper {
        OutputStream wrap(OutputStream out) throws IOException;
    }
}
