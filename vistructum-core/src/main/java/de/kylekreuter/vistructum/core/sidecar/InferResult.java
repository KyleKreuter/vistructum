package de.kylekreuter.vistructum.core.sidecar;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public record InferResult(
        String kind,
        @SerializedName("model_version") String modelVersion,
        double threshold,
        @SerializedName("min_votes") int minVotes,
        int windows,
        @SerializedName("max_score") double maxScore,
        boolean flagged,
        List<Detection> detections,
        @SerializedName("elapsed_ms") double elapsedMs) {
}
