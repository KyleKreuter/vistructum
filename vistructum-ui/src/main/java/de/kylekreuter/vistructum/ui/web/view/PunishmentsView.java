package de.kylekreuter.vistructum.ui.web.view;

import de.kylekreuter.vistructum.ui.integration.punishment.Punishment;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public record PunishmentsView(String source, List<PunishmentView> items) {

    public static PunishmentsView of(String source, List<Punishment> punishments, Map<UUID, Optional<String>> names) {
        return new PunishmentsView(source, punishments.stream().map(punishment -> new PunishmentView(
                punishment.kind().name(), punishment.reason().orElse(null),
                punishment.operatorName().or(() -> punishment.operatorId()
                        .flatMap(id -> names.getOrDefault(id, Optional.empty()))).orElse(null),
                punishment.issuedAt().map(Instant::toString).orElse(null),
                punishment.expiresAt().map(Instant::toString).orElse(null), punishment.active())).toList());
    }

    public record PunishmentView(String type, String reason, String operator, String issuedAt, String expiresAt,
                                 boolean active) {
    }
}
