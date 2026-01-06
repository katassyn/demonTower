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

    @EventHandler(priority = EventPriority.NORMAL)
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        Entity entity = event.getEntity();
        UUID mobId = entity.getUniqueId();

        DemonTowerSession session = plugin.getSessionManager().getCurrentSession();
        if (session != null && session.getAliveMobs().contains(mobId)) {
            session.onMobKilled(mobId);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();

        // Check if it's a MythicMob we're tracking
        if (!plugin.getMythicMobsIntegration().isEnabled()) return;
        if (!plugin.getMythicMobsIntegration().isMythicMob(entity)) return;

        UUID mobId = entity.getUniqueId();

        DemonTowerSession session = plugin.getSessionManager().getCurrentSession();
        if (session != null && session.getAliveMobs().contains(mobId)) {
            // MythicMobDeathEvent should handle this, but as fallback
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

        if (collectItem != null && plugin.getMythicMobsIntegration().isMythicItem(item, collectItem)) {
            int amount = item.getAmount();
            session.onItemCollected(amount);
        }
    }
}
