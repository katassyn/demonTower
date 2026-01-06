package pl.yourserver.demonTowerPlugin.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pl.yourserver.demonTowerPlugin.DemonTowerPlugin;
import pl.yourserver.demonTowerPlugin.utils.MessageUtils;

public class DTReloadCommand implements CommandExecutor {

    private final DemonTowerPlugin plugin;

    public DTReloadCommand(DemonTowerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("demontower.admin")) {
            if (sender instanceof Player) {
                MessageUtils.sendMessage((Player) sender, "&cYou don't have permission!");
            } else {
                sender.sendMessage("You don't have permission!");
            }
            return true;
        }

        plugin.getConfigManager().loadConfig();

        if (sender instanceof Player) {
            MessageUtils.sendMessage((Player) sender, "&aConfiguration has been reloaded!");
        } else {
            sender.sendMessage("Configuration has been reloaded!");
        }
        return true;
    }
}
