package de.kylekreuter.vistructum.inference;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

final class TestModels {

    private TestModels() {
    }

    static byte[] testMask() {
        try (InputStream in = TestModels.class.getResourceAsStream("/test-mask.onnx")) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static byte[] bundled(ModelKind kind) {
        try {
            return ModelFiles.bundled(kind).orElseThrow();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
