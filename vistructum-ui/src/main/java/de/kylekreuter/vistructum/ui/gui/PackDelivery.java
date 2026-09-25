package de.kylekreuter.vistructum.ui.gui;

import de.kylekreuter.vistructum.ui.text.Message;
import de.kylekreuter.vistructum.ui.text.Messages;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public final class PackDelivery implements Listener {

    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("vistructum-ui".getBytes(StandardCharsets.UTF_8));

    private final ResourcePack pack;
    private final URI publicUrl;
    private final String staffPermission;
    private final Messages messages;

    public PackDelivery(ResourcePack pack, URI publicUrl, String staffPermission, Messages messages) {
        this.pack = Objects.requireNonNull(pack, "pack");
        this.publicUrl = Objects.requireNonNull(publicUrl, "publicUrl");
        this.staffPermission = Objects.requireNonNull(staffPermission, "staffPermission");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(staffPermission)) {
            player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                    .packs(ResourcePackInfo.resourcePackInfo(PACK_ID, publicUrl, pack.sha1()))
                    .required(false)
                    .prompt(messages.chat(Message.PACK_PROMPT)));
        }
    }
}
