package pl.yourserver.demonTowerPlugin.integration;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import pl.yourserver.demonTowerPlugin.DemonTowerPlugin;

import java.lang.reflect.Method;

/**
 * Integration with IngredientPouchPlugin for crafting materials.
 * Uses reflection to avoid compile-time dependency.
 */
public class PouchIntegration {

    private final DemonTowerPlugin plugin;
    private Object pouchAPI = null;
    private Method getItemQuantityMethod = null;
    private Method updateItemQuantityMethod = null;
    private boolean enabled = false;

    public PouchIntegration(DemonTowerPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean setup() {
        Plugin ingredientPouch = plugin.getServer().getPluginManager().getPlugin("IngredientPouchPlugin");
        if (ingredientPouch == null) {
            plugin.getLogger().warning("IngredientPouchPlugin not found! Crafting materials will not be checked from pouch.");
            return false;
        }

        try {
            // Use reflection to get the API
            Method getAPIMethod = ingredientPouch.getClass().getMethod("getAPI");
            pouchAPI = getAPIMethod.invoke(ingredientPouch);

            if (pouchAPI != null) {
                // Get the methods we need
                Class<?> apiClass = pouchAPI.getClass();
                getItemQuantityMethod = apiClass.getMethod("getItemQuantity", String.class, String.class);
                updateItemQuantityMethod = apiClass.getMethod("updateItemQuantity", String.class, String.class, int.class);
                enabled = true;
                plugin.getLogger().info("IngredientPouchPlugin integration enabled!");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to hook into IngredientPouchPlugin: " + e.getMessage());
            return false;
        }

        return enabled;
    }

    /**
     * Check if player has enough of a specific material in their pouch
     * @param player The player
     * @param itemId The MythicMobs item ID
     * @param amount The required amount
     * @return true if player has enough (always true if plugin not available)
     */
    public boolean hasItem(Player player, String itemId, int amount) {
        if (!enabled || pouchAPI == null) return true; // Allow if plugin not available

        int quantity = getItemAmount(player, itemId);
        return quantity >= amount;
    }

    /**
     * Get the amount of a specific material in player's pouch
     * @param player The player
     * @param itemId The MythicMobs item ID
     * @return The quantity (0 if plugin not available)
     */
    public int getItemAmount(Player player, String itemId) {
        if (!enabled || pouchAPI == null || getItemQuantityMethod == null) return 0;

        try {
            Object result = getItemQuantityMethod.invoke(pouchAPI, player.getUniqueId().toString(), itemId);
            return (Integer) result;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get item quantity from pouch: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Remove a specific amount of material from player's pouch
     * @param player The player
     * @param itemId The MythicMobs item ID
     * @param amount The amount to remove
     * @return true if successful (always true if plugin not available)
     */
    public boolean removeItem(Player player, String itemId, int amount) {
        if (!enabled || pouchAPI == null || updateItemQuantityMethod == null) return true; // Allow if plugin not available

        try {
            Object result = updateItemQuantityMethod.invoke(pouchAPI, player.getUniqueId().toString(), itemId, -amount);
            return (Boolean) result;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to remove item from pouch: " + e.getMessage());
            return false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
