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

import java.util.ArrayList;
import java.util.List;

public class AdminFloorSelectionGui {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final DemonTowerPlugin plugin;
    private final Player player;
    private Inventory inventory;

    // Slots for floor buttons
    public static final int SLOT_FLOOR_1 = 10;
    public static final int SLOT_FLOOR_2 = 12;
    public static final int SLOT_FLOOR_3 = 14;
    public static final int SLOT_FLOOR_4 = 16;
    public static final int SLOT_CLOSE = 22;

    public AdminFloorSelectionGui(DemonTowerPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        inventory = Bukkit.createInventory(null, 27,
                LEGACY.deserialize("&4&lDemon Tower &8- Admin Drops"));

        // Fill background
        ItemStack background = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, background);
        }

        // Floor I
        int dropsFloor1 = plugin.getFloorDropsManager().getDropCount(1);
        inventory.setItem(SLOT_FLOOR_1, createFloorItem(1, Material.NETHERITE_SWORD, dropsFloor1));

        // Floor II
        int dropsFloor2 = plugin.getFloorDropsManager().getDropCount(2);
        inventory.setItem(SLOT_FLOOR_2, createFloorItem(2, Material.NETHERITE_CHESTPLATE, dropsFloor2));

        // Floor III
        int dropsFloor3 = plugin.getFloorDropsManager().getDropCount(3);
        inventory.setItem(SLOT_FLOOR_3, createFloorItem(3, Material.NETHERITE_HELMET, dropsFloor3));

        // Floor IV
        int dropsFloor4 = plugin.getFloorDropsManager().getDropCount(4);
        inventory.setItem(SLOT_FLOOR_4, createFloorItem(4, Material.DRAGON_HEAD, dropsFloor4));

        // Close button
        inventory.setItem(SLOT_CLOSE, createItem(Material.BARRIER, "&c&lClose",
                "&7Close this menu"));

        player.openInventory(inventory);
    }

    private ItemStack createFloorItem(int floor, Material material, int dropCount) {
        String roman = plugin.getFloorDropsManager().getRomanNumeral(floor);
        String colorCode = getFloorColor(floor);

        List<String> lore = new ArrayList<>();
        lore.add("&8━━━━━━━━━━━━━━━━━━━━");
        lore.add("");
        lore.add("&7Edit drop items for");
        lore.add("&7Floor " + roman + " of Demon Tower.");
        lore.add("");
        lore.add("&eCurrent items: &a" + dropCount);
        lore.add("");
        lore.add("&8━━━━━━━━━━━━━━━━━━━━");
        lore.add("&aClick to edit drops!");

        return createItem(material, colorCode + "&lFloor " + roman + " &7[ADMIN]", lore.toArray(new String[0]));
    }

    private String getFloorColor(int floor) {
        return switch (floor) {
            case 1 -> "&a";  // Green
            case 2 -> "&e";  // Yellow
            case 3 -> "&6";  // Orange
            case 4 -> "&c";  // Red
            default -> "&7";
        };
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
