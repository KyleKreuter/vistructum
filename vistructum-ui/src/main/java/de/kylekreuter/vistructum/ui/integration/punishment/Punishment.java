package de.kylekreuter.vistructum.ui.integration.punishment;

import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record Punishment(PunishmentKind kind, Optional<String> reason, Optional<String> operatorName,
                         Optional<UUID> operatorId, Optional<Instant> issuedAt, Optional<Instant> expiresAt,
                         boolean active) {

    public static final Comparator<Punishment> NEWEST_FIRST = Comparator
            .comparing((Punishment punishment) -> punishment.issuedAt().orElse(Instant.MAX)).reversed();

    public Punishment {
        Objects.requireNonNull(kind, "kind");
        reason = Objects.requireNonNull(reason, "reason").filter(text -> !text.isBlank());
        operatorName = Objects.requireNonNull(operatorName, "operatorName").filter(name -> !name.isBlank());
        Objects.requireNonNull(operatorId, "operatorId");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
