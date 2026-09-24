package de.kylekreuter.vistructum.core.alert;

import de.kylekreuter.vistructum.api.BlockBox;
import de.kylekreuter.vistructum.api.Preview;
import de.kylekreuter.vistructum.api.Source;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record FindingDraft(Source source, String world, BlockBox box, double score, int votes, Set<UUID> players,
                           String detail, String modelVersion, Preview preview) {

    public FindingDraft {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(box, "box");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(modelVersion, "modelVersion");
        Objects.requireNonNull(preview, "preview");
        players = Set.copyOf(players);
    }
}
