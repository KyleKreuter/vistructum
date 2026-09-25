package de.kylekreuter.vistructum.ui.gui;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class MapCards {

    static final int MAP_PIXELS = 128;
    private static final String IDS = "ids";

    private final List<MapView> views;
    private final CardRenderer renderer;

    private MapCards(List<MapView> views, CardRenderer renderer) {
        this.views = views;
        this.renderer = renderer;
    }

    public static MapCards load(File file, World world) throws IOException {
        YamlConfiguration stored = YamlConfiguration.loadConfiguration(file);
        List<Integer> ids = stored.getIntegerList(IDS);
        List<MapView> views = new ArrayList<>();
        List<Integer> kept = new ArrayList<>();
        CardRenderer renderer = new CardRenderer();
        for (int index = 0; index < Layout.LIST_CARDS.size(); index++) {
            MapView view = index < ids.size() ? Bukkit.getMap(ids.get(index)) : null;
            if (view == null) {
                view = Bukkit.createMap(world);
            }
            view.getRenderers().forEach(view::removeRenderer);
            view.setTrackingPosition(false);
            view.setUnlimitedTracking(false);
            view.setLocked(true);
            view.addRenderer(renderer);
            views.add(view);
            kept.add(view.getId());
        }
        if (!kept.equals(ids)) {
            stored.set(IDS, kept);
            stored.save(file);
        }
        return new MapCards(List.copyOf(views), renderer);
    }

    MapView view(int index) {
        return views.get(index);
    }

    void send(Player player, int index, Picture picture) {
        renderer.pending = image(picture.resample(MAP_PIXELS));
        try {
            player.sendMap(views.get(index));
        } finally {
            renderer.pending = null;
        }
    }

    private static BufferedImage image(Picture picture) {
        BufferedImage image = new BufferedImage(picture.width(), picture.height(), BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, picture.width(), picture.height(), picture.rgb(), 0, picture.width());
        return image;
    }

    private static final class CardRenderer extends MapRenderer {

        private BufferedImage pending;

        CardRenderer() {
            super(true);
        }

        @Override
        public void render(MapView map, MapCanvas canvas, Player player) {
            if (pending != null) {
                canvas.drawImage(0, 0, pending);
            }
        }
    }
}
