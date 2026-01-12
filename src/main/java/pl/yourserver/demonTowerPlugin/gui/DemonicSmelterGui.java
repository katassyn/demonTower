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
    public static final String MATERIAL_ID = "smelter_coal";
    public static final int MATERIAL_AMOUNT = 10;

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

        // No cancel button for smelter - result is random, action cannot be undone
        // Slot 42 stays as background

        // Confirm button (disabled initially)
        inventory.setItem(SLOT_CONFIRM, createItem(Material.GRAY_CONCRETE, "&7&lWaiting for items...",
            "&7Place two items to merge."));

        player.openInventory(inventory);
    }

    /**
     * Try to place an item in the GUI.
     * @return true if item was accepted, false if rejected (item stays in player inventory)
     */
    public boolean handleItemPlace(int slot, ItemStack item) {
        ItemManipulator manipulator = plugin.getItemManipulator();

        // Require unstacked item (amount = 1)
        if (item.getAmount() > 1) {
            player.sendMessage(LEGACY.deserialize("&6&lDemonic Smelter: &7You must place a single item, not a stack! Unstack your items first."));
            return false;
        }

        ItemState state = manipulator.getItemState(item);

        // Check restrictions
        if (state == ItemState.CORRUPTED) {
            player.sendMessage(LEGACY.deserialize("&6&lDemonic Smelter: &7CORRUPTED items cannot be smelted!"));
            return false;
        }

        if (state == ItemState.SMELTED) {
            player.sendMessage(LEGACY.deserialize("&6&lDemonic Smelter: &7Already smelted items cannot be smelted again!"));
            return false;
        }

        if (state == ItemState.ULTIMATE) {
            player.sendMessage(LEGACY.deserialize("&6&lDemonic Smelter: &7ULTIMATE items cannot be smelted!"));
            return false;
        }

        // Check item type compatibility if one slot is already filled
        ItemManipulator manipulator2 = plugin.getItemManipulator();
        if (inputItem1 != null && inputItem2 == null) {
            // Second item being added - check if same type as first
            if (!manipulator2.isSameBaseType(inputItem1, item)) {
                player.sendMessage(LEGACY.deserialize("&6&lDemonic Smelter: &7Both items must be the same type (weapon/armor/accessory)!"));
                return false;
            }
        } else if (inputItem2 != null && inputItem1 == null) {
            // First slot being filled after second - check type
            if (!manipulator2.isSameBaseType(item, inputItem2)) {
                player.sendMessage(LEGACY.deserialize("&6&lDemonic Smelter: &7Both items must be the same type (weapon/armor/accessory)!"));
                return false;
            }
        }

        if (slot == SLOT_INPUT_1 || inputItem1 == null) {
            inputItem1 = item;
            inventory.setItem(SLOT_INPUT_1, item);
            previewItem = null;  // Reset preview when item changes
        } else if (slot == SLOT_INPUT_2 || inputItem2 == null) {
            inputItem2 = item;
            inventory.setItem(SLOT_INPUT_2, item);
            previewItem = null;  // Reset preview when item changes
        } else {
            // Both slots full
            player.sendMessage(LEGACY.deserialize("&6&lDemonic Smelter: &7Both slots are already filled!"));
            return false;
        }

        updateGui();
        return true;
    }

    private void updateGui() {
        // Show input items
        if (inputItem1 == null) {
            inventory.setItem(SLOT_INPUT_1, createItem(Material.BLAST_FURNACE, "&6&lPlace First Item",
                "&7Place the first item to merge.",
                "",
                "&eClick or drag item here"));
        } else {
            inventory.setItem(SLOT_INPUT_1, inputItem1);
        }

        if (inputItem2 == null) {
            inventory.setItem(SLOT_INPUT_2, createItem(Material.BLAST_FURNACE, "&6&lPlace Second Item",
                "&7Place the second item to merge.",
                "",
                "&eClick or drag item here"));
        } else {
            inventory.setItem(SLOT_INPUT_2, inputItem2);
        }

        // NO PREVIEW - result is random and generated only after payment
        if (inputItem1 != null && inputItem2 != null) {
            // Show "?" instead of preview - result is unknown until smelting
            inventory.setItem(SLOT_PREVIEW, createItem(Material.ORANGE_STAINED_GLASS_PANE, "&6&l?",
                "&7Result will be random!",
                "&7Stats are randomly picked",
                "&7from both items.",
                "",
                "&cNo preview - pay to smelt!"));

            // Check balance and materials
            boolean hasBalance = plugin.getVaultIntegration().hasBalance(player, COST);
            boolean hasMaterials = plugin.getPouchIntegration().hasItem(player, MATERIAL_ID, MATERIAL_AMOUNT);
            String costDisplay = plugin.getVaultIntegration().formatCompact(COST);
            int currentMaterials = plugin.getPouchIntegration().getItemAmount(player, MATERIAL_ID);

            if (hasBalance && hasMaterials) {
                inventory.setItem(SLOT_CONFIRM, createItem(Material.LIME_CONCRETE, "&a&lSmelt Items",
                    "&7Merge these items into one.",
                    "&cResult is RANDOM and FINAL!",
                    "",
                    "&eCost: &c" + costDisplay,
                    "&eMaterial: &c" + MATERIAL_AMOUNT + "x &5Smelter Magma",
                    "&7You have: &a" + currentMaterials,
                    "",
                    "&aClick to smelt!"));
            } else {
                List<String> lore = new ArrayList<>();
                lore.add("&7You're missing requirements:");
                lore.add("");
                if (!hasBalance) {
                    lore.add("&c✗ &7Money: &c" + costDisplay);
                } else {
                    lore.add("&a✓ &7Money: &a" + costDisplay);
                }
                if (!hasMaterials) {
                    lore.add("&c✗ &7Material: &c" + MATERIAL_AMOUNT + "x &5Smelter Magma");
                    lore.add("  &7You have: &c" + currentMaterials);
                } else {
                    lore.add("&a✓ &7Material: &a" + MATERIAL_AMOUNT + "x &5Smelter Magma");
                }
                inventory.setItem(SLOT_CONFIRM, createItem(Material.GRAY_CONCRETE, "&c&lMissing Requirements",
                    lore.toArray(new String[0])));
            }
        } else {
            inventory.setItem(SLOT_PREVIEW, createItem(Material.BARRIER, "&7&lNo Items",
                "&7Place two items first."));
            inventory.setItem(SLOT_CONFIRM, createItem(Material.GRAY_CONCRETE, "&7&lWaiting for items...",
                "&7Place two items to merge."));
        }
    }

    public void handleConfirm() {
        if (inputItem1 == null || inputItem2 == null) return;

        // Check balance
        if (!plugin.getVaultIntegration().hasBalance(player, COST)) {
            player.sendMessage(LEGACY.deserialize("&6&lDemonic Smelter: &7You don't have enough money!"));
            return;
        }

        // Check materials from pouch
        if (!plugin.getPouchIntegration().hasItem(player, MATERIAL_ID, MATERIAL_AMOUNT)) {
            player.sendMessage(LEGACY.deserialize("&6&lDemonic Smelter: &7You don't have enough Smelter Magma!"));
            return;
        }

        // Withdraw money FIRST
        if (!plugin.getVaultIntegration().withdraw(player, COST)) {
            player.sendMessage(LEGACY.deserialize("&6&lDemonic Smelter: &7Transaction failed!"));
            return;
        }

        // Remove materials from pouch
        if (!plugin.getPouchIntegration().removeItem(player, MATERIAL_ID, MATERIAL_AMOUNT)) {
            // Refund money
            plugin.getVaultIntegration().deposit(player, COST);
            player.sendMessage(LEGACY.deserialize("&6&lDemonic Smelter: &7Failed to consume materials!"));
            return;
        }

        // Generate result NOW (after payment) - this is the final result
        ItemManipulator manipulator = plugin.getItemManipulator();
        ItemStack result = manipulator.smeltItems(inputItem1, inputItem2);

        // IMPORTANT: Clear state BEFORE closing to prevent duplication in handleClose()
        // Items are consumed in the smelting process
        inputItem1 = null;
        inputItem2 = null;

        // Give smelted item to player
        player.getInventory().addItem(result);

        player.sendMessage(LEGACY.deserialize("&6&lDemonic Smelter: &7Your items have been merged!"));
        player.closeInventory();
    }

    // No cancel method - smelting is RNG and cannot be cancelled once items are placed
    // Players must close inventory to retrieve items before confirming

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
        lore.add("&eMaterial: &c" + MATERIAL_AMOUNT + "x &5Smelter Magma");
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

    public ItemStack getInputItem1() {
        return inputItem1;
    }

    public ItemStack getInputItem2() {
        return inputItem2;
    }
}
