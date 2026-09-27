package de.kylekreuter.vistructum.core.update;

import de.kylekreuter.vistructum.inference.GitHubReleases;
import de.kylekreuter.vistructum.inference.InferenceEngine;
import de.kylekreuter.vistructum.inference.ModelUpdate;

import java.io.IOException;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;

/** Looks for a newer plugin release and newer models; installs models only when auto-update is on. */
public final class UpdateCheck implements Runnable {

    private final GitHubReleases releases;
    private final String runningVersion;
    private final InferenceEngine engine;
    private final boolean autoUpdateModels;
    private final Logger logger;

    /** @param engine the local models to check, or null when this server runs none */
    public UpdateCheck(GitHubReleases releases, String runningVersion, InferenceEngine engine,
                       boolean autoUpdateModels, Logger logger) {
        this.releases = releases;
        this.runningVersion = runningVersion;
        this.engine = engine;
        this.autoUpdateModels = autoUpdateModels;
        this.logger = logger;
    }

    @Override
    public void run() {
        checkPlugin();
        if (engine != null) {
            checkModels();
        }
    }

    private void checkPlugin() {
        try {
            releases.latestPluginVersion()
                    .filter(latest -> PluginVersion.isNewer(latest, runningVersion))
                    .ifPresent(latest -> logger.info("Vistructum " + latest + " is available (running "
                            + runningVersion + "): " + releases.releasePage(GitHubReleases.PLUGIN_TAG_PREFIX + latest)));
        } catch (IOException | RuntimeException e) {
            logger.warning("plugin update check failed: " + rootMessage(e));
        }
    }

    private void checkModels() {
        if (autoUpdateModels) {
            engine.update(releases).whenComplete((installed, error) -> {
                if (error != null) {
                    logger.warning("model update check failed: " + rootMessage(error));
                }
            });
            return;
        }
        engine.available(releases).whenComplete((updates, error) -> {
            if (error != null) {
                logger.warning("model update check failed: " + rootMessage(error));
                return;
            }
            for (ModelUpdate update : updates) {
                logger.info(update.describe() + ", set updates.auto-update-models to true to install it");
            }
        });
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
