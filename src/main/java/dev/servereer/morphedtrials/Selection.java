package dev.servereer.morphedtrials;

import org.bukkit.Location;

/** One player's in-progress wand selection: left-click sets pos1, right-click sets pos2. */
final class Selection {
    Location pos1;
    Location pos2;

    boolean complete() { return pos1 != null && pos2 != null && pos1.getWorld() != null
            && pos1.getWorld().equals(pos2.getWorld()); }

    Region toRegion() {
        if (!complete()) throw new IllegalStateException("Selection incomplete.");
        return new Region(pos1.getWorld().getName(),
                pos1.getBlockX(), pos1.getBlockY(), pos1.getBlockZ(),
                pos2.getBlockX(), pos2.getBlockY(), pos2.getBlockZ());
    }
}
