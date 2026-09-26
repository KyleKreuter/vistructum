package de.kylekreuter.vistructum.api;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Result of {@link Findings#exportTraining()}.
 *
 * <p>The export directory holds one JSON Lines file per model kind, named after the kind with the extension
 * {@code .jsonl}. A file exists only for kinds with at least one exported finding. Each line describes one reviewed
 * finding with its identifier, detection path, verdict, model version, the model input scene with all channels and
 * the detection window within that scene. The scene fields use the same encoding as requests to the inference
 * sidecar.
 *
 * @param directory newly created directory that contains the export files
 * @param samples unmodifiable map from model kind to the number of findings written for that kind; kinds without
 *                exported findings are absent
 * @param skipped number of reviewed findings that were not exported because no model input scene is stored for them
 */
public record TrainingExport(Path directory, Map<String, Long> samples, long skipped) {

    /**
     * Validates the components and copies {@code samples} into an unmodifiable map.
     *
     * @throws NullPointerException if {@code directory} or {@code samples} is {@code null}, or {@code samples}
     *                              contains a {@code null} key or value
     * @throws IllegalArgumentException if {@code skipped} is negative
     */
    public TrainingExport {
        Objects.requireNonNull(directory, "directory");
        samples = Map.copyOf(samples);
        if (skipped < 0) {
            throw new IllegalArgumentException("negative skipped count " + skipped);
        }
    }

    /**
     * Returns the total number of exported findings.
     *
     * @return the sum of all values of {@link #samples()}
     */
    public long total() {
        return samples.values().stream().mapToLong(Long::longValue).sum();
    }
}
