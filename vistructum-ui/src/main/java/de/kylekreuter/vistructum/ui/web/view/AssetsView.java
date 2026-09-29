package de.kylekreuter.vistructum.ui.web.view;

import de.kylekreuter.vistructum.ui.web.assets.GameAssets;

public record AssetsView(boolean available, String version, boolean downloading) {

    public static AssetsView of(GameAssets assets) {
        return new AssetsView(assets.available(), assets.cachedVersion().orElse(null), assets.downloading());
    }
}
