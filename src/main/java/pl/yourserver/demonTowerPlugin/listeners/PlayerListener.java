package pl.yourserver.demonTowerPlugin.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pl.yourserver.demonTowerPlugin.DemonTowerPlugin;
import pl.yourserver.demonTowerPlugin.game.DemonTowerSession;

public class PlayerListener implements Listener {

    private final DemonTowerPlugin plugin;

    public PlayerListener(DemonTowerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // When player dies, remove from session
        // They will respawn at spawn and are no longer in DT
        if (plugin.getSessionManager().isPlayerInSession(player)) {
            DemonTowerSession session = plugin.getSessionManager().getCurrentSession();
            if (session != null) {
                session.removePlayer(player.getUniqueId());
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Close any open GUI
        plugin.getGuiManager().closeGui(player);

        // Remove from session if in one
        if (plugin.getSessionManager().isPlayerInSession(player)) {
            DemonTowerSession session = plugin.getSessionManager().getCurrentSession();
            if (session != null) {
                session.removePlayer(player.getUniqueId());
            }
        }
    }
}
