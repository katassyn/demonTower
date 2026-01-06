package pl.yourserver.demonTowerPlugin.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.yourserver.demonTowerPlugin.DemonTowerPlugin;
import pl.yourserver.demonTowerPlugin.config.FloorConfig;
import pl.yourserver.demonTowerPlugin.game.DemonTowerSession;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class InfoGui {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final DemonTowerPlugin plugin;
    private final Player player;
    private Inventory inventory;

    public InfoGui(DemonTowerPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        inventory = Bukkit.createInventory(null, 45,
            LEGACY.deserialize("&4&lDemon Tower &8- Information"));

        // Fill background
        ItemStack background = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 45; i++) {
            inventory.setItem(i, background);
        }

        // Floor items (10, 12, 14, 16 for floors 1-4)
        int[] floorSlots = {10, 12, 14, 16};
        Material[] floorMaterials = {Material.COAL_BLOCK, Material.IRON_BLOCK, Material.GOLD_BLOCK, Material.DIAMOND_BLOCK};
        String[] floorColors = {"&7", "&f", "&6", "&b"};

        for (int i = 0; i < 4; i++) {
            int floorNum = i + 1;
            FloorConfig floor = plugin.getConfigManager().getFloor(floorNum);

            if (floor != null) {
                inventory.setItem(floorSlots[i], createFloorItem(floor, floorMaterials[i], floorColors[i]));
            }
        }

        // Current session info
        DemonTowerSession session = plugin.getSessionManager().getCurrentSession();
        if (session != null && session.getPlayerCount() > 0) {
            inventory.setItem(31, createSessionInfoItem(session));
        } else {
            inventory.setItem(31, createItem(Material.BARRIER, "&c&lNo Active Session",
                "&7Nobody is currently playing DT"));
        }

        // General info
        inventory.setItem(40, createItem(Material.BOOK, "&e&lHow to Play?",
            "&8━━━━━━━━━━━━━━━━━━━━",
            "&71. Join the lobby with &e/dt_join",
            "&72. Wait for other players (PvP enabled!)",
            "&73. Game auto-starts after 5 minutes",
            "&74. Defeat 95% of enemies in time!",
            "&75. Kill the boss to proceed",
            "&8━━━━━━━━━━━━━━━━━━━━",
            "",
            "&c&lWarning: If you fail - death!"));

        // Close button
        inventory.setItem(44, createItem(Material.BARRIER, "&c&lClose", "&7Click to close"));

        player.openInventory(inventory);
    }

    private ItemStack createFloorItem(FloorConfig floor, Material material, String color) {
        List<String> lore = new ArrayList<>();
        lore.add("&8━━━━━━━━━━━━━━━━━━━━");
        lore.add("");
        lore.add("&7Required level: &e" + floor.getRequiredLevel());

        if (floor.requiresKey()) {
            boolean hasKey = plugin.getMythicMobsIntegration().hasItem(player, floor.getRequiredKey());
            String status = hasKey ? "&a" : "&c";
            lore.add("&7Required key: " + status + floor.getRequiredKey());
        } else {
            lore.add("&7Required key: &aNone");
        }

        lore.add("");
        lore.add("&7Number of stages: &e" + floor.getStageCount());
        lore.add("");

        // Check if player meets requirements
        boolean meetsLevel = player.getLevel() >= floor.getRequiredLevel();
        boolean meetsKey = !floor.requiresKey() ||
            plugin.getMythicMobsIntegration().hasItem(player, floor.getRequiredKey());

        if (meetsLevel && meetsKey) {
            lore.add("&a&lYou can enter this floor!");
        } else {
            lore.add("&c&lYou don't meet the requirements!");
        }

        lore.add("&8━━━━━━━━━━━━━━━━━━━━");

        return createItem(material, color + "&lFloor " + floor.getFloor(), lore.toArray(new String[0]));
    }

    private ItemStack createSessionInfoItem(DemonTowerSession session) {
        List<String> lore = new ArrayList<>();
        lore.add("&8━━━━━━━━━━━━━━━━━━━━");
        lore.add("");
        lore.add("&7Floor: &e" + session.getCurrentFloor());
        lore.add("&7Stage: &e" + session.getCurrentStage());
        lore.add("&7State: &e" + getStateDisplay(session));
        lore.add("&7Players: &e" + session.getPlayerCount());
        lore.add("");
        lore.add("&7Players in session:");

        for (UUID playerId : session.getPlayers()) {
            Player p = plugin.getServer().getPlayer(playerId);
            if (p != null) {
                lore.add("&7" + p.getName());
            }
        }

        lore.add("");
        lore.add("&8━━━━━━━━━━━━━━━━━━━━");

        return createItem(Material.ENDER_EYE, "&a&lActive Session", lore.toArray(new String[0]));
    }

    private String getStateDisplay(DemonTowerSession session) {
        switch (session.getState()) {
            case WAITING: return "Lobby";
            case ACTIVE: return "Wave Active";
            case BOSS: return "Boss Fight";
            case COLLECTING: return "Collecting";
            case COMPLETED: return "Completed";
            case FAILED: return "Failed";
            default: return "Unknown";
        }
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(LEGACY.deserialize(name));

            if (lore.length > 0) {
                List<Component> loreComponents = new ArrayList<>();
                for (String line : lore) {
                    loreComponents.add(LEGACY.deserialize(line));
                }
                meta.lore(loreComponents);
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    public Inventory getInventory() {
        return inventory;
    }
}
