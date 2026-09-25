package de.kylekreuter.vistructum.inference;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

public final class ModelFiles {

    private final Path directory;

    public ModelFiles(Path directory) {
        this.directory = directory;
    }

    public Path directory() {
        return directory;
    }

    Path file(ModelKind kind) {
        return directory.resolve(kind.id() + ".onnx");
    }

    Optional<byte[]> stored(ModelKind kind) throws IOException {
        Path file = file(kind);
        return Files.isRegularFile(file) ? Optional.of(Files.readAllBytes(file)) : Optional.empty();
    }

    static Optional<byte[]> bundled(ModelKind kind) throws IOException {
        try (InputStream in = ModelFiles.class.getResourceAsStream("/models/" + kind.id() + ".onnx")) {
            return in == null ? Optional.empty() : Optional.of(in.readAllBytes());
        }
    }

    void store(ModelKind kind, byte[] bytes) throws IOException {
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, kind.id() + "-", ".download");
        try {
            Files.write(temporary, bytes);
            Files.move(temporary, file(kind), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
