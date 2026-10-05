package dev.servereer.morphedtrials;

import org.bukkit.Location;
import org.bukkit.World;

/** An immutable, axis-aligned cuboid region in a single world. */
public final class Region {

    private final String world;
    private final int minX, minY, minZ, maxX, maxY, maxZ;

    public Region(String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.world = world;
        this.minX = Math.min(x1, x2);
        this.maxX = Math.max(x1, x2);
        this.minY = Math.min(y1, y2);
        this.maxY = Math.max(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxZ = Math.max(z1, z2);
    }

    public String world() { return world; }
    public int minX() { return minX; }
    public int minY() { return minY; }
    public int minZ() { return minZ; }
    public int maxX() { return maxX; }
    public int maxY() { return maxY; }
    public int maxZ() { return maxZ; }
    public int sizeX() { return maxX - minX + 1; }
    public int sizeY() { return maxY - minY + 1; }
    public int sizeZ() { return maxZ - minZ + 1; }

    public boolean contains(Location loc) {
        World w = loc.getWorld();
        if (w == null || !w.getName().equals(world)) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** The floor-centre of the region, one block above its lowest layer — a sane default stand point. */
    public Location standPoint(World w) {
        return new Location(w, minX + sizeX() / 2.0, minY + 1, minZ + sizeZ() / 2.0);
    }

    /** Same world AND same exact corners — not just same size. A morph always writes back to its
     *  snapshot's own stored coordinates, so every level of one dungeon must be captured from this exact
     *  box, not merely a same-sized one elsewhere — see Level.setregion's validation in MTCommand. */
    public boolean sameBox(Region o) {
        return o != null && world.equals(o.world)
                && minX == o.minX && minY == o.minY && minZ == o.minZ
                && maxX == o.maxX && maxY == o.maxY && maxZ == o.maxZ;
    }

    public String describe() {
        return world + " (" + minX + "," + minY + "," + minZ + ") -> (" + maxX + "," + maxY + "," + maxZ + ")"
                + "  [" + sizeX() + "x" + sizeY() + "x" + sizeZ() + "]";
    }

    public String serialize() {
        return world + ";" + minX + ";" + minY + ";" + minZ + ";" + maxX + ";" + maxY + ";" + maxZ;
    }

    public static Region deserialize(String s) {
        String[] p = s.split(";");
        if (p.length != 7) throw new IllegalArgumentException("Bad region string: " + s);
        return new Region(p[0],
                Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]),
                Integer.parseInt(p[4]), Integer.parseInt(p[5]), Integer.parseInt(p[6]));
    }
}
