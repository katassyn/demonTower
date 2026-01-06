package pl.yourserver.demonTowerPlugin.game;

import org.bukkit.entity.Player;
import pl.yourserver.demonTowerPlugin.DemonTowerPlugin;
import pl.yourserver.demonTowerPlugin.utils.MessageUtils;

import java.util.UUID;

public class SessionManager {

    private final DemonTowerPlugin plugin;
    private DemonTowerSession currentSession;
    private boolean blocked = false; // Blocking new sessions until floor 3 reached

    public SessionManager(DemonTowerPlugin plugin) {
        this.plugin = plugin;
    }

    public DemonTowerSession getOrCreateSession() {
        if (currentSession == null) {
            currentSession = new DemonTowerSession(plugin);
        }
        return currentSession;
    }

    public DemonTowerSession getCurrentSession() {
        return currentSession;
    }

    public boolean hasActiveSession() {
        return currentSession != null && currentSession.isActive();
    }

    public boolean hasSession() {
        return currentSession != null;
    }

    public void endSession() {
        currentSession = null;
        // When session ends, lift the blocking so new sessions can start
        blocked = false;
    }

    public void resetSession() {
        if (currentSession != null) {
            currentSession.reset();
        }
        currentSession = null;
        blocked = false;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public void checkUnblock() {
        if (currentSession != null) {
            int unblockFloor = plugin.getConfigManager().getUnblockFloor();
            if (currentSession.getCurrentFloor() >= unblockFloor) {
                blocked = false;
            }
        }
    }

    public boolean isPlayerInSession(Player player) {
        return currentSession != null && currentSession.hasPlayer(player);
    }

    public boolean isPlayerInSession(UUID playerId) {
        if (currentSession == null) return false;
        return currentSession.getPlayers().contains(playerId);
    }

    public boolean canJoinSession(Player player) {
        // Check if blocked (until floor 3 reached)
        if (blocked) {
            MessageUtils.sendMessage(player, plugin.getConfigManager().getMessage("dt_blocked"));
            return false;
        }

        // Check if there's an active session that is not in lobby
        if (currentSession != null && !currentSession.canJoin()) {
            MessageUtils.sendMessage(player, plugin.getConfigManager().getMessage("joining_disabled"));
            return false;
        }

        return true;
    }

    public boolean joinSession(Player player) {
        if (!canJoinSession(player)) {
            return false;
        }

        DemonTowerSession session = getOrCreateSession();
        return session.addPlayer(player);
    }

    public boolean leaveSession(Player player) {
        if (currentSession == null || !currentSession.hasPlayer(player)) {
            return false;
        }
        currentSession.removePlayer(player.getUniqueId());
        return true;
    }

    public void onMobKilled(UUID mobId) {
        if (currentSession != null) {
            currentSession.onMobKilled(mobId);
        }
    }

    public void onItemCollected(int amount) {
        if (currentSession != null) {
            currentSession.onItemCollected(amount);
        }
    }

    public void advanceToNextFloor() {
        if (currentSession != null) {
            currentSession.advanceToNextFloor();
            // Check if we should unblock after reaching floor 3
            checkUnblock();
        }
    }
}
