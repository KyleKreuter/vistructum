package de.kylekreuter.vistructum.core.face;

import de.kylekreuter.vistructum.api.PlayerFace;

import java.time.Instant;

public record StoredFace(PlayerFace face, Instant fetchedAt) {
}
