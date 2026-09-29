package de.kylekreuter.vistructum.core.skin;

import java.time.Instant;
import java.util.Objects;

public record StoredSkin(SkinLookup lookup, Instant fetchedAt) {

    public StoredSkin {
        Objects.requireNonNull(lookup, "lookup");
        Objects.requireNonNull(fetchedAt, "fetchedAt");
    }
}
