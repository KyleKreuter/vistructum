package de.kylekreuter.vistructum.core.region;

import java.util.Optional;

public record RegionChunk(int chunkX, int chunkZ, Optional<byte[]> nbt) {
}
