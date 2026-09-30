package de.kylekreuter.vistructum.ui.integration.punishment;

import com.earth2me.essentials.IEssentials;
import com.earth2me.essentials.User;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class EssentialsMutes {

    private final IEssentials essentials;

    EssentialsMutes(Plugin plugin) {
        this.essentials = (IEssentials) Objects.requireNonNull(plugin, "plugin");
    }

    Optional<Punishment> mute(UUID player) {
        User user = essentials.getUser(player);
        if (user == null || !user.isMuted()) {
            return Optional.empty();
        }
        long timeout = user.getMuteTimeout();
        return Optional.of(new Punishment(PunishmentKind.MUTE,
                user.hasMuteReason() ? Optional.of(user.getMuteReason()) : Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(),
                timeout > 0 ? Optional.of(Instant.ofEpochMilli(timeout)) : Optional.empty(), true));
    }
}
