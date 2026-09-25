package de.kylekreuter.vistructum.inference;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public record ModelInfo(
        String kind,
        @SerializedName("model_version") String version,
        List<String> labels,
        double threshold,
        @SerializedName("feature_spec") String featureSpec,
        String commit,
        @SerializedName("min_votes") int minVotes,
        boolean tta,
        Double prefilter,
        String sha256) {
}
