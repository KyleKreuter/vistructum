package de.kylekreuter.vistructum.core.tracking;

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

/** Feeds every block placement/break, by every player, into a {@link ModificationTracker}. */
public final class BlockChangeListener implements Listener {

    private final ModificationTracker tracker;
    private final LongSupplier clock;

    public BlockChangeListener(ModificationTracker tracker) {
        this(tracker, System::currentTimeMillis);
    }

    public BlockChangeListener(ModificationTracker tracker, LongSupplier clock) {
        this.tracker = Objects.requireNonNull(tracker, "tracker");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        record(event.getBlock(), event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMultiPlace(BlockMultiPlaceEvent event) {
        var player = event.getPlayer().getUniqueId();
        long now = clock.getAsLong();
        for (BlockState state : event.getReplacedBlockStates()) {
            Location loc = state.getLocation();
            tracker.record(loc.getWorld().getUID(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), player, now);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        record(event.getBlock(), event.getPlayer());
    }

    private void record(Block block, Player player) {
        tracker.record(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ(),
                player.getUniqueId(), clock.getAsLong());
    }
}
