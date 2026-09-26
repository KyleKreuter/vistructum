package de.kylekreuter.vistructum.core.scan;

import de.kylekreuter.vistructum.core.region.PaletteEntry;

@FunctionalInterface
public interface BlockStates {

    StateInfo info(PaletteEntry entry);

    record StateInfo(short material, boolean surface, boolean solid, int luminance) {
    }
}
