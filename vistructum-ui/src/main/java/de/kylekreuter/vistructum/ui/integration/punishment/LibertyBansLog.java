package de.kylekreuter.vistructum.ui.integration.punishment;

import space.arim.libertybans.api.LibertyBans;
import space.arim.libertybans.api.Operator;
import space.arim.libertybans.api.PlayerOperator;
import space.arim.libertybans.api.PlayerVictim;
import space.arim.libertybans.api.select.PunishmentSelector;
import space.arim.omnibus.OmnibusProvider;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

final class LibertyBansLog implements PunishmentLog {

    private static final String CONSOLE = "Console";

    private final PunishmentSelector selector;

    private LibertyBansLog(PunishmentSelector selector) {
        this.selector = Objects.requireNonNull(selector, "selector");
    }

    static Optional<PunishmentLog> connect() {
        return OmnibusProvider.getOmnibus().getRegistry().getProvider(LibertyBans.class)
                .map(libertyBans -> new LibertyBansLog(libertyBans.getSelector()));
    }

    @Override
    public String source() {
        return "LibertyBans";
    }

    @Override
    public CompletableFuture<List<Punishment>> history(UUID player) {
        CompletableFuture<List<space.arim.libertybans.api.punish.Punishment>> all = selector.selectionBuilder()
                .victim(PlayerVictim.of(player)).selectAll().limitToRetrieve(LIMIT).build()
                .getAllSpecificPunishments().toCompletableFuture();
        CompletableFuture<Set<Long>> active = selector.selectionBuilder().victim(PlayerVictim.of(player))
                .selectActiveOnly().build().getAllSpecificPunishments().toCompletableFuture()
                .thenApply(punishments -> punishments.stream()
                        .map(space.arim.libertybans.api.punish.Punishment::getIdentifier)
                        .collect(Collectors.toSet()));
        return all.thenCombine(active, (punishments, ids) -> punishments.stream()
                .map(punishment -> punishment(punishment, ids.contains(punishment.getIdentifier())))
                .toList());
    }

    private static Punishment punishment(space.arim.libertybans.api.punish.Punishment punishment, boolean active) {
        Operator operator = punishment.getOperator();
        Optional<UUID> operatorId = operator instanceof PlayerOperator player ? Optional.of(player.getUUID())
                : Optional.empty();
        return new Punishment(PunishmentKind.valueOf(punishment.getType().name()),
                Optional.ofNullable(punishment.getReason()),
                operatorId.isPresent() ? Optional.empty() : Optional.of(CONSOLE), operatorId,
                Optional.of(punishment.getStartDate()),
                punishment.isPermanent() ? Optional.empty() : Optional.of(punishment.getEndDate()), active);
    }
}
