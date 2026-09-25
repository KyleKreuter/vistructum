package de.kylekreuter.vistructum.inference;

import ai.onnxruntime.OrtEnvironment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class InferenceEngine implements AutoCloseable {

    private final OrtEnvironment environment = OrtEnvironment.getEnvironment();
    private final ModelFiles files;
    private final int threads;
    private final Logger logger;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("vistructum-inference").daemon().factory());
    private final ExecutorService downloads = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("vistructum-model-update").daemon().factory());
    private final Map<ModelKind, Model> models = new EnumMap<>(ModelKind.class);

    private InferenceEngine(ModelFiles files, int threads, Logger logger) {
        this.files = files;
        this.threads = threads;
        this.logger = logger;
    }

    public static InferenceEngine start(ModelFiles files, int threads, Logger logger) {
        InferenceEngine engine = new InferenceEngine(files, threads, logger);
        engine.executor.execute(engine::loadAll);
        return engine;
    }

    public CompletableFuture<InferResult> infer(ModelKind kind, SurfaceScene scene) {
        return CompletableFuture.supplyAsync(() -> {
            Model model = models.get(kind);
            if (model == null) {
                throw new CompletionException(new ModelException("no " + kind.id() + " model loaded"));
            }
            try {
                return model.infer(scene);
            } catch (ModelException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    public CompletableFuture<Map<ModelKind, ModelInfo>> models() {
        return CompletableFuture.supplyAsync(() -> {
            Map<ModelKind, ModelInfo> infos = new EnumMap<>(ModelKind.class);
            models.forEach((kind, model) -> infos.put(kind, model.info()));
            return Map.copyOf(infos);
        }, executor);
    }

    public CompletableFuture<List<ModelInfo>> update(ReleaseSource source) {
        return models()
                .thenApplyAsync(current -> fetch(source, current), downloads)
                .thenApplyAsync(this::install, executor);
    }

    @Override
    public void close() {
        downloads.shutdownNow();
        executor.execute(() -> {
            models.values().forEach(Model::close);
            models.clear();
        });
        executor.shutdown();
    }

    private void loadAll() {
        for (ModelKind kind : ModelKind.values()) {
            load(kind).ifPresentOrElse(model -> {
                models.put(kind, model);
                logger.info(kind.id() + " model " + model.info().version() + " loaded (sha256 "
                        + model.info().sha256().substring(0, 12) + ")");
            }, () -> logger.severe("no usable " + kind.id() + " model, " + kind.id() + " inference is off"));
        }
    }

    private Optional<Model> load(ModelKind kind) {
        try {
            Optional<byte[]> stored = files.stored(kind);
            if (stored.isPresent()) {
                try {
                    return Optional.of(checked(kind, Model.load(environment, stored.get(), threads)));
                } catch (ModelException e) {
                    logger.warning("ignoring " + files.file(kind) + ": " + e.getMessage());
                }
            }
            Optional<byte[]> bundled = ModelFiles.bundled(kind);
            if (bundled.isPresent()) {
                return Optional.of(checked(kind, Model.load(environment, bundled.get(), threads)));
            }
        } catch (IOException | ModelException e) {
            logger.log(Level.WARNING, "cannot load the " + kind.id() + " model", e);
        }
        return Optional.empty();
    }

    private static Model checked(ModelKind kind, Model model) throws ModelException {
        if (model.kind() != kind) {
            model.close();
            throw new ModelException("file holds a " + model.kind().id() + " model, expected " + kind.id());
        }
        return model;
    }

    private List<Download> fetch(ReleaseSource source, Map<ModelKind, ModelInfo> current) {
        Optional<ModelRelease> release;
        try {
            release = source.latest();
        } catch (IOException e) {
            throw new CompletionException(e);
        }
        if (release.isEmpty()) {
            return List.of();
        }
        List<Download> downloadsFound = new ArrayList<>();
        for (ModelRelease.Entry entry : release.get().models()) {
            Optional<ModelKind> kind = ModelKind.byId(entry.kind());
            if (kind.isEmpty()) {
                continue;
            }
            if (!Contract.FEATURE_SPEC.equals(entry.featureSpec())) {
                logger.warning("skipping " + entry.kind() + " model " + entry.version() + " from "
                        + release.get().tag() + ": needs feature spec " + entry.featureSpec() + ", this build reads "
                        + Contract.FEATURE_SPEC);
                continue;
            }
            ModelInfo loaded = current.get(kind.get());
            if (loaded != null && loaded.sha256().equalsIgnoreCase(entry.sha256())) {
                continue;
            }
            try {
                byte[] bytes = source.download(release.get(), entry);
                String actual = Model.sha256(bytes);
                if (!actual.equalsIgnoreCase(entry.sha256())) {
                    throw new IOException("sha256 " + actual + " != " + entry.sha256());
                }
                downloadsFound.add(new Download(kind.get(), release.get().tag(), bytes));
            } catch (IOException e) {
                logger.warning("cannot download " + entry.file() + " from " + release.get().tag() + ": "
                        + e.getMessage());
            }
        }
        return downloadsFound;
    }

    private List<ModelInfo> install(List<Download> downloaded) {
        List<ModelInfo> installed = new ArrayList<>();
        for (Download download : downloaded) {
            try {
                Model model = checked(download.kind(), Model.load(environment, download.bytes(), threads));
                files.store(download.kind(), download.bytes());
                Model previous = models.put(download.kind(), model);
                if (previous != null) {
                    previous.close();
                }
                installed.add(model.info());
                logger.info(download.kind().id() + " model " + model.info().version() + " from " + download.tag()
                        + " is active");
            } catch (ModelException | IOException e) {
                logger.warning("rejecting the " + download.kind().id() + " model from " + download.tag() + ": "
                        + e.getMessage());
            }
        }
        return installed;
    }

    private record Download(ModelKind kind, String tag, byte[] bytes) {
    }
}
