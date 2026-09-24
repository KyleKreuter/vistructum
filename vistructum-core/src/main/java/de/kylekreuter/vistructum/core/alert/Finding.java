package de.kylekreuter.vistructum.core.alert;

import de.kylekreuter.vistructum.core.scene.WorldBox;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * One suspected symbol for staff to review.
 *
 * @param source "mask" (recent building activity) or "fullscan" (daily surface scan)
 * @param detail how it was seen, e.g. the projection axis or the scan tile
 */
public record Finding(String source, String world, WorldBox box, double score, int votes, Set<UUID> players,
                      String detail, Instant time) {

    public Finding {
        players = Set.copyOf(players);
    }

    public int centerX() {
        return Math.floorDiv(box.minX() + box.maxX(), 2);
    }

    public int centerZ() {
        return Math.floorDiv(box.minZ() + box.maxZ(), 2);
    }

    boolean overlaps(Finding other) {
        return world.equals(other.world)
                && box.minX() <= other.box.maxX() && other.box.minX() <= box.maxX()
                && box.minZ() <= other.box.maxZ() && other.box.minZ() <= box.maxZ()
                && box.minY() <= other.box.maxY() && other.box.minY() <= box.maxY();
    }
}
