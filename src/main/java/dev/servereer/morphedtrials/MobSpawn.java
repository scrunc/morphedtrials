package dev.servereer.morphedtrials;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;

/** One mob spawn point in a level: what to spawn, where, and how many at once. */
public record MobSpawn(EntityType type, double x, double y, double z, float yaw, int count) {

    public static MobSpawn at(Location loc, EntityType type, int count) {
        return new MobSpawn(type, loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), count);
    }

    public Location toLocation(World w) {
        return new Location(w, x, y, z, yaw, 0f);
    }

    String serialize() {
        return type.name() + ";" + x + ";" + y + ";" + z + ";" + yaw + ";" + count;
    }

    static MobSpawn deserialize(String s) {
        String[] p = s.split(";");
        return new MobSpawn(EntityType.valueOf(p[0]),
                Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                Float.parseFloat(p[4]), Integer.parseInt(p[5]));
    }
}
