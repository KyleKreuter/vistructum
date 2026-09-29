package de.kylekreuter.vistructum.ui.web.assets;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Stream;

final class AssetDownload {

    private static final double MEBIBYTE = 1024.0 * 1024.0;

    private final Path root;
    private final String version;
    private final URI manifest;
    private final Logger logger;

    AssetDownload(Path root, String version, URI manifest, Logger logger) {
        this.root = Objects.requireNonNull(root, "root");
        this.version = Objects.requireNonNull(version, "version");
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    void run() throws IOException, InterruptedException {
        Files.createDirectories(root);
        Path jar = root.resolve(version + ".jar.part");
        Path staging = root.resolve(version + ".tmp");
        Path target = root.resolve(version);
        long started = System.nanoTime();
        logger.info("downloading the Minecraft " + version + " client jar for the web app textures");
        try {
            ClientJar.Download client = ClientJar.locate(manifest, version);
            long size = ClientJar.download(client, jar);
            deleteTree(staging);
            int files = AssetExtraction.extract(jar, staging);
            ModelIndex.write(staging, version);
            Files.writeString(staging.resolve(GameAssets.MARKER), client.sha1());
            deleteTree(target);
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            double seconds = (System.nanoTime() - started) / 1e9;
            logger.info(String.format(Locale.ROOT,
                    "the Minecraft %s textures are ready: %d files extracted from %.1f MiB in %.1f s",
                    version, files, size / MEBIBYTE, seconds));
        } finally {
            Files.deleteIfExists(jar);
            deleteTree(staging);
        }
    }

    static void deleteTree(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }
}
