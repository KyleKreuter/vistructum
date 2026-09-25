package de.kylekreuter.vistructum.core.inference;

import com.google.gson.JsonObject;
import de.kylekreuter.vistructum.api.InferenceMode;
import de.kylekreuter.vistructum.api.InferenceStatus;
import de.kylekreuter.vistructum.core.sidecar.SidecarException;
import de.kylekreuter.vistructum.inference.InferResult;
import de.kylekreuter.vistructum.inference.ModelKind;
import de.kylekreuter.vistructum.inference.SurfaceScene;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FallbackInferenceTest {

    private static final SurfaceScene SCENE = new SurfaceScene(1, 1, new short[1], new short[1], new byte[1],
            new byte[1]);
    private static final InferResult REMOTE_RESULT = result("remote-1");
    private static final InferResult LOCAL_RESULT = result("local-1");

    @Test
    void remoteResultIsUsedWhenTheSidecarAnswers() {
        FakeInference local = FakeInference.answering(LOCAL_RESULT, available(InferenceMode.LOCAL));
        FallbackInference fallback = fallback(FakeInference.answering(REMOTE_RESULT,
                available(InferenceMode.REMOTE)), local);

        assertSame(REMOTE_RESULT, fallback.infer(ModelKind.MASK, SCENE, new JsonObject()).join());
        assertEquals(0, local.calls);
    }

    @Test
    void localInferenceTakesOverWhenTheSidecarIsUnreachable() {
        FallbackInference fallback = fallback(FakeInference.failing(new ConnectException("refused")),
                FakeInference.answering(LOCAL_RESULT, available(InferenceMode.LOCAL)));

        assertSame(LOCAL_RESULT, fallback.infer(ModelKind.MASK, SCENE, new JsonObject()).join());
    }

    @Test
    void localInferenceTakesOverOnSidecarServerErrors() {
        FallbackInference fallback = fallback(FakeInference.failing(new SidecarException(503, "no model")),
                FakeInference.answering(LOCAL_RESULT, available(InferenceMode.LOCAL)));

        assertSame(LOCAL_RESULT, fallback.infer(ModelKind.FULLSCAN, SCENE, new JsonObject()).join());
    }

    @Test
    void requestErrorsAreNotRetriedLocally() {
        FakeInference local = FakeInference.answering(LOCAL_RESULT, available(InferenceMode.LOCAL));
        FallbackInference fallback = fallback(FakeInference.failing(new SidecarException(422, "bad scene")), local);

        CompletionException error = assertThrows(CompletionException.class,
                () -> fallback.infer(ModelKind.MASK, SCENE, new JsonObject()).join());
        assertInstanceOf(SidecarException.class, RemoteInference.root(error));
        assertEquals(0, local.calls);
    }

    @Test
    void statusReportsTheLocalFallbackWhileTheSidecarIsDown() {
        FallbackInference fallback = fallback(new FakeInference(null, null,
                        InferenceStatus.unavailable(InferenceMode.REMOTE, "refused")),
                FakeInference.answering(LOCAL_RESULT, available(InferenceMode.LOCAL)));

        InferenceStatus status = fallback.status().join();

        assertEquals(InferenceMode.LOCAL, status.mode());
        assertTrue(status.available());
        assertEquals(Optional.of("sidecar: refused"), status.error());
    }

    @Test
    void statusReportsTheSidecarWhileItAnswers() {
        FallbackInference fallback = fallback(FakeInference.answering(REMOTE_RESULT, available(InferenceMode.REMOTE)),
                FakeInference.answering(LOCAL_RESULT, available(InferenceMode.LOCAL)));

        InferenceStatus status = fallback.status().join();

        assertEquals(InferenceMode.REMOTE, status.mode());
        assertFalse(status.error().isPresent());
    }

    private static FallbackInference fallback(Inference remote, Inference local) {
        return new FallbackInference(remote, local, Logger.getLogger("test"));
    }

    private static InferenceStatus available(InferenceMode mode) {
        return new InferenceStatus(mode, true, Map.of("mask", "mask-1"), Optional.empty());
    }

    private static InferResult result(String version) {
        return new InferResult("mask", version, 0.5, 1, 0.1, false, List.of(), 1.0, 1, 0);
    }

    private static final class FakeInference implements Inference {

        private final InferResult result;
        private final Throwable failure;
        private final InferenceStatus status;
        private int calls;

        private FakeInference(InferResult result, Throwable failure, InferenceStatus status) {
            this.result = result;
            this.failure = failure;
            this.status = status;
        }

        static FakeInference answering(InferResult result, InferenceStatus status) {
            return new FakeInference(result, null, status);
        }

        static FakeInference failing(Throwable failure) {
            return new FakeInference(null, failure, InferenceStatus.unavailable(InferenceMode.REMOTE, "failing"));
        }

        @Override
        public CompletableFuture<InferResult> infer(ModelKind kind, SurfaceScene scene, JsonObject context) {
            calls++;
            if (failure != null) {
                return CompletableFuture.supplyAsync(() -> {
                    throw new CompletionException(failure);
                });
            }
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public CompletableFuture<InferenceStatus> status() {
            return CompletableFuture.completedFuture(status);
        }

        @Override
        public void close() {
        }
    }
}
