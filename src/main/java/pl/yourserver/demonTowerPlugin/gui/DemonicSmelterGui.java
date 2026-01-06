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
import pl.yourserver.demonTowerPlugin.mechanics.ItemManipulator;
import pl.yourserver.demonTowerPlugin.mechanics.ItemState;

import java.util.ArrayList;
import java.util.List;

public class DemonicSmelterGui {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    public static final String TITLE = "&6&lDemonic Smelter";

    private final DemonTowerPlugin plugin;
    private final Player player;
    private Inventory inventory;

    // Slot positions
    public static final int SLOT_INPUT_1 = 20;         // First item
    public static final int SLOT_INPUT_2 = 24;         // Second item
    public static final int SLOT_PREVIEW = 31;         // Preview of result
    public static final int SLOT_CONFIRM = 38;         // Confirm button
    public static final int SLOT_CANCEL = 42;          // Cancel button
    public static final int SLOT_INFO = 4;             // Info display

    // State
    private ItemStack inputItem1 = null;
    private ItemStack inputItem2 = null;
    private ItemStack previewItem = null;

    // Cost
    public static final double COST = 50_000_000;      // 50m$

    public DemonicSmelterGui(DemonTowerPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        inventory = Bukkit.createInventory(null, 54, LEGACY.deserialize(TITLE));

        // Fill background
        ItemStack orangePane = createItem(Material.ORANGE_STAINED_GLASS_PANE, " ");
        ItemStack blackPane = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");

        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45) {
                inventory.setItem(i, orangePane);
            } else {
                inventory.setItem(i, blackPane);
            }
        }

        // Info display
        inventory.setItem(SLOT_INFO, createInfoItem());

        // Input slot markers
        inventory.setItem(SLOT_INPUT_1, createItem(Material.BLAST_FURNACE, "&6&lPlace First Item",
            "&7Place the first item to merge."));

        inventory.setItem(SLOT_INPUT_2, createItem(Material.BLAST_FURNACE, "&6&lPlace Second Item",
            "&7Place the second item to merge."));

        // Arrow indicator
        inventory.setItem(22, createItem(Material.MAGMA_CREAM, "&6&l+", "&7Merge items together"));

        // Preview slot
        inventory.setItem(SLOT_PREVIEW, createItem(Material.BARRIER, "&7&lNo Preview",
            "&7Place two items first."));

        // Cancel button
        inventory.setItem(SLOT_CANCEL, createItem(Material.RED_CONCRETE, "&c&lCancel",
            "&7Close without changes."));

        // Confirm button (disabled initially)
        inventory.setItem(SLOT_CONFIRM, createItem(Material.GRAY_CONCRETE, "&7&lWaiting for items...",
            "&7Place two items to merge."));

        player.openInventory(inventory);
    }

    public void handleItemPlace(int slot, ItemStack item) {
        ItemManipulator manipulator = plugin.getItemManipulator();
        ItemState state = manipulator.getItemState(item);

        // Check restrictions
        if (state == ItemState.CORRUPTED) {
            player.sendMessage(LEGACY.deserialize("&6&lDemonic Smelter: &7CORRUPTED items cannot be smelted!"));
            returnItemToPlayer(item);
            return;
        }

        if (state == ItemState.SMELTED) {
            player.sendMessage(LEGACY.deserialize("&6&lDemonic Smelter: &7Already smelted items cannot be smelted again!"));
            returnItemToPlayer(item);
            return;
        }

        if (state == ItemState.ULTIMATE) {
            player.sendMessage(LEGACY.deserialize("&6&lDemonic Smelter: &7ULTIMATE items cannot be smelted!"));
            returnItemToPlayer(item);
            return;
        }

        if (slot == SLOT_INPUT_1) {
            inputItem1 = item;
            previewItem = null;  // Reset preview when item changes
        } else if (slot == SLOT_INPUT_2) {
            inputItem2 = item;
            previewItem = null;  // Reset preview when item changes
        }

        updateGui();
    }

    private void updateGui() {
        // Show input items
        inventory.setItem(SLOT_INPUT_1, inputItem1 != null ? inputItem1 :
            createItem(Material.BLAST_FURNACE, "&6&lPlace First Item", "&7Place the first item to merge."));
        inventory.setItem(SLOT_INPUT_2, inputItem2 != null ? inputItem2 :
            createItem(Material.BLAST_FURNACE, "&6&lPlace Second Item", "&7Place the second item to merge."));

        // Generate preview if both items present (only generate once)
        if (inputItem1 != null && inputItem2 != null) {
            if (previewItem == null) {
                previewItem = generateSmeltedItem();
            }
            inventory.setItem(SLOT_PREVIEW, previewItem);

            // Check balance
            boolean hasBalance = plugin.getVaultIntegration().hasBalance(player, COST);
            String costDisplay = plugin.getVaultIntegration().formatCompact(COST);

            if (hasBalance) {
                inventory.setItem(SLOT_CONFIRM, createItem(Material.LIME_CONCRETE, "&a&lConfirm Smelting",
                    "&7Merge these items into one.",
                    "",
                    "&eCost: &c" + costDisplay,
                    "",
                    "&aClick to confirm!"));
            } else {
                inventory.setItem(SLOT_CONFIRM, createItem(Material.GRAY_CONCRETE, "&c&lInsufficient Funds",
                    "&7You don't have enough money.",
                    "",
                    "&eCost: &c" + costDisplay));
            }
        } else {
            previewItem = null;
            inventory.setItem(SLOT_PREVIEW, createItem(Material.BARRIER, "&7&lNo Preview",
                "&7Place two items first."));
            inventory.setItem(SLOT_CONFIRM, createItem(Material.GRAY_CONCRETE, "&7&lWaiting for items...",
                "&7Place two items to merge."));
        }
    }

    private ItemStack generateSmeltedItem() {
        ItemManipulator manipulator = plugin.getItemManipulator();
        return manipulator.smeltItems(inputItem1, inputItem2);
    }

    public void handleConfirm() {
        if (inputItem1 == null || inputItem2 == null || previewItem == null) return;

        // Check balance
        if (!plugin.getVaultIntegration().hasBalance(player, COST)) {
            player.sendMessage(LEGACY.deserialize("&6&lDemonic Smelter: &7You don't have enough money!"));
            return;
        }

        // Withdraw money
        if (!plugin.getVaultIntegration().withdraw(player, COST)) {
            player.sendMessage(LEGACY.deserialize("&6&lDemonic Smelter: &7Transaction failed!"));
            return;
        }

        // Give smelted item to player
        player.getInventory().addItem(previewItem);

        player.sendMessage(LEGACY.deserialize("&6&lDemonic Smelter: &7Your items have been merged!"));
        player.closeInventory();

        // Clear state (items consumed)
        inputItem1 = null;
        inputItem2 = null;
        previewItem = null;
    }

    public void handleCancel() {
        // Return input items
        if (inputItem1 != null) {
            returnItemToPlayer(inputItem1);
            inputItem1 = null;
        }
        if (inputItem2 != null) {
            returnItemToPlayer(inputItem2);
            inputItem2 = null;
        }
        player.closeInventory();
    }

    public void handleClose() {
        // Return input items
        if (inputItem1 != null) {
            returnItemToPlayer(inputItem1);
            inputItem1 = null;
        }
        if (inputItem2 != null) {
            returnItemToPlayer(inputItem2);
            inputItem2 = null;
        }
    }

    private void returnItemToPlayer(ItemStack item) {
        if (item == null || item.getType().isAir()) return;

        if (player.isOnline()) {
            // Try to add to inventory
            var leftover = player.getInventory().addItem(item);
            // Drop any items that didn't fit
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        } else {
            // Player offline - drop at their last location
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
    }

    private ItemStack createInfoItem() {
        List<String> lore = new ArrayList<>();
        lore.add("&8━━━━━━━━━━━━━━━━━━━━");
        lore.add("&7The Demonic Smelter merges");
        lore.add("&7two items into one!");
        lore.add("");
        lore.add("&e&lHow it works:");
        lore.add("&7- Place two items");
        lore.add("&7- Stats are randomly picked");
        lore.add("&7- Base item randomly selected");
        lore.add("&7- Item becomes 'Smelted'");
        lore.add("");
        lore.add("&c&lRestrictions:");
        lore.add("&7- No CORRUPTED items");
        lore.add("&7- No already smelted items");
        lore.add("");
        lore.add("&eCost: &c" + plugin.getVaultIntegration().formatCompact(COST));
        lore.add("&8━━━━━━━━━━━━━━━━━━━━");

        return createItem(Material.BLAST_FURNACE, "&6&lDemonic Smelter", lore.toArray(new String[0]));
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

    public int getInputSlot1() {
        return SLOT_INPUT_1;
    }

    public int getInputSlot2() {
        return SLOT_INPUT_2;
    }
}
