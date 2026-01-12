package pl.yourserver.demonTowerPlugin.listeners;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import pl.yourserver.demonTowerPlugin.DemonTowerPlugin;
import pl.yourserver.demonTowerPlugin.config.FloorConfig;
import pl.yourserver.demonTowerPlugin.config.StageConfig;
import pl.yourserver.demonTowerPlugin.game.DemonTowerSession;
import pl.yourserver.demonTowerPlugin.game.StageType;

import java.util.UUID;

public class MobDeathListener implements Listener {

    private final DemonTowerPlugin plugin;

    public MobDeathListener(DemonTowerPlugin plugin) {
        this.plugin = plugin;
    }

    private void debug(String message) {
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[MobDeathListener DEBUG] " + message);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        Entity entity = event.getEntity();
        UUID mobId = entity.getUniqueId();
        String mobType = event.getMobType().getInternalName();

        debug("MythicMobDeath: mobType=" + mobType + ", mobId=" + mobId);

        DemonTowerSession session = plugin.getSessionManager().getCurrentSession();
        if (session != null && session.getAliveMobs().contains(mobId)) {
            debug("  -> Mob is tracked, calling onMobKilled");
            session.onMobKilled(mobId);
        } else {
            debug("  -> Mob is NOT tracked (session=" + (session != null) + ", inAliveMobs=" + (session != null && session.getAliveMobs().contains(mobId)) + ")");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();

        // Check if it's a MythicMob we're tracking
        if (!plugin.getMythicMobsIntegration().isEnabled()) return;
        if (!plugin.getMythicMobsIntegration().isMythicMob(entity)) return;

        UUID mobId = entity.getUniqueId();

        debug("EntityDeath (MythicMob fallback): entity=" + entity.getType() + ", mobId=" + mobId);

        DemonTowerSession session = plugin.getSessionManager().getCurrentSession();
        if (session != null && session.getAliveMobs().contains(mobId)) {
            // MythicMobDeathEvent should handle this, but as fallback
            debug("  -> Mob is tracked, calling onMobKilled (fallback)");
            session.onMobKilled(mobId);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();

        // Check if player is in a collect stage
        if (!plugin.getSessionManager().isPlayerInSession(player)) return;

        DemonTowerSession session = plugin.getSessionManager().getCurrentSession();
        if (session == null) return;

        FloorConfig floor = plugin.getConfigManager().getFloor(session.getCurrentFloor());
        if (floor == null) return;

        StageConfig stage = floor.getStage(session.getCurrentStage());
        if (stage == null || stage.getType() != StageType.COLLECT) return;

        // Check if picked up item is the collect item
        ItemStack item = event.getItem().getItemStack();
        String collectItem = stage.getCollectItem();

        debug("ItemPickup: player=" + player.getName() + ", itemType=" + item.getType() + ", amount=" + item.getAmount());
        debug("  collectItem configured=" + collectItem);

        if (collectItem != null) {
            boolean isMythicItem = plugin.getMythicMobsIntegration().isMythicItem(item, collectItem);
            debug("  isMythicItem=" + isMythicItem);

            if (isMythicItem) {
                int amount = item.getAmount();
                debug("  -> Collecting " + amount + " items");
                session.onItemCollected(amount);
            }
        }
    }
}
