package pl.yourserver.demonTowerPlugin.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pl.yourserver.demonTowerPlugin.DemonTowerPlugin;
import pl.yourserver.demonTowerPlugin.game.DemonTowerSession;
import pl.yourserver.demonTowerPlugin.utils.MessageUtils;

import java.util.UUID;

public class WhoDoDTCommand implements CommandExecutor {

    private final DemonTowerPlugin plugin;

    public WhoDoDTCommand(DemonTowerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        DemonTowerSession session = plugin.getSessionManager().getCurrentSession();

        if (session == null || session.getPlayerCount() == 0) {
            sender.sendMessage(MessageUtils.colorize("&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            sender.sendMessage(MessageUtils.colorize("&4&lDemon Tower &8- &7Status"));
            sender.sendMessage(MessageUtils.colorize("&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            sender.sendMessage(MessageUtils.colorize("&7Nobody is currently playing Demon Tower."));
            sender.sendMessage(MessageUtils.colorize("&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            return true;
        }

        sender.sendMessage(MessageUtils.colorize("&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        sender.sendMessage(MessageUtils.colorize("&4&lDemon Tower &8- &7Status"));
        sender.sendMessage(MessageUtils.colorize("&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        sender.sendMessage(MessageUtils.colorize("&7Floor: &e" + session.getCurrentFloor() +
            " &8| &7Stage: &e" + session.getCurrentStage()));
        sender.sendMessage(MessageUtils.colorize("&7State: &e" + getStateDisplay(session)));

        if (session.getTimeRemaining() > 0) {
            sender.sendMessage(MessageUtils.colorize("&7Time remaining: &c" +
                MessageUtils.formatTime(session.getTimeRemaining())));
        }

        // Show mob progress
        int totalMobs = session.getTotalMobCount();
        int aliveMobs = session.getAliveMobs().size();
        int killedMobs = totalMobs - aliveMobs;
        if (totalMobs > 0) {
            int percent = (int) ((killedMobs / (double) totalMobs) * 100);
            sender.sendMessage(MessageUtils.colorize("&7Mobs killed: &a" + killedMobs + "&7/&a" + totalMobs +
                " &7(&e" + percent + "%&7)"));
        }

        sender.sendMessage(MessageUtils.colorize(""));
        sender.sendMessage(MessageUtils.colorize("&7Players (&e" + session.getPlayerCount() + "&7):"));

        for (UUID playerId : session.getPlayers()) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                String health = String.format("%.1f", player.getHealth());
                sender.sendMessage(MessageUtils.colorize("&7" + player.getName() +
                    " &8(&c" + health + "&4\u2764&8)"));
            }
        }

        sender.sendMessage(MessageUtils.colorize("&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));

        return true;
    }

    private String getStateDisplay(DemonTowerSession session) {
        switch (session.getState()) {
            case WAITING:
                return "&eLobby";
            case ACTIVE:
                return "&cWave Active";
            case BOSS:
                return "&4Boss Fight";
            case COLLECTING:
                return "&6Collecting";
            case COMPLETED:
                return "&aCompleted";
            case FAILED:
                return "&cFailed";
            default:
                return "&7Unknown";
        }
    }
}
