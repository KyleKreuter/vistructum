package de.kylekreuter.vistructum.ui;

import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.ui.text.Message;
import de.kylekreuter.vistructum.ui.text.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class Teleports {

    private static final int HEIGHT_ABOVE = 3;

    private Teleports() {
    }

    public static void toFinding(Player player, Finding finding, Messages messages) {
        World world = Bukkit.getWorld(finding.world());
        if (world == null) {
            player.sendMessage(messages.chat(Message.COMMAND_WORLD_NOT_LOADED, Messages.text("world", finding.world())));
            return;
        }
        player.teleportAsync(new Location(world, finding.box().centerX() + 0.5, finding.box().maxY() + HEIGHT_ABOVE,
                finding.box().centerZ() + 0.5));
    }
}
