package pl.yourserver.demonTowerPlugin.commands;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pl.yourserver.demonTowerPlugin.DemonTowerPlugin;

public class DTDropsCommand implements CommandExecutor {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final DemonTowerPlugin plugin;

    public DTDropsCommand(DemonTowerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(LEGACY.deserialize("&cThis command can only be used by players!"));
            return true;
        }

        if (!player.hasPermission("demontower.admin")) {
            player.sendMessage(LEGACY.deserialize("&cYou don't have permission to use this command!"));
            return true;
        }

        if (!plugin.getDatabaseManager().isEnabled()) {
            player.sendMessage(LEGACY.deserialize("&cDatabase is not enabled! Check your config.yml"));
            return true;
        }

        // Open admin floor selection GUI
        plugin.getGuiManager().openAdminFloorSelectionGui(player);
        return true;
    }
}
