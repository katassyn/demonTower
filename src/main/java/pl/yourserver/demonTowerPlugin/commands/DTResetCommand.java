package pl.yourserver.demonTowerPlugin.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pl.yourserver.demonTowerPlugin.DemonTowerPlugin;
import pl.yourserver.demonTowerPlugin.utils.MessageUtils;

public class DTResetCommand implements CommandExecutor {

    private final DemonTowerPlugin plugin;

    public DTResetCommand(DemonTowerPlugin plugin) {
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

        if (!player.hasPermission("demontower.admin")) {
            MessageUtils.sendMessage(player, "&cYou don't have permission!");
            return true;
        }

        plugin.getSessionManager().resetSession();
        MessageUtils.sendMessage(player, "&aDemon Tower session has been reset!");
        return true;
    }
}
