package pl.yourserver.demonTowerPlugin.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pl.yourserver.demonTowerPlugin.DemonTowerPlugin;
import pl.yourserver.demonTowerPlugin.game.DemonTowerSession;
import pl.yourserver.demonTowerPlugin.utils.MessageUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DemonTowerCommand implements CommandExecutor, TabCompleter {

    private final DemonTowerPlugin plugin;
    private final List<String> subCommands = Arrays.asList("join", "leave", "start", "info", "reset", "reload");

    public DemonTowerCommand(DemonTowerPlugin plugin) {
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

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "join":
                handleJoin(player);
                break;

            case "leave":
                handleLeave(player);
                break;

            case "start":
                handleStart(player);
                break;

            case "info":
                handleInfo(player);
                break;

            case "reset":
                handleReset(player);
                break;

            case "reload":
                handleReload(player);
                break;

            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    private void handleJoin(Player player) {
        if (plugin.getSessionManager().isPlayerInSession(player)) {
            MessageUtils.sendMessage(player, plugin.getConfigManager().getMessage("already_in_dt"));
            return;
        }

        if (plugin.getSessionManager().hasActiveSession()) {
            MessageUtils.sendMessage(player, plugin.getConfigManager().getMessage("session_in_progress"));
            return;
        }

        if (plugin.getSessionManager().joinSession(player)) {
            // Successfully joined - message sent by session
        }
    }

    private void handleLeave(Player player) {
        if (!plugin.getSessionManager().isPlayerInSession(player)) {
            MessageUtils.sendMessage(player, plugin.getConfigManager().getMessage("not_in_dt"));
            return;
        }

        if (plugin.getSessionManager().leaveSession(player)) {
            MessageUtils.sendMessage(player, "&cOpusciles Demon Tower!");
        }
    }

    private void handleStart(Player player) {
        if (!plugin.getSessionManager().isPlayerInSession(player)) {
            MessageUtils.sendMessage(player, plugin.getConfigManager().getMessage("not_in_dt"));
            return;
        }

        DemonTowerSession session = plugin.getSessionManager().getCurrentSession();
        if (session == null) return;

        if (session.isActive()) {
            MessageUtils.sendMessage(player, "&cSesja jest juz aktywna!");
            return;
        }

        session.startWave();
    }

    private void handleInfo(Player player) {
        plugin.getGuiManager().openInfoGui(player);
    }

    private void handleReset(Player player) {
        if (!player.hasPermission("demontower.admin")) {
            MessageUtils.sendMessage(player, "&cNie masz uprawnien!");
            return;
        }

        plugin.getSessionManager().resetSession();
        MessageUtils.sendMessage(player, "&aSesja Demon Tower zostala zresetowana!");
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("demontower.admin")) {
            MessageUtils.sendMessage(player, "&cNie masz uprawnien!");
            return;
        }

        plugin.getConfigManager().loadConfig();
        MessageUtils.sendMessage(player, "&aKonfiguracja zostala przeladowana!");
    }

    private void sendHelp(Player player) {
        MessageUtils.sendMessage(player, "&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtils.sendMessage(player, "&4&lDemon Tower &8- &7Pomoc");
        MessageUtils.sendMessage(player, "&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtils.sendMessage(player, "&e/dt join &8- &7Dolacz do Demon Tower");
        MessageUtils.sendMessage(player, "&e/dt leave &8- &7Opusc Demon Tower");
        MessageUtils.sendMessage(player, "&e/dt start &8- &7Rozpocznij fale (lider)");
        MessageUtils.sendMessage(player, "&e/dt info &8- &7Informacje o Demon Tower");
        MessageUtils.sendMessage(player, "&e/dt_gui &8- &7Otworz mechanike pietra");

        if (player.hasPermission("demontower.admin")) {
            MessageUtils.sendMessage(player, "&c/dt reset &8- &7Zresetuj sesje (admin)");
            MessageUtils.sendMessage(player, "&c/dt reload &8- &7Przeladuj konfiguracje (admin)");
        }

        MessageUtils.sendMessage(player, "&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return subCommands.stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
