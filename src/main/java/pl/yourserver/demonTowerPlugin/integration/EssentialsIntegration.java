package pl.yourserver.demonTowerPlugin.integration;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import pl.yourserver.demonTowerPlugin.DemonTowerPlugin;

public class EssentialsIntegration {

    private final DemonTowerPlugin plugin;
    private Essentials essentials;
    private boolean enabled = false;

    public EssentialsIntegration(DemonTowerPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean setup() {
        if (plugin.getServer().getPluginManager().getPlugin("Essentials") == null) {
            plugin.getLogger().warning("Essentials not found! Warp teleportation will not work.");
            return false;
        }

        try {
            essentials = (Essentials) plugin.getServer().getPluginManager().getPlugin("Essentials");
            enabled = essentials != null;
            if (enabled) {
                plugin.getLogger().info("Essentials integration enabled!");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to hook into Essentials: " + e.getMessage());
            return false;
        }

        return enabled;
    }

    public boolean warpPlayer(Player player, String warpName) {
        if (!enabled) {
            // Fallback: execute warp command via console
            return plugin.getServer().dispatchCommand(
                plugin.getServer().getConsoleSender(),
                "warp " + warpName + " " + player.getName()
            );
        }

        try {
            Location warpLocation = getWarpLocation(warpName);
            if (warpLocation != null) {
                player.teleport(warpLocation);
                return true;
            } else {
                plugin.getLogger().warning("Warp not found: " + warpName);
                return false;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to warp player to " + warpName + ": " + e.getMessage());
            // Fallback to command
            return plugin.getServer().dispatchCommand(
                plugin.getServer().getConsoleSender(),
                "warp " + warpName + " " + player.getName()
            );
        }
    }

    public Location getWarpLocation(String warpName) {
        if (!enabled) return null;

        try {
            return essentials.getWarps().getWarp(warpName);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get warp location for " + warpName + ": " + e.getMessage());
            return null;
        }
    }

    public boolean warpExists(String warpName) {
        if (!enabled) return false;

        try {
            return essentials.getWarps().getWarp(warpName) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Teleport player to spawn using EssentialsX spawn command (not warp)
     * @param player the player to teleport
     * @return true if successful
     */
    public boolean teleportToSpawn(Player player) {
        // Use command-based approach for spawn since it's more reliable
        try {
            // Execute the spawn command as console
            return plugin.getServer().dispatchCommand(
                plugin.getServer().getConsoleSender(),
                "spawn " + player.getName()
            );
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to teleport player to spawn: " + e.getMessage());
            // Try to get world spawn as last resort
            Location worldSpawn = player.getWorld().getSpawnLocation();
            player.teleport(worldSpawn);
            return true;
        }
    }

    public int getPlayerLevel(Player player) {
        // Use vanilla XP level
        return player.getLevel();
    }

    public boolean isEnabled() {
        return enabled;
    }
}
