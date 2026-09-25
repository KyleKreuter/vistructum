package de.kylekreuter.vistructum.inference;

import com.google.gson.annotations.SerializedName;

import java.net.URI;
import java.util.List;
import java.util.Map;

public record ModelRelease(String tag, List<Entry> models, Map<String, URI> assets) {

    public ModelRelease {
        models = List.copyOf(models);
        assets = Map.copyOf(assets);
    }

    public record Entry(
            String kind,
            String file,
            String sha256,
            @SerializedName("model_version") String version,
            @SerializedName("feature_spec") String featureSpec) {
    }

    record Manifest(List<Entry> models) {
    }
}
