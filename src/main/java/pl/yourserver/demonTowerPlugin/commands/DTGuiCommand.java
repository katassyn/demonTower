package pl.yourserver.demonTowerPlugin.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pl.yourserver.demonTowerPlugin.DemonTowerPlugin;
import pl.yourserver.demonTowerPlugin.game.DemonTowerSession;
import pl.yourserver.demonTowerPlugin.utils.MessageUtils;

public class DTGuiCommand implements CommandExecutor {

    private final DemonTowerPlugin plugin;

    public DTGuiCommand(DemonTowerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        // Check permission
        if (!player.hasPermission("demontower.gui")) {
            MessageUtils.sendMessage(player, "&cYou don't have permission to use this command!");
            return true;
        }

        if (!plugin.getSessionManager().isPlayerInSession(player)) {
            MessageUtils.sendMessage(player, plugin.getConfigManager().getMessage("not_in_dt"));
            return true;
        }

        DemonTowerSession session = plugin.getSessionManager().getCurrentSession();
        if (session == null) return true;

        if (!session.canUseMechanics()) {
            MessageUtils.sendMessage(player, "&cFloor mechanics are available only after defeating the boss!");
            return true;
        }

        plugin.getGuiManager().openFloorMechanicGui(player);
        return true;
    }
}
