package pl.yourserver.demonTowerPlugin.config;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class SpawnPoint {
    private final String world;
    private final double x;
    private final double y;
    private final double z;

    public SpawnPoint(String world, double x, double y, double z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public String getWorld() {
        return world;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public Location toLocation() {
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, x, y, z);
    }

    /**
     * Get a random location within radius of this spawn point
     */
    public Location getRandomLocation(double radius) {
        World w = Bukkit.getWorld(world);
        if (w == null) return null;

        double offsetX = (Math.random() - 0.5) * 2 * radius;
        double offsetZ = (Math.random() - 0.5) * 2 * radius;

        return new Location(w, x + offsetX, y, z + offsetZ);
    }
}
