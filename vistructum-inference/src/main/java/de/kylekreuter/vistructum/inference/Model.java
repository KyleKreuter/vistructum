package de.kylekreuter.vistructum.inference;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxJavaType;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.OptionalDouble;

public final class Model implements AutoCloseable {

    private final ModelKind kind;
    private final ModelInfo info;
    private final OrtSession session;
    private final Scoring scoring;

    private Model(ModelKind kind, ModelInfo info, OrtEnvironment environment, OrtSession session) {
        this.kind = kind;
        this.info = info;
        this.session = session;
        this.scoring = new Scoring(environment, session);
    }

    public static Model load(OrtEnvironment environment, byte[] bytes, int threads) throws ModelException {
        OrtSession session;
        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(threads);
            options.setInterOpNumThreads(1);
            session = environment.createSession(bytes, options);
        } catch (OrtException e) {
            throw new ModelException("onnx runtime rejected the model: " + e.getMessage(), e);
        }
        try {
            ModelInfo info = ModelMetadata.validate(session.getMetadata().getCustomMetadata(), sha256(bytes));
            ModelKind kind = ModelKind.byId(info.kind()).orElseThrow();
            checkSignature(session, kind);
            return new Model(kind, info, environment, session);
        } catch (ModelException | OrtException | RuntimeException e) {
            closeQuietly(session);
            throw e instanceof ModelException modelException ? modelException
                    : new ModelException("cannot read the model: " + e.getMessage(), e);
        }
    }

    public ModelKind kind() {
        return kind;
    }

    public ModelInfo info() {
        return info;
    }

    public InferResult infer(SurfaceScene scene) throws ModelException {
        long started = System.nanoTime();
        WindowBatch batch = Features.windows(kind, Features.extract(kind, scene));
        Scoring.Scores scores = score(batch);
        List<Detection> flagged = Clusters.flagged(batch, scores.values(), info.threshold(), info.minVotes());
        double maxScore = Arrays.stream(scores.values()).max().orElse(0.0);
        double elapsedMs = (System.nanoTime() - started) / 1_000_000.0;
        return new InferResult(kind.id(), info.version(), info.threshold(), batch.size(), maxScore, !flagged.isEmpty(),
                flagged.stream().limit(Contract.MAX_DETECTIONS).toList(), elapsedMs, info.minVotes(),
                scores.refined());
    }

    Scoring.Scores score(WindowBatch batch) throws ModelException {
        OptionalDouble prefilter = info.prefilter() == null ? OptionalDouble.empty() : OptionalDouble.of(info.prefilter());
        try {
            return scoring.score(batch, info.tta(), prefilter);
        } catch (OrtException e) {
            throw new ModelException("inference failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        closeQuietly(session);
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void checkSignature(OrtSession session, ModelKind kind) throws OrtException, ModelException {
        NodeInfo input = session.getInputInfo().get(Contract.INPUT_NAME);
        NodeInfo output = session.getOutputInfo().get(Contract.OUTPUT_NAME);
        if (input == null || output == null) {
            throw new ModelException("model needs input '" + Contract.INPUT_NAME + "' and output '"
                    + Contract.OUTPUT_NAME + "'");
        }
        if (!(input.getInfo() instanceof TensorInfo tensor) || tensor.type != OnnxJavaType.UINT8) {
            throw new ModelException("input '" + Contract.INPUT_NAME + "' must be a uint8 tensor");
        }
        long[] shape = tensor.getShape();
        if (shape.length != 4 || shape[1] != kind.channels() || shape[2] != Contract.GRID || shape[3] != Contract.GRID) {
            throw new ModelException("input shape " + Arrays.toString(shape) + " does not match [batch, "
                    + kind.channels() + ", " + Contract.GRID + ", " + Contract.GRID + "]");
        }
    }

    private static void closeQuietly(OrtSession session) {
        try {
            session.close();
        } catch (OrtException ignored) {
        }
    }
}
