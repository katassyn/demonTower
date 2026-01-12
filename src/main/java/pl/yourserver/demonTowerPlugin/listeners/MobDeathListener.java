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

            // Obsługa dropu PRZED usunięciem moba z listy (bo onMobKilled go usuwa)
            handleVirtualCollect(session);

            session.onMobKilled(mobId);
        } else {
            debug("  -> Mob is NOT tracked");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();

        if (!plugin.getMythicMobsIntegration().isEnabled()) return;
        if (!plugin.getMythicMobsIntegration().isMythicMob(entity)) return;

        UUID mobId = entity.getUniqueId();

        debug("EntityDeath (MythicMob fallback): entity=" + entity.getType() + ", mobId=" + mobId);

        DemonTowerSession session = plugin.getSessionManager().getCurrentSession();
        if (session != null && session.getAliveMobs().contains(mobId)) {
            debug("  -> Mob is tracked, calling handleVirtualCollect then onMobKilled (fallback)");

            handleVirtualCollect(session);

            session.onMobKilled(mobId);
        }
    }

    /**
     * Wirtualny system dropu z PROGRESYWNĄ SZANSĄ.
     * Zamiast "sztywnego 100% na końcu", szansa rośnie liniowo z każdym zabitym mobem bez dropu.
     */
    private void handleVirtualCollect(DemonTowerSession session) {
        if (session.getState() != SessionState.COLLECTING) return;

        FloorConfig floor = plugin.getConfigManager().getFloor(session.getCurrentFloor());
        if (floor == null) return;

        StageConfig stage = floor.getStage(session.getCurrentStage());
        if (stage == null || stage.getType() != StageType.COLLECT) return;

        int required = stage.getCollectAmount();
        int collected = session.getCollectedItems();
        int totalMobs = session.getTotalMobCount();

        if (collected >= required) return; // Już zebrano wystarczająco

        // Obliczamy ile mobów przypada na 1 item (np. 200 mobów / 20 itemów = 10 mobów na item)
        double avgMobsPerItem = (double) totalMobs / (double) required;
        if (avgMobsPerItem < 1.0) avgMobsPerItem = 1.0;

        // Szansa bazowa za jedno zabicie
        double chanceIncrement = 1.0 / avgMobsPerItem;

        // Progresywna szansa: (liczba zabitych od ostatniego dropu + 1) * przyrost
        int killsSinceLast = session.getKillsSinceLastDrop();
        double currentChance = (killsSinceLast + 1) * chanceIncrement;

        debug("Progressive Drop Calc: need=" + (required - collected) +
                ", killsSinceLast=" + killsSinceLast +
                ", avgMobs/Item=" + String.format("%.1f", avgMobsPerItem) +
                ", currentChance=" + String.format("%.2f%%", currentChance * 100));

        if (Math.random() < currentChance) {
            debug("  -> VIRTUAL DROP SUCCESS!");
            session.onItemCollected(1);
        } else {
            debug("  -> Virtual drop failed (RNG). Incrementing luck counter.");
            session.incrementKillsSinceLastDrop();
        }
    }
}