package de.kylekreuter.vistructum.inference;

import java.io.IOException;
import java.util.Optional;

public interface ReleaseSource {

    Optional<ModelRelease> latest() throws IOException;

    byte[] download(ModelRelease release, ModelRelease.Entry entry) throws IOException;
}
