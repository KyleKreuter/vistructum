package de.kylekreuter.vistructum.core.alert;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.kylekreuter.vistructum.api.TrainingExport;
import de.kylekreuter.vistructum.inference.ModelKind;
import de.kylekreuter.vistructum.inference.SceneCodec;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class FindingExporter {

    private static final String FILE_EXTENSION = ".jsonl";
    private static final int PAGE_SIZE = 32;
    private static final DateTimeFormatter DIRECTORY_NAME = DateTimeFormatter.ofPattern("'findings-'yyyyMMdd-HHmmss-SSS",
            Locale.ROOT);

    private final FindingStore store;
    private final Path folder;
    private final Clock clock;
    private final Gson gson = new Gson();

    public FindingExporter(FindingStore store, Path folder, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.folder = Objects.requireNonNull(folder, "folder");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CompletableFuture<TrainingExport> export() {
        Path directory = folder.resolve(DIRECTORY_NAME.format(clock.instant().atZone(clock.getZone())));
        return store.countReviewedWithoutScene().thenComposeAsync(skipped -> {
            ExportFiles files = open(directory);
            return page(files, 0).whenComplete((done, error) -> files.close()).thenApply(done ->
                    new TrainingExport(directory, files.counts(), skipped));
        });
    }

    private CompletableFuture<Void> page(ExportFiles files, long afterId) {
        return store.reviewedScenes(afterId, PAGE_SIZE).thenComposeAsync(scenes -> {
            scenes.forEach(scene -> files.write(scene.input().kind(), gson.toJson(line(scene))));
            return scenes.size() < PAGE_SIZE
                    ? CompletableFuture.completedFuture(null)
                    : page(files, scenes.getLast().findingId());
        });
    }

    static JsonObject line(ReviewedScene reviewed) {
        ModelInput input = reviewed.input();
        JsonObject line = new JsonObject();
        line.addProperty("finding", reviewed.findingId());
        line.addProperty("source", reviewed.source().modelKind());
        line.addProperty("verdict", reviewed.verdict().name());
        line.addProperty("model_version", reviewed.modelVersion());
        JsonObject window = new JsonObject();
        window.addProperty("top", input.top());
        window.addProperty("left", input.left());
        window.addProperty("bottom", input.bottom());
        window.addProperty("right", input.right());
        line.add("window", window);
        SceneCodec.encode(input.kind(), input.scene()).entrySet()
                .forEach(entry -> line.add(entry.getKey(), entry.getValue()));
        return line;
    }

    private static ExportFiles open(Path directory) {
        try {
            Files.createDirectories(directory.getParent());
            Files.createDirectory(directory);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot create " + directory, e);
        }
        return new ExportFiles(directory);
    }

    private static final class ExportFiles {

        private final Path directory;
        private final Map<ModelKind, BufferedWriter> writers = new EnumMap<>(ModelKind.class);
        private final Map<ModelKind, Long> counts = new EnumMap<>(ModelKind.class);

        private ExportFiles(Path directory) {
            this.directory = directory;
        }

        private void write(ModelKind kind, String json) {
            try {
                BufferedWriter writer = writers.get(kind);
                if (writer == null) {
                    writer = Files.newBufferedWriter(directory.resolve(kind.id() + FILE_EXTENSION),
                            StandardCharsets.UTF_8);
                    writers.put(kind, writer);
                }
                writer.write(json);
                writer.newLine();
                counts.merge(kind, 1L, Long::sum);
            } catch (IOException e) {
                throw new UncheckedIOException("cannot write the " + kind.id() + " export", e);
            }
        }

        private Map<String, Long> counts() {
            return counts.entrySet().stream().collect(Collectors.toMap(entry -> entry.getKey().id(), Map.Entry::getValue));
        }

        private void close() {
            List<IOException> failures = writers.values().stream().map(ExportFiles::closeQuietly)
                    .filter(Objects::nonNull).toList();
            if (!failures.isEmpty()) {
                throw new UncheckedIOException("cannot close the export files", failures.getFirst());
            }
        }

        private static IOException closeQuietly(BufferedWriter writer) {
            try {
                writer.close();
                return null;
            } catch (IOException e) {
                return e;
            }
        }
    }
}
