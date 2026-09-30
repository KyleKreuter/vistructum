package de.kylekreuter.vistructum.ui.integration.punishment;

import io.papermc.paper.ban.BanListType;
import org.bukkit.BanEntry;
import org.bukkit.Bukkit;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

final class MinecraftBanLog implements PunishmentLog {

    private final Executor mainThread;
    private final Optional<EssentialsMutes> mutes;
    private final Clock clock;

    MinecraftBanLog(Executor mainThread, Optional<EssentialsMutes> mutes, Clock clock) {
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.mutes = Objects.requireNonNull(mutes, "mutes");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String source() {
        return mutes.isPresent() ? "EssentialsX" : "Minecraft";
    }

    @Override
    public CompletableFuture<List<Punishment>> history(UUID player) {
        return CompletableFuture.supplyAsync(() -> {
            List<Punishment> punishments = new ArrayList<>();
            ban(player).ifPresent(punishments::add);
            mutes.flatMap(essentials -> essentials.mute(player)).ifPresent(punishments::add);
            return punishments;
        }, mainThread);
    }

    private Optional<Punishment> ban(UUID player) {
        BanEntry<?> entry = Bukkit.getBanList(BanListType.PROFILE).getBanEntry(Bukkit.createProfile(player));
        if (entry == null) {
            return Optional.empty();
        }
        Optional<Instant> expiresAt = Optional.ofNullable(entry.getExpiration()).map(Date::toInstant);
        return Optional.of(new Punishment(PunishmentKind.BAN, Optional.ofNullable(entry.getReason()),
                Optional.ofNullable(entry.getSource()), Optional.empty(),
                Optional.ofNullable(entry.getCreated()).map(Date::toInstant), expiresAt,
                expiresAt.map(clock.instant()::isBefore).orElse(true)));
    }
}
