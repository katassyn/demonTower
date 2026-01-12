package pl.yourserver.demonTowerPlugin.listeners;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import pl.yourserver.demonTowerPlugin.DemonTowerPlugin;
import pl.yourserver.demonTowerPlugin.config.FloorConfig;
import pl.yourserver.demonTowerPlugin.config.StageConfig;
import pl.yourserver.demonTowerPlugin.game.DemonTowerSession;
import pl.yourserver.demonTowerPlugin.game.SessionState;
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
            debug("  -> Mob is tracked, calling handleVirtualCollect then onMobKilled");

            // Obsługa wirtualnego dropu (zaliczenie przedmiotu bez fizycznego spawnowania)
            handleVirtualCollect(session);

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
            debug("  -> Mob is tracked, calling handleVirtualCollect then onMobKilled (fallback)");

            // Obsługa wirtualnego dropu
            handleVirtualCollect(session);

            session.onMobKilled(mobId);
        }
    }

    /**
     * Wirtualny system dropu:
     * Oblicza szansę na "drop" na podstawie brakujących przedmiotów i żywych mobów.
     * Gwarantuje 100% zdobycia wymaganej liczby przedmiotów.
     * Nie spawnuje fizycznego przedmiotu, tylko od razu zalicza postęp.
     */
    private void handleVirtualCollect(DemonTowerSession session) {
        if (session.getState() != SessionState.COLLECTING) return;

        FloorConfig floor = plugin.getConfigManager().getFloor(session.getCurrentFloor());
        if (floor == null) return;

        StageConfig stage = floor.getStage(session.getCurrentStage());
        if (stage == null || stage.getType() != StageType.COLLECT) return;

        int required = stage.getCollectAmount();
        int collected = session.getCollectedItems();

        // Mob, który właśnie umiera, wciąż jest na liście aliveMobs w tym momencie
        int mobsAlive = session.getAliveMobs().size();

        if (collected >= required) return; // Już zebrano wystarczająco

        // Oblicz szansę: (Ile brakuje / Ile mobów zostało)
        // Jeśli brakuje 1 przedmiotu i został 1 mob -> szansa 100%
        // Jeśli brakuje 5 przedmiotów i jest 10 mobów -> szansa 50%
        double chance = (double) (required - collected) / (double) mobsAlive;

        debug("Virtual Drop Calc: need=" + (required - collected) + ", mobs=" + mobsAlive + ", chance=" + String.format("%.2f", chance));

        if (Math.random() < chance) {
            // Sukces - zaliczamy przedmiot wirtualnie
            debug("  -> VIRTUAL DROP SUCCESS! Adding +1 to collected items.");
            session.onItemCollected(1);
        } else {
            debug("  -> Virtual drop failed (RNG).");
        }
    }
}
