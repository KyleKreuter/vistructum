package de.kylekreuter.vistructum.core.face;

import de.kylekreuter.vistructum.api.PlayerFace;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface FaceSource {

    CompletableFuture<PlayerFace> fetch(UUID player, Optional<String> knownName);
}
