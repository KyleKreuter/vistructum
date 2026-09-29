package de.kylekreuter.vistructum.ui.web.view;

import de.kylekreuter.vistructum.api.InferenceStatus;
import de.kylekreuter.vistructum.api.ScanJob;
import de.kylekreuter.vistructum.api.VistructumStatus;
import de.kylekreuter.vistructum.ui.web.assets.GameAssets;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record StatusView(long trackedChanges, long openFindings, InferenceView inference, List<ScanView> scans,
                         boolean recordingEnabled, TexturesView textures) {

    public static StatusView of(VistructumStatus status, GameAssets assets) {
        return new StatusView(status.trackedChanges(), status.openFindings(), InferenceView.of(status.inference()),
                status.activeScans().stream().map(ScanView::of).toList(), status.recordingEnabled(),
                new TexturesView(assets.enabled(), assets.available(), assets.cachedVersion().orElse(null)));
    }

    public record InferenceView(String mode, boolean available, List<ModelView> models, Optional<String> detail) {

        static InferenceView of(InferenceStatus inference) {
            return new InferenceView(inference.mode().name(), inference.available(),
                    inference.models().entrySet().stream().sorted(Map.Entry.comparingByKey())
                            .map(model -> new ModelView(model.getKey(), model.getValue())).toList(),
                    inference.error());
        }
    }

    public record ModelView(String kind, String version) {
    }

    public record ScanView(long id, String world, String cause, String status, long doneTiles, long totalTiles,
                           long findings, long failures, String startedAt) {

        static ScanView of(ScanJob job) {
            return new ScanView(job.id(), job.world(), job.cause().name(), job.status().name(), job.tilesDone(),
                    job.tilesTotal(), job.findings(), job.failures(), job.createdAt().toString());
        }
    }

    public record TexturesView(boolean enabled, boolean available, String version) {
    }
}
