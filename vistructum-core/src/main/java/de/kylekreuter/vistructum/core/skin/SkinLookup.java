package de.kylekreuter.vistructum.core.skin;

import de.kylekreuter.vistructum.api.PlayerSkin;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record SkinLookup(UUID player, Optional<String> name, Optional<PlayerSkin> skin) {

    public SkinLookup {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(skin, "skin");
    }

    public static SkinLookup without(UUID player, Optional<String> name) {
        return new SkinLookup(player, name, Optional.empty());
    }

    public static SkinLookup of(UUID player, Optional<String> name, byte[] png, boolean slim) {
        return new SkinLookup(player, name, Optional.of(new PlayerSkin(player, name, png, slim)));
    }
}
