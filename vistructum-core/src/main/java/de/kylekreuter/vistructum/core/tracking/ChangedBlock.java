package de.kylekreuter.vistructum.core.tracking;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ChangedBlock(ChangeKind kind, String material, Set<UUID> placers, Set<UUID> breakers) {

    public ChangedBlock {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(material, "material");
        placers = Set.copyOf(placers);
        breakers = Set.copyOf(breakers);
    }
}
