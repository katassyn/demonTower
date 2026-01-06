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

public class LobbyGui {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final DemonTowerPlugin plugin;
    private final Player player;
    private Inventory inventory;

    public static final int SLOT_JOIN = 11;
    public static final int SLOT_STATUS = 13;
    public static final int SLOT_INFO = 15;

    public LobbyGui(DemonTowerPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        inventory = Bukkit.createInventory(null, 27, LEGACY.deserialize("&4&lDemon Tower &8- Lobby"));

        // Fill background
        ItemStack background = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, background);
        }

        DemonTowerSession session = plugin.getSessionManager().getCurrentSession();
        boolean inSession = session != null && session.hasPlayer(player);

        // Join button
        if (!inSession) {
            inventory.setItem(SLOT_JOIN, createItem(Material.LIME_CONCRETE, "&a&lJoin Demon Tower",
                "&7Click to join Demon Tower",
                "",
                "&eRequirements:",
                "&7- Level: &c60+",
                "&7- Keys for higher floors",
                "",
                "&aClick to join!"));
        } else {
            int playerCount = session.getPlayerCount();
            inventory.setItem(SLOT_JOIN, createItem(Material.EMERALD_BLOCK, "&a&lAlready in Session",
                "&7You are in Demon Tower",
                "",
                "&ePlayers in session: &a" + playerCount,
                "&eFloor: &a" + session.getCurrentFloor(),
                "&eStage: &a" + session.getCurrentStage()));
        }

        // Status display
        if (inSession) {
            int playerCount = session.getPlayerCount();
            int lobbyTime = plugin.getConfigManager().getLobbyTime();
            inventory.setItem(SLOT_STATUS, createItem(Material.CLOCK, "&e&lAuto-Start Timer",
                "&7The wave will start automatically!",
                "",
                "&ePlayers in session: &a" + playerCount,
                "&eLobby time: &c" + (lobbyTime / 60) + " minutes",
                "",
                "&7Fight other players for spots",
                "&7while waiting!"));
        } else {
            boolean blocked = plugin.getSessionManager().isBlocked();
            if (blocked) {
                inventory.setItem(SLOT_STATUS, createItem(Material.BARRIER, "&c&lBlocked",
                    "&7Demon Tower is currently blocked!",
                    "",
                    "&7Wait until a group reaches",
                    "&7Floor 3 to start a new session."));
            } else {
                inventory.setItem(SLOT_STATUS, createItem(Material.GRAY_CONCRETE, "&7&lNo Session",
                    "&7Join Demon Tower first",
                    "",
                    "&eWave starts automatically",
                    "&eafter lobby time expires!"));
            }
        }

        // Info button
        inventory.setItem(SLOT_INFO, createInfoItem());

        player.openInventory(inventory);
    }

    private ItemStack createInfoItem() {
        List<String> lore = new ArrayList<>();
        lore.add("&8━━━━━━━━━━━━━━━━━━━━");
        lore.add("&7Demon Tower is a group challenge");
        lore.add("&7where you must defeat waves of enemies!");
        lore.add("");
        lore.add("&e&lFloors:");

        for (int i = 1; i <= plugin.getConfigManager().getFloorCount(); i++) {
            FloorConfig floor = plugin.getConfigManager().getFloor(i);
            if (floor != null) {
                String keyInfo = floor.requiresKey() ? " + Key " + getRomanNumeral(i) : "";
                lore.add("&7Floor " + i + ": &cLvl " + floor.getRequiredLevel() + keyInfo);
            }
        }

        lore.add("");
        lore.add("&c&lMechanics:");
        lore.add("&7- Each stage has a time limit");
        lore.add("&7- Defeat 95% of mobs to proceed");
        lore.add("&7- Bosses must be killed!");
        lore.add("&7- If you fail - death!");
        lore.add("&8━━━━━━━━━━━━━━━━━━━━");
        lore.add("");
        lore.add("&d&lTIP: &7Right-click or Middle-click");
        lore.add("&7anywhere to view floor drops!");

        return createItem(Material.BOOK, "&b&lInformation", lore.toArray(new String[0]));
    }

    private String getRomanNumeral(int number) {
        switch (number) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            case 4: return "IV";
            default: return String.valueOf(number);
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
