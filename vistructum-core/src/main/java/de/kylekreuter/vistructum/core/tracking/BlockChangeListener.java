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
            Block placed = event.getBlockPlaced();
            record(placed.getLocation(), event.getPlayer(), ChangeKind.PLACE, placed.getBlockData().getAsString(),
                    event.getBlockReplacedState().getBlockData().getAsString(), clock.getAsLong());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMultiPlace(BlockMultiPlaceEvent event) {
        long now = clock.getAsLong();
        for (BlockState replaced : event.getReplacedBlockStates()) {
            Location location = replaced.getLocation();
            record(location, event.getPlayer(), ChangeKind.PLACE, location.getBlock().getBlockData().getAsString(),
                    replaced.getBlockData().getAsString(), now);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        String broken = event.getBlock().getBlockData().getAsString();
        record(event.getBlock().getLocation(), event.getPlayer(), ChangeKind.BREAK, broken, broken, clock.getAsLong());
    }

    private void record(Location location, Player player, ChangeKind kind, String blockData, String previousData,
                        long changedAt) {
        store.record(new TrackedChange(location.getWorld().getName(),
                new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ()), player.getUniqueId(),
                player.getName(), kind, blockData, previousData, changedAt));
    }
}
