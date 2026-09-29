package de.kylekreuter.vistructum.core.recording;

import de.kylekreuter.vistructum.api.MotionFrame;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class MotionRecorder implements Listener {

    static final long CHUNK_MILLIS = 5000;

    private final Plugin plugin;
    private final MotionStore store;
    private final LongSupplier clock;
    private final Duration keep;
    private final Map<UUID, MotionBuffer> buffers = new HashMap<>();
    private final Map<UUID, Integer> swingTicks = new HashMap<>();
    private BukkitTask task;

    public MotionRecorder(Plugin plugin, MotionStore store, LongSupplier clock, Duration keep) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.keep = Objects.requireNonNull(keep, "keep");
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::sample, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        flushAll();
    }

    public void flushAll() {
        List<MotionChunk> chunks = new ArrayList<>();
        buffers.values().forEach(buffer -> chunks.add(buffer.chunk()));
        buffers.clear();
        write(chunks);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnimation(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        UUID player = event.getPlayer().getUniqueId();
        int tick = Bukkit.getCurrentTick();
        MotionBuffer buffer = buffers.get(player);
        if (buffer != null && buffer.lastTick() == tick) {
            buffer.markLastSwinging();
        } else {
            swingTicks.put(player, tick);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID player = event.getPlayer().getUniqueId();
        swingTicks.remove(player);
        MotionBuffer buffer = buffers.remove(player);
        if (buffer != null) {
            write(List.of(buffer.chunk()));
        }
    }

    private void sample() {
        long now = clock.getAsLong();
        int tick = Bukkit.getCurrentTick();
        List<MotionChunk> full = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            Location location = player.getLocation();
            String world = location.getWorld().getName();
            MotionBuffer buffer = buffers.get(id);
            if (buffer != null && (!buffer.world().equals(world) || now - buffer.startMillis() >= CHUNK_MILLIS)) {
                full.add(buffer.chunk());
                buffer = null;
            }
            if (buffer == null) {
                buffer = new MotionBuffer(id, player.getName(), world);
                buffers.put(id, buffer);
            }
            Integer swing = swingTicks.remove(id);
            buffer.add(frame(player, location, now, swing != null && swing == tick), tick);
        }
        write(full);
    }

    private void write(List<MotionChunk> chunks) {
        if (!chunks.isEmpty()) {
            store.append(chunks, clock.getAsLong() - keep.toMillis());
        }
    }

    static MotionFrame frame(Player player, Location location, long now, boolean swinging) {
        int flags = (player.isOnGround() ? MotionFrame.ON_GROUND : 0)
                | (player.isSneaking() ? MotionFrame.SNEAKING : 0)
                | (player.isSprinting() ? MotionFrame.SPRINTING : 0)
                | (player.isSwimming() ? MotionFrame.SWIMMING : 0)
                | (player.isGliding() ? MotionFrame.GLIDING : 0)
                | (swinging ? MotionFrame.SWINGING : 0);
        ItemStack hand = player.getInventory().getItemInMainHand();
        String mainHand = hand.getType() == Material.AIR ? "" : hand.getType().getKey().toString();
        return new MotionFrame(now, location.getX(), location.getY(), location.getZ(), location.getYaw(),
                location.getPitch(), flags, mainHand);
    }

    private static final class MotionBuffer {

        private final UUID player;
        private final String playerName;
        private final String world;
        private final List<MotionFrame> frames = new ArrayList<>();
        private int lastTick;

        private MotionBuffer(UUID player, String playerName, String world) {
            this.player = player;
            this.playerName = playerName;
            this.world = world;
        }

        private String world() {
            return world;
        }

        private long startMillis() {
            return frames.getFirst().atMillis();
        }

        private int lastTick() {
            return lastTick;
        }

        private void add(MotionFrame frame, int tick) {
            frames.add(frame);
            lastTick = tick;
        }

        private void markLastSwinging() {
            MotionFrame last = frames.getLast();
            frames.set(frames.size() - 1, new MotionFrame(last.atMillis(), last.x(), last.y(), last.z(), last.yaw(),
                    last.pitch(), last.flags() | MotionFrame.SWINGING, last.mainHand()));
        }

        private MotionChunk chunk() {
            return new MotionChunk(player, playerName, world, frames);
        }
    }
}
