package de.kylekreuter.vistructum.ui.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import de.kylekreuter.vistructum.api.Activity;
import de.kylekreuter.vistructum.api.BlockBox;
import de.kylekreuter.vistructum.api.BlockEvent;
import de.kylekreuter.vistructum.api.BlockVolume;
import de.kylekreuter.vistructum.api.DailyStats;
import de.kylekreuter.vistructum.api.Evidence;
import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.FindingScene;
import de.kylekreuter.vistructum.api.FindingStats;
import de.kylekreuter.vistructum.api.Heatmap;
import de.kylekreuter.vistructum.api.InferenceStatus;
import de.kylekreuter.vistructum.api.MotionFrame;
import de.kylekreuter.vistructum.api.Recording;
import de.kylekreuter.vistructum.api.ReviewerStats;
import de.kylekreuter.vistructum.api.ScanJob;
import de.kylekreuter.vistructum.api.Source;
import de.kylekreuter.vistructum.api.SourcePrecision;
import de.kylekreuter.vistructum.api.VistructumStatus;
import de.kylekreuter.vistructum.api.WebSession;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class Views {

    private Views() {
    }

    static JsonObject me(WebSession session, boolean canShare) {
        JsonObject me = new JsonObject();
        me.addProperty("player", session.player().toString());
        me.addProperty("name", session.playerName());
        me.addProperty("expiresAt", session.expiresAt().toString());
        me.addProperty("canShare", canShare);
        return me;
    }

    static JsonObject finding(Finding finding, Map<UUID, Optional<String>> names, boolean hasEvidence,
                              Optional<Instant> sharedSince) {
        JsonObject json = new JsonObject();
        json.addProperty("id", finding.id());
        json.addProperty("source", source(finding.source()));
        json.addProperty("world", finding.world());
        json.add("box", box(finding.box()));
        json.addProperty("score", finding.score());
        json.addProperty("votes", finding.votes());
        json.addProperty("detail", finding.detail());
        json.addProperty("modelVersion", finding.modelVersion());
        json.addProperty("createdAt", finding.createdAt().toString());
        json.add("players", players(finding.players(), names));
        json.add("review", finding.review().<JsonElement>map(review -> {
            JsonObject object = new JsonObject();
            object.addProperty("verdict", review.verdict().name());
            object.addProperty("reviewer", review.reviewer());
            object.addProperty("reviewedAt", review.reviewedAt().toString());
            return object;
        }).orElse(JsonNull.INSTANCE));
        json.addProperty("hasEvidence", hasEvidence);
        json.add("sharedSince", time(sharedSince));
        return json;
    }

    static String teleport(BlockBox box, Optional<String> dimension) {
        String tp = "tp @s " + box.centerX() + " " + Math.floorDiv(box.minY() + box.maxY(), 2) + " " + box.centerZ();
        return dimension.map(key -> "/execute in " + key + " run " + tp).orElse("/" + tp);
    }

    static JsonObject publicFinding(Finding finding, Map<UUID, Optional<String>> names) {
        JsonObject json = new JsonObject();
        json.addProperty("id", finding.id());
        json.addProperty("createdAt", finding.createdAt().toString());
        json.addProperty("verdict", "CONFIRMED");
        json.add("players", players(finding.players(), names));
        return json;
    }

    static JsonArray players(Collection<UUID> players, Map<UUID, Optional<String>> names) {
        JsonArray array = new JsonArray();
        players.stream().sorted(Comparator.comparing(UUID::toString)).forEach(player -> {
            JsonObject object = new JsonObject();
            object.addProperty("uuid", player.toString());
            object.add("name", text(names.getOrDefault(player, Optional.empty())));
            array.add(object);
        });
        return array;
    }

    static JsonObject status(VistructumStatus status, boolean recordingEnabled) {
        JsonObject json = new JsonObject();
        json.addProperty("trackedChanges", status.trackedChanges());
        json.addProperty("openFindings", status.openFindings());
        json.add("inference", inference(status.inference()));
        JsonArray scans = new JsonArray();
        status.activeScans().forEach(job -> scans.add(scan(job)));
        json.add("scans", scans);
        json.addProperty("recordingEnabled", recordingEnabled);
        return json;
    }

    private static JsonObject inference(InferenceStatus inference) {
        JsonObject json = new JsonObject();
        json.addProperty("mode", inference.mode().name());
        json.addProperty("available", inference.available());
        JsonArray models = new JsonArray();
        inference.models().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(model -> {
            JsonObject object = new JsonObject();
            object.addProperty("kind", model.getKey());
            object.addProperty("version", model.getValue());
            models.add(object);
        });
        json.add("models", models);
        inference.error().ifPresent(error -> json.addProperty("detail", error));
        return json;
    }

    private static JsonObject scan(ScanJob job) {
        JsonObject json = new JsonObject();
        json.addProperty("id", job.id());
        json.addProperty("world", job.world());
        json.addProperty("cause", job.cause().name());
        json.addProperty("status", job.status().name());
        json.addProperty("doneTiles", job.tilesDone());
        json.addProperty("totalTiles", job.tilesTotal());
        json.addProperty("findings", job.findings());
        json.addProperty("failures", job.failures());
        json.addProperty("startedAt", job.createdAt().toString());
        return json;
    }

    static JsonObject scene(FindingScene scene) {
        JsonObject json = new JsonObject();
        json.addProperty("source", source(scene.source()));
        json.addProperty("width", scene.width());
        json.addProperty("height", scene.height());
        JsonObject window = new JsonObject();
        window.addProperty("top", scene.windowTop());
        window.addProperty("left", scene.windowLeft());
        window.addProperty("bottom", scene.windowBottom());
        window.addProperty("right", scene.windowRight());
        json.add("window", window);
        json.add("palette", strings(scene.palette()));
        json.add("blocks", ints(scene.blocks()));
        json.add("heights", ints(scene.heights()));
        json.add("luminance", ints(scene.luminance()));
        return json;
    }

    static JsonObject heatmap(Heatmap heatmap) {
        JsonObject json = new JsonObject();
        json.addProperty("width", heatmap.width());
        json.addProperty("height", heatmap.height());
        json.add("values", ints(heatmap.values()));
        return json;
    }

    static JsonObject evidence(Evidence evidence, int shiftX, int shiftY, int shiftZ) {
        BlockVolume before = evidence.before();
        JsonObject json = new JsonObject();
        json.addProperty("findingId", evidence.findingId());
        JsonObject volume = new JsonObject();
        volume.addProperty("minX", before.minX() - shiftX);
        volume.addProperty("minY", before.minY() - shiftY);
        volume.addProperty("minZ", before.minZ() - shiftZ);
        volume.addProperty("sizeX", before.sizeX());
        volume.addProperty("sizeY", before.sizeY());
        volume.addProperty("sizeZ", before.sizeZ());
        volume.add("palette", strings(before.palette()));
        volume.add("cells", ints(before.cells()));
        json.add("before", volume);
        JsonArray changes = new JsonArray();
        for (BlockEvent change : evidence.changes()) {
            JsonObject object = new JsonObject();
            object.addProperty("t", change.at().toEpochMilli());
            object.addProperty("player", change.player().toString());
            object.addProperty("playerName", change.playerName());
            object.addProperty("action", change.action().name());
            object.addProperty("x", change.x() - shiftX);
            object.addProperty("y", change.y() - shiftY);
            object.addProperty("z", change.z() - shiftZ);
            object.addProperty("blockData", change.blockData());
            changes.add(object);
        }
        json.add("changes", changes);
        JsonArray recordings = new JsonArray();
        for (Recording recording : evidence.recordings()) {
            JsonObject object = new JsonObject();
            object.addProperty("player", recording.player().toString());
            object.addProperty("playerName", recording.playerName());
            JsonArray frames = new JsonArray();
            for (MotionFrame frame : recording.frames()) {
                JsonObject point = new JsonObject();
                point.addProperty("t", frame.atMillis());
                point.addProperty("x", frame.x() - shiftX);
                point.addProperty("y", frame.y() - shiftY);
                point.addProperty("z", frame.z() - shiftZ);
                point.addProperty("yaw", frame.yaw());
                point.addProperty("pitch", frame.pitch());
                point.addProperty("flags", frame.flags());
                point.addProperty("mainHand", frame.mainHand());
                frames.add(point);
            }
            object.add("frames", frames);
            recordings.add(object);
        }
        json.add("recordings", recordings);
        return json;
    }

    static JsonObject stats(FindingStats stats, List<SourcePrecision> precision) {
        JsonObject json = new JsonObject();
        JsonArray days = new JsonArray();
        for (DailyStats day : stats.days()) {
            JsonObject object = new JsonObject();
            object.addProperty("day", day.day().toString());
            object.addProperty("source", source(day.source()));
            object.addProperty("created", day.created());
            object.addProperty("confirmed", day.confirmed());
            object.addProperty("falseAlarms", day.falseAlarms());
            days.add(object);
        }
        json.add("days", days);
        JsonArray reviewers = new JsonArray();
        for (ReviewerStats reviewer : stats.reviewers()) {
            JsonObject object = new JsonObject();
            object.addProperty("reviewer", reviewer.reviewer());
            object.addProperty("confirmed", reviewer.confirmed());
            object.addProperty("falseAlarms", reviewer.falseAlarms());
            reviewers.add(object);
        }
        json.add("reviewers", reviewers);
        json.addProperty("open", stats.open());
        json.add("oldestOpen", time(stats.oldestOpen()));
        JsonArray sources = new JsonArray();
        for (SourcePrecision source : precision) {
            JsonObject object = new JsonObject();
            object.addProperty("source", source(source.source()));
            object.addProperty("confirmed", source.confirmed());
            object.addProperty("falseAlarms", source.falseAlarms());
            sources.add(object);
        }
        json.add("precision", sources);
        return json;
    }

    static JsonObject activity(List<Activity> activity) {
        JsonArray items = new JsonArray();
        for (Activity entry : activity) {
            JsonObject object = new JsonObject();
            object.addProperty("at", entry.at().toString());
            object.addProperty("actor", entry.actor());
            object.addProperty("kind", entry.kind().name());
            object.addProperty("findingId", entry.findingId());
            items.add(object);
        }
        JsonObject json = new JsonObject();
        json.add("items", items);
        return json;
    }

    static JsonObject palette(Map<String, Integer> palette) {
        JsonObject json = new JsonObject();
        palette.forEach(json::addProperty);
        return json;
    }

    static String source(Source source) {
        return source.modelKind();
    }

    static JsonElement time(Optional<Instant> time) {
        return time.<JsonElement>map(instant -> new JsonPrimitive(instant.toString())).orElse(JsonNull.INSTANCE);
    }

    private static JsonElement text(Optional<String> text) {
        return text.<JsonElement>map(JsonPrimitive::new).orElse(JsonNull.INSTANCE);
    }

    private static JsonObject box(BlockBox box) {
        JsonObject json = new JsonObject();
        json.addProperty("minX", box.minX());
        json.addProperty("minY", box.minY());
        json.addProperty("minZ", box.minZ());
        json.addProperty("maxX", box.maxX());
        json.addProperty("maxY", box.maxY());
        json.addProperty("maxZ", box.maxZ());
        return json;
    }

    private static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray(values.size());
        values.forEach(array::add);
        return array;
    }

    private static JsonArray ints(int[] values) {
        JsonArray array = new JsonArray(values.length);
        for (int value : values) {
            array.add(value);
        }
        return array;
    }
}
