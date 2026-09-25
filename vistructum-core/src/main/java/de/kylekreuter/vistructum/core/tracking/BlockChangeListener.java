package de.kylekreuter.vistructum.core.tracking;

import de.kylekreuter.vistructum.core.scene.BlockPos;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Objects;
import java.util.function.LongSupplier;

public final class BlockChangeListener implements Listener {

    private final BlockChangeStore store;
    private final LongSupplier clock;

    public BlockChangeListener(BlockChangeStore store, LongSupplier clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!(event instanceof BlockMultiPlaceEvent)) {
            record(event.getBlock(), event.getPlayer(), ChangeKind.PLACE);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMultiPlace(BlockMultiPlaceEvent event) {
        long now = clock.getAsLong();
        for (BlockState state : event.getReplacedBlockStates()) {
            Location location = state.getLocation();
            store.record(location.getWorld().getName(),
                    new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ()),
                    event.getPlayer().getUniqueId(), ChangeKind.PLACE, location.getBlock().getType().name(), now);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        record(event.getBlock(), event.getPlayer(), ChangeKind.BREAK);
    }

    private void record(Block block, Player player, ChangeKind kind) {
        store.record(block.getWorld().getName(), new BlockPos(block.getX(), block.getY(), block.getZ()),
                player.getUniqueId(), kind, block.getType().name(), clock.getAsLong());
    }
}
