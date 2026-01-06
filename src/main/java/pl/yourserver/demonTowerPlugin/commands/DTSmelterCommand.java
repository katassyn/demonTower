package pl.yourserver.demonTowerPlugin.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pl.yourserver.demonTowerPlugin.DemonTowerPlugin;
import pl.yourserver.demonTowerPlugin.utils.MessageUtils;

/**
 * Opens Demonic Smelter GUI (Floor 2 mechanic)
 * Can be used for testing outside of DT session
 */
public class DTSmelterCommand implements CommandExecutor {

    private final DemonTowerPlugin plugin;

    public DTSmelterCommand(DemonTowerPlugin plugin) {
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

        if (!player.hasPermission("demontower.mechanic.smelter")) {
            MessageUtils.sendMessage(player, "&cYou don't have permission to use this!");
            return true;
        }

        plugin.getGuiManager().openDemonicSmelterGui(player);
        return true;
    }
}
