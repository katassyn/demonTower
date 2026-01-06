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
import java.util.Map;

public class FloorDropsPreviewGui {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final DemonTowerPlugin plugin;
    private final Player player;
    private final int floor;
    private Inventory inventory;

    // Layout: 54 slots (6 rows)
    // Rows 0-4 (slots 0-44): Drop items
    // Row 5 (slots 45-53): Navigation
    public static final int SLOT_BACK = 49;

    public FloorDropsPreviewGui(DemonTowerPlugin plugin, Player player, int floor) {
        this.plugin = plugin;
        this.player = player;
        this.floor = floor;
    }

    public void open() {
        String roman = plugin.getFloorDropsManager().getRomanNumeral(floor);
        String colorCode = getFloorColor(floor);

        inventory = Bukkit.createInventory(null, 54,
                LEGACY.deserialize(colorCode + "&lFloor " + roman + " &8- Drop Preview"));

        // Fill navigation row with glass
        ItemStack navBackground = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, navBackground);
        }

        // Load drops from cache
        Map<Integer, ItemStack> drops = plugin.getFloorDropsManager().getFloorDrops(floor);

        // Fill item slots (0-44)
        for (int i = 0; i < 45; i++) {
            ItemStack drop = drops.get(i);
            if (drop != null && !drop.getType().isAir()) {
                inventory.setItem(i, drop.clone());
            }
            // Empty slots remain empty (air)
        }

        // Back button
        inventory.setItem(SLOT_BACK, createItem(Material.ARROW, "&c&lBack",
                "&7Click to return to floor selection"));

        player.openInventory(inventory);
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

    public int getFloor() {
        return floor;
    }

    public Inventory getInventory() {
        return inventory;
    }
}
