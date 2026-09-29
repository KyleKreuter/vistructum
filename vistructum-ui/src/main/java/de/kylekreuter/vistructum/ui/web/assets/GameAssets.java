package de.kylekreuter.vistructum.ui.web;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public final class GameAssets implements AutoCloseable {

    public static final URI MANIFEST = URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");

    static final String MODELS = "models.json.gz";
    static final String MARKER = "complete";
    static final String TEXTURES = "textures";

    private static final Pattern VERSION = Pattern.compile("[0-9A-Za-z][0-9A-Za-z._-]*");
    private static final Pattern TEXTURE_ID = Pattern.compile(
            "(?:block|item)(?:/[a-z0-9_-][a-z0-9_.-]*)+|colormap/(?:grass|foliage)");

    private final Path root;
    private final String version;
    private final boolean enabled;
    private final AtomicReference<Thread> download = new AtomicReference<>();

    public GameAssets(Path root, String version, boolean enabled) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.version = Objects.requireNonNull(version, "version");
        this.enabled = enabled && VERSION.matcher(version).matches();
    }

    public boolean enabled() {
        return enabled;
    }

    public String version() {
        return version;
    }

    public boolean available() {
        return enabled && Files.isRegularFile(cache().resolve(MARKER));
    }

    public boolean downloading() {
        Thread thread = download.get();
        return thread != null && thread.isAlive();
    }

    public Optional<String> cachedVersion() {
        return available() ? Optional.of(version) : Optional.empty();
    }

    public CompletableFuture<Void> fetch(URI manifest, Logger logger) {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(logger, "logger");
        CompletableFuture<Void> done = new CompletableFuture<>();
        if (!enabled || available()) {
            done.complete(null);
            return done;
        }
        Thread thread = Thread.ofPlatform().name("vistructum-assets").daemon().unstarted(() -> {
            try {
                new AssetDownload(root, version, manifest, logger).run();
                done.complete(null);
            } catch (Exception e) {
                logger.log(Level.WARNING, "the Minecraft " + version + " textures cannot be downloaded,"
                        + " the web app uses its own textures until the next start: " + e);
                done.completeExceptionally(e);
            }
        });
        if (!download.compareAndSet(null, thread)) {
            done.completeExceptionally(new IllegalStateException("the textures are fetched already"));
            return done;
        }
        thread.start();
        return done;
    }

    Optional<Path> models(String requested) {
        return matching(requested).map(cache -> cache.resolve(MODELS)).filter(Files::isRegularFile);
    }

    Optional<Path> texture(String requested, String id) {
        if (!TEXTURE_ID.matcher(id).matches()) {
            return Optional.empty();
        }
        return matching(requested).flatMap(cache -> {
            Path textures = cache.resolve(TEXTURES);
            Path file = textures.resolve(id + ".png").normalize();
            return file.startsWith(textures) && Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
        });
    }

    @Override
    public void close() {
        Thread thread = download.get();
        if (thread != null) {
            thread.interrupt();
        }
    }

    private Optional<Path> matching(String requested) {
        return requested.equals(version) && available() ? Optional.of(cache()) : Optional.empty();
    }

    private Path cache() {
        return root.resolve(version);
    }
}
