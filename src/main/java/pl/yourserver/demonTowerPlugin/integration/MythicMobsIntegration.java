package pl.yourserver.demonTowerPlugin.integration;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import io.lumine.mythic.api.mobs.MythicMob;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pl.yourserver.demonTowerPlugin.DemonTowerPlugin;

import java.util.*;

public class MythicMobsIntegration {

    private final DemonTowerPlugin plugin;
    private MythicBukkit mythicMobs;
    private boolean enabled = false;

    public MythicMobsIntegration(DemonTowerPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean setup() {
        if (plugin.getServer().getPluginManager().getPlugin("MythicMobs") == null) {
            plugin.getLogger().warning("MythicMobs not found! Mob spawning will not work.");
            return false;
        }

        try {
            mythicMobs = MythicBukkit.inst();
            enabled = mythicMobs != null;
            if (enabled) {
                plugin.getLogger().info("MythicMobs integration enabled!");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to hook into MythicMobs: " + e.getMessage());
            return false;
        }

        return enabled;
    }

    public UUID spawnMob(String mobType, String warpName) {
        if (!enabled) return null;

        Location location = plugin.getEssentialsIntegration().getWarpLocation(warpName);
        if (location == null) {
            plugin.getLogger().warning("Could not find warp location: " + warpName);
            return null;
        }

        return spawnMobAtLocation(mobType, location);
    }

    public UUID spawnMobAtLocation(String mobType, Location location) {
        // FIX: Ustawiono na false, aby uniknąć podwójnego losowania pozycji
        return spawnMobAtLocation(mobType, location, false);
    }

    public UUID spawnMobAtExactLocation(String mobType, Location location) {
        return spawnMobAtLocation(mobType, location, false);
    }

    private UUID spawnMobAtLocation(String mobType, Location location, boolean withOffset) {
        if (!enabled) return null;

        try {
            Optional<MythicMob> mythicMob = mythicMobs.getMobManager().getMythicMob(mobType);
            if (mythicMob.isEmpty()) {
                plugin.getLogger().warning("MythicMob not found: " + mobType);
                return null;
            }

            Location spawnLoc = location.clone();
            if (withOffset) {
                double offsetX = (Math.random() - 0.5) * 10;
                double offsetZ = (Math.random() - 0.5) * 10;
                spawnLoc.add(offsetX, 0, offsetZ);
            }

            ActiveMob activeMob = mythicMobs.getMobManager().spawnMob(mobType, spawnLoc);
            if (activeMob != null && activeMob.getEntity() != null) {
                return activeMob.getEntity().getUniqueId();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn MythicMob " + mobType + ": " + e.getMessage());
        }

        return null;
    }

    /**
     * NOWA METODA: Sprawdza, czy mob faktycznie istnieje w świecie gry.
     * Służy do usuwania "duchów" (ghost mobs) z licznika sesji.
     */
    public boolean isMobValid(UUID mobId) {
        if (!enabled) return false;

        Optional<ActiveMob> activeMobOpt = mythicMobs.getMobManager().getActiveMob(mobId);

        // Jeśli MythicMobs nie ma tego moba w rejestrze, to znaczy, że zniknął
        if (activeMobOpt.isEmpty()) {
            return false;
        }

        ActiveMob activeMob = activeMobOpt.get();
        Entity entity = activeMob.getEntity().getBukkitEntity();

        // Jeśli entity bukkita nie istnieje lub jest martwe
        if (entity == null || entity.isDead() || !entity.isValid()) {
            return false;
        }

        // Jeśli chunk jest niezaładowany, uznajemy moba za "poprawnego" (istnieje, ale daleko)
        if (!entity.getLocation().getChunk().isLoaded()) {
            return true;
        }

        return true;
    }

    public void killMob(UUID mobId) {
        if (!enabled) return;

        try {
            Optional<ActiveMob> activeMob = mythicMobs.getMobManager().getActiveMob(mobId);
            activeMob.ifPresent(mob -> {
                mob.getEntity().remove();
                mythicMobs.getMobManager().unregisterActiveMob(mob);
            });
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to kill mob: " + e.getMessage());
        }
    }

    public void killMobs(Collection<UUID> mobIds) {
        for (UUID mobId : mobIds) {
            killMob(mobId);
        }
    }

    public boolean isMythicMob(Entity entity) {
        if (!enabled) return false;
        return mythicMobs.getMobManager().isActiveMob(entity.getUniqueId());
    }

    public Optional<ActiveMob> getActiveMob(Entity entity) {
        if (!enabled) return Optional.empty();
        return mythicMobs.getMobManager().getActiveMob(entity.getUniqueId());
    }

    public String getMobType(Entity entity) {
        if (!enabled) return null;
        Optional<ActiveMob> activeMob = getActiveMob(entity);
        return activeMob.map(mob -> mob.getMobType()).orElse(null);
    }

    public String getMobDisplayName(String mobType) {
        if (!enabled) return null;

        try {
            Optional<MythicMob> mythicMob = mythicMobs.getMobManager().getMythicMob(mobType);
            if (mythicMob.isPresent()) {
                // Obsługa starszych i nowszych wersji API MythicMobs
                return mythicMob.get().getDisplayName().get();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get display name for mob: " + mobType);
        }
        return null;
    }

    public boolean isEliteMob(String mobType) {
        String displayName = getMobDisplayName(mobType);
        if (displayName == null) return false;
        return displayName.startsWith("&e&l") || displayName.startsWith("§e§l");
    }

    public boolean isMiniBoss(String mobType) {
        String displayName = getMobDisplayName(mobType);
        if (displayName == null) return false;
        return displayName.startsWith("&5&l") || displayName.startsWith("§5§l");
    }

    public boolean isBossMob(String mobType) {
        String displayName = getMobDisplayName(mobType);
        if (displayName == null) return false;
        return displayName.contains("&4<&skull>") || (displayName.contains("§4") && displayName.contains("skull"));
    }

    public boolean isNormalMob(String mobType) {
        return !isEliteMob(mobType) && !isMiniBoss(mobType) && !isBossMob(mobType);
    }

    public boolean hasItem(Player player, String itemId) {
        if (!enabled) return false;

        try {
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && isMythicItem(item, itemId)) {
                    return true;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to check for MythicMobs item: " + e.getMessage());
        }
        return false;
    }

    public boolean isMythicItem(ItemStack item, String itemId) {
        if (!enabled || item == null) return false;

        try {
            String mythicItemId = mythicMobs.getItemManager().getMythicTypeFromItem(item);
            return itemId.equalsIgnoreCase(mythicItemId);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean removeItem(Player player, String itemId, int amount) {
        if (!enabled) return false;

        try {
            int remaining = amount;
            ItemStack[] contents = player.getInventory().getContents();

            for (int i = 0; i < contents.length && remaining > 0; i++) {
                ItemStack item = contents[i];
                if (item != null && isMythicItem(item, itemId)) {
                    int toRemove = Math.min(remaining, item.getAmount());
                    if (item.getAmount() <= toRemove) {
                        player.getInventory().setItem(i, null);
                    } else {
                        item.setAmount(item.getAmount() - toRemove);
                    }
                    remaining -= toRemove;
                }
            }

            player.updateInventory();
            return remaining == 0;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to remove MythicMobs item: " + e.getMessage());
            return false;
        }
    }

    public int countItem(Player player, String itemId) {
        if (!enabled) return 0;

        int count = 0;
        try {
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && isMythicItem(item, itemId)) {
                    count += item.getAmount();
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to count MythicMobs items: " + e.getMessage());
        }
        return count;
    }

    public ItemStack getMythicItem(String itemId) {
        if (!enabled) return null;

        try {
            return mythicMobs.getItemManager().getItemStack(itemId);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get MythicMobs item: " + e.getMessage());
            return null;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}