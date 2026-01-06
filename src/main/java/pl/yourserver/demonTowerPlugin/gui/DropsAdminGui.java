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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DropsAdminGui {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final DemonTowerPlugin plugin;
    private final Player player;
    private final int floor;
    private Inventory inventory;

    // Track items placed by admin
    private final Map<Integer, ItemStack> pendingItems = new HashMap<>();

    // Layout: 54 slots (6 rows)
    // Rows 0-4 (slots 0-44): Drop item slots
    // Row 5 (slots 45-53): Controls
    public static final int SLOT_SAVE = 47;
    public static final int SLOT_CLEAR = 49;
    public static final int SLOT_BACK = 51;

    public DropsAdminGui(DemonTowerPlugin plugin, Player player, int floor) {
        this.plugin = plugin;
        this.player = player;
        this.floor = floor;
    }

    public void open() {
        String roman = plugin.getFloorDropsManager().getRomanNumeral(floor);
        String colorCode = getFloorColor(floor);

        inventory = Bukkit.createInventory(null, 54,
                LEGACY.deserialize(colorCode + "&lFloor " + roman + " &8- Admin Edit"));

        refresh();

        player.openInventory(inventory);
    }

    public void refresh() {
        // Fill navigation row with glass
        ItemStack navBackground = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, navBackground);
        }

        // Load current drops
        Map<Integer, ItemStack> drops = plugin.getFloorDropsManager().getFloorDrops(floor);

        // Fill item slots (0-44) with current drops
        for (int i = 0; i < 45; i++) {
            ItemStack drop = drops.get(i);
            if (drop != null && !drop.getType().isAir()) {
                inventory.setItem(i, drop.clone());
                pendingItems.put(i, drop.clone());
            } else {
                inventory.setItem(i, null);
            }
        }

        // Save button
        inventory.setItem(SLOT_SAVE, createItem(Material.EMERALD_BLOCK, "&a&lSave Changes",
                "&7Save all items to database",
                "",
                "&eClick to save!"));

        // Clear all button
        inventory.setItem(SLOT_CLEAR, createItem(Material.BARRIER, "&c&lClear All",
                "&7Remove all items from this floor",
                "",
                "&cClick to clear!"));

        // Back button
        inventory.setItem(SLOT_BACK, createItem(Material.ARROW, "&6&lBack",
                "&7Return to floor selection",
                "",
                "&eClick to go back"));
    }

    public void handleItemPlace(int slot, ItemStack item) {
        if (slot < 0 || slot >= 45) return;

        if (item == null || item.getType().isAir()) {
            pendingItems.remove(slot);
            inventory.setItem(slot, null);
        } else {
            pendingItems.put(slot, item.clone());
            inventory.setItem(slot, item.clone());
        }
    }

    public void handleItemRemove(int slot) {
        if (slot < 0 || slot >= 45) return;

        pendingItems.remove(slot);
        inventory.setItem(slot, null);
    }

    public void handleSave() {
        // Save all pending items to database
        plugin.getFloorDropsManager().saveFloorDrops(floor, pendingItems);

        player.sendMessage(LEGACY.deserialize("&a&lDrops saved! &7Floor " +
                plugin.getFloorDropsManager().getRomanNumeral(floor) + " now has " +
                pendingItems.size() + " items."));
    }

    public void handleClear() {
        pendingItems.clear();

        // Clear inventory slots
        for (int i = 0; i < 45; i++) {
            inventory.setItem(i, null);
        }

        player.sendMessage(LEGACY.deserialize("&c&lAll items cleared! &7Click Save to confirm."));
    }

    public void handleClose() {
        // Return any items in cursor to player
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            player.setItemOnCursor(null);
            giveItemToPlayer(cursor);
        }
    }

    private void giveItemToPlayer(ItemStack item) {
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            leftover.values().forEach(i -> player.getWorld().dropItem(player.getLocation(), i));
        }
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

    public Map<Integer, ItemStack> getPendingItems() {
        return pendingItems;
    }
}
