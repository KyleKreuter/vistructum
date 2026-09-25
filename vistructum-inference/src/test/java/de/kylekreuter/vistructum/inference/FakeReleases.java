package de.kylekreuter.vistructum.inference;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class FakeReleases implements ReleaseSource {

    private final List<ModelRelease.Entry> entries = new ArrayList<>();
    private final Map<String, byte[]> files = new HashMap<>();
    int downloads;

    FakeReleases offer(String kind, byte[] bytes, String featureSpec) {
        return offer(kind, bytes, Model.sha256(bytes), featureSpec);
    }

    FakeReleases offer(String kind, byte[] bytes, String sha256, String featureSpec) {
        String file = kind + ".onnx";
        entries.add(new ModelRelease.Entry(kind, file, sha256, "v", featureSpec));
        files.put(file, bytes);
        return this;
    }

    @Override
    public Optional<ModelRelease> latest() {
        return entries.isEmpty() ? Optional.empty() : Optional.of(new ModelRelease("models-test", entries, Map.of()));
    }

    @Override
    public byte[] download(ModelRelease release, ModelRelease.Entry entry) throws IOException {
        downloads++;
        byte[] bytes = files.get(entry.file());
        if (bytes == null) {
            throw new IOException("missing " + entry.file());
        }
        return bytes;
    }
}
