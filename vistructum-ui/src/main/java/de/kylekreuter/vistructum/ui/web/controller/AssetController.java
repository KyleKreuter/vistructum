package de.kylekreuter.vistructum.ui.web.controller;

import de.kylekreuter.vistructum.ui.web.Responses;
import de.kylekreuter.vistructum.ui.web.WebSettings;
import de.kylekreuter.vistructum.ui.web.assets.GameAssets;
import de.kylekreuter.vistructum.ui.web.error.ApiError;
import de.kylekreuter.vistructum.ui.web.filter.Access;
import de.kylekreuter.vistructum.ui.web.view.AssetsView;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class AssetController {

    private static final String ASSETS = WebSettings.API + "assets";
    private static final String TEXTURE_SUFFIX = ".png";
    private static final String PALETTE_CACHE = "public, max-age=86400";

    private final GameAssets assets;
    private final Map<String, Integer> palette;

    public AssetController(GameAssets assets, Map<String, Integer> palette) {
        this.assets = Objects.requireNonNull(assets, "assets");
        this.palette = new TreeMap<>(palette);
    }

    public void register(JavalinConfig config) {
        Routes.read(config, WebSettings.API + "palette", this::palette, Access.PUBLIC);
        Routes.read(config, ASSETS, this::overview, Access.PUBLIC);
        Routes.read(config, ASSETS + "/{version}/models.json", this::models, Access.PUBLIC);
        Routes.read(config, ASSETS + "/{version}/textures/<file>", this::texture, Access.PUBLIC);
        Routes.read(config, ASSETS + "/*", ctx -> {
            throw ApiError.notFound();
        }, Access.PUBLIC);
    }

    private void palette(Context ctx) {
        Responses.json(ctx, palette);
        ctx.header(Responses.CACHE_CONTROL, PALETTE_CACHE);
    }

    private void overview(Context ctx) {
        Responses.json(ctx, AssetsView.of(assets));
        ctx.header(Responses.CACHE_CONTROL, Responses.NO_CACHE);
    }

    private void models(Context ctx) {
        Responses.file(ctx, Responses.JSON, assets.models(ctx.pathParam("version")).orElseThrow(ApiError::notFound),
                true);
    }

    private void texture(Context ctx) {
        String file = ctx.pathParam("file");
        if (!file.endsWith(TEXTURE_SUFFIX)) {
            throw ApiError.notFound();
        }
        String id = file.substring(0, file.length() - TEXTURE_SUFFIX.length());
        Responses.file(ctx, Responses.PNG,
                assets.texture(ctx.pathParam("version"), id).orElseThrow(ApiError::notFound), false);
    }
}
