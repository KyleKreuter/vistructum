package de.kylekreuter.vistructum.core.skin;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface SkinSource {

    CompletableFuture<SkinLookup> fetch(UUID player, Optional<String> knownName);
}
