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

public class InfernalCrucibleGui {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    public static final String TITLE = "&5&lInfernal Crucible";

    private final DemonTowerPlugin plugin;
    private final Player player;
    private Inventory inventory;

    // Slot positions
    public static final int SLOT_INPUT_1 = 20;         // First SANCTIFIED item
    public static final int SLOT_INPUT_2 = 24;         // Second SANCTIFIED item
    public static final int SLOT_PREVIEW = 31;         // Preview of result
    public static final int SLOT_CONFIRM = 38;         // Confirm button
    public static final int SLOT_CANCEL = 42;          // Cancel button
    public static final int SLOT_INFO = 4;             // Info display

    // State
    private ItemStack inputItem1 = null;
    private ItemStack inputItem2 = null;
    private ItemStack previewItem = null;

    // Cost
    public static final double COST = 1_000_000_000;   // 1b$
    public static final String MATERIAL_ID = "crucible_flux";
    public static final int MATERIAL_AMOUNT = 10;

    public InfernalCrucibleGui(DemonTowerPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        inventory = Bukkit.createInventory(null, 54, LEGACY.deserialize(TITLE));

        // Fill background
        ItemStack purplePane = createItem(Material.PURPLE_STAINED_GLASS_PANE, " ");
        ItemStack magentaPane = createItem(Material.MAGENTA_STAINED_GLASS_PANE, " ");

        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45) {
                inventory.setItem(i, purplePane);
            } else {
                inventory.setItem(i, magentaPane);
            }
        }

        // Info display
        inventory.setItem(SLOT_INFO, createInfoItem());

        // Input slot markers
        inventory.setItem(SLOT_INPUT_1, createItem(Material.END_CRYSTAL, "&5&lPlace First Item",
            "&7Place any item to fuse.",
            "",
            "&7Requirements:",
            "&7- Need 2 IDENTICAL copies!",
            "&7- Same item name and type",
            "",
            "&eClick or drag item here"));

        inventory.setItem(SLOT_INPUT_2, createItem(Material.END_CRYSTAL, "&5&lPlace Second Item",
            "&7Place the SAME item.",
            "",
            "&7Requirements:",
            "&7- Must be IDENTICAL to first!",
            "&7- Same item name and type",
            "",
            "&eClick or drag item here"));

        // Fusion indicator
        inventory.setItem(22, createItem(Material.DRAGON_EGG, "&5&l=", "&7Fuse into ULTIMATE!"));

        // Preview slot
        inventory.setItem(SLOT_PREVIEW, createItem(Material.BARRIER, "&7&lNo Preview",
            "&7Place two IDENTICAL items first."));

        // Cancel button
        inventory.setItem(SLOT_CANCEL, createItem(Material.RED_CONCRETE, "&c&lCancel",
            "&7Close without changes."));

        // Confirm button (disabled initially)
        inventory.setItem(SLOT_CONFIRM, createItem(Material.GRAY_CONCRETE, "&7&lWaiting for items...",
            "&7Place two IDENTICAL items."));

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
            player.sendMessage(LEGACY.deserialize("&5&lInfernal Crucible: &7You must place a single item, not a stack! Unstack your items first."));
            return false; // Item stays in player inventory
        }

        ItemState state = manipulator.getItemState(item);

        // Check - cannot fuse ULTIMATE items (already max)
        if (state == ItemState.ULTIMATE) {
            player.sendMessage(LEGACY.deserialize("&5&lInfernal Crucible: &7ULTIMATE items cannot be fused further!"));
            return false; // Item stays in player inventory
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
            player.sendMessage(LEGACY.deserialize("&5&lInfernal Crucible: &7Both slots are already filled!"));
            return false; // Item stays in player inventory
        }

        updateGui();
        return true; // Item accepted
    }

    private void updateGui() {
        // Show input items
        if (inputItem1 == null) {
            inventory.setItem(SLOT_INPUT_1, createItem(Material.END_CRYSTAL, "&5&lPlace First Item",
                "&7Place any item to fuse."));
        } else {
            inventory.setItem(SLOT_INPUT_1, inputItem1);
        }

        if (inputItem2 == null) {
            inventory.setItem(SLOT_INPUT_2, createItem(Material.END_CRYSTAL, "&5&lPlace Second Item",
                "&7Place the SAME item."));
        } else {
            inventory.setItem(SLOT_INPUT_2, inputItem2);
        }

        ItemManipulator manipulator = plugin.getItemManipulator();

        // Check if both items present and compatible
        if (inputItem1 != null && inputItem2 != null) {
            // Items must be IDENTICAL (same item type and name)
            if (!manipulator.areItemsIdentical(inputItem1, inputItem2)) {
                inventory.setItem(SLOT_PREVIEW, createItem(Material.BARRIER, "&c&lItems Not Identical!",
                    "&7Both items must be EXACTLY",
                    "&7the same item (same name/type).",
                    "",
                    "&7You need two copies of the",
                    "&7same item!"));
                inventory.setItem(SLOT_CONFIRM, createItem(Material.GRAY_CONCRETE, "&c&lCannot Fuse",
                    "&7Items must be identical."));
                previewItem = null;
                return;
            }

            // Generate preview (only once)
            if (previewItem == null) {
                previewItem = manipulator.fuseToUltimate(inputItem1.clone(), inputItem2.clone());
            }
            inventory.setItem(SLOT_PREVIEW, previewItem);

            // Check balance and materials
            boolean hasBalance = plugin.getVaultIntegration().hasBalance(player, COST);
            boolean hasMaterials = plugin.getPouchIntegration().hasItem(player, MATERIAL_ID, MATERIAL_AMOUNT);
            String costDisplay = plugin.getVaultIntegration().formatCompact(COST);
            int currentMaterials = plugin.getPouchIntegration().getItemAmount(player, MATERIAL_ID);

            if (hasBalance && hasMaterials) {
                inventory.setItem(SLOT_CONFIRM, createItem(Material.LIME_CONCRETE, "&a&lConfirm Fusion",
                    "&7Create an ULTIMATE item!",
                    "",
                    "&5&lAll stats will be DOUBLED!",
                    "",
                    "&eCost: &c" + costDisplay,
                    "&eMaterial: &c" + MATERIAL_AMOUNT + "x &cCrucible Flux",
                    "&7You have: &a" + currentMaterials,
                    "",
                    "&c&lWARNING: Only 1 ULTIMATE",
                    "&c&litem can be equipped at once!",
                    "",
                    "&aClick to confirm!"));
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
                    lore.add("&c✗ &7Material: &c" + MATERIAL_AMOUNT + "x &cCrucible Flux");
                    lore.add("  &7You have: &c" + currentMaterials);
                } else {
                    lore.add("&a✓ &7Material: &a" + MATERIAL_AMOUNT + "x &cCrucible Flux");
                }
                inventory.setItem(SLOT_CONFIRM, createItem(Material.GRAY_CONCRETE, "&c&lMissing Requirements",
                    lore.toArray(new String[0])));
            }
        } else {
            previewItem = null;
            inventory.setItem(SLOT_PREVIEW, createItem(Material.BARRIER, "&7&lNo Preview",
                "&7Place two SANCTIFIED items first."));
            inventory.setItem(SLOT_CONFIRM, createItem(Material.GRAY_CONCRETE, "&7&lWaiting for items...",
                "&7Place two SANCTIFIED items."));
        }
    }

    public void handleConfirm() {
        if (inputItem1 == null || inputItem2 == null || previewItem == null) return;

        // Check balance
        if (!plugin.getVaultIntegration().hasBalance(player, COST)) {
            player.sendMessage(LEGACY.deserialize("&5&lInfernal Crucible: &7You don't have enough money!"));
            return;
        }

        // Check materials from pouch
        if (!plugin.getPouchIntegration().hasItem(player, MATERIAL_ID, MATERIAL_AMOUNT)) {
            player.sendMessage(LEGACY.deserialize("&5&lInfernal Crucible: &7You don't have enough Crucible Flux!"));
            return;
        }

        // Withdraw money
        if (!plugin.getVaultIntegration().withdraw(player, COST)) {
            player.sendMessage(LEGACY.deserialize("&5&lInfernal Crucible: &7Transaction failed!"));
            return;
        }

        // Remove materials from pouch
        if (!plugin.getPouchIntegration().removeItem(player, MATERIAL_ID, MATERIAL_AMOUNT)) {
            // Refund money
            plugin.getVaultIntegration().deposit(player, COST);
            player.sendMessage(LEGACY.deserialize("&5&lInfernal Crucible: &7Failed to consume materials!"));
            return;
        }

        // IMPORTANT: Clear state BEFORE closing to prevent duplication in handleClose()
        // Both items consumed in fusion
        inputItem1 = null;
        inputItem2 = null;
        ItemStack result = previewItem;
        previewItem = null;

        // Give ULTIMATE item to player
        player.getInventory().addItem(result);

        player.sendMessage(LEGACY.deserialize("&5&lInfernal Crucible: &7An &5&lULTIMATE &7item has been forged!"));
        player.sendMessage(LEGACY.deserialize("&c&lRemember: &7Only ONE ULTIMATE item can be equipped at a time!"));
        player.closeInventory();
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
        lore.add("&7The Infernal Crucible fuses");
        lore.add("&7two IDENTICAL items into");
        lore.add("&7one &5&lULTIMATE &7item!");
        lore.add("");
        lore.add("&e&lRequirements:");
        lore.add("&7- Two IDENTICAL items");
        lore.add("&7- Same name and type");
        lore.add("");
        lore.add("&e&lEffects:");
        lore.add("&5ALL STATS x2!");
        lore.add("&7Doubles all stat values");
        lore.add("");
        lore.add("&c&l!!! WARNING !!!");
        lore.add("&7Only ONE ULTIMATE item");
        lore.add("&7can be equipped at once!");
        lore.add("");
        lore.add("&eCost: &c" + plugin.getVaultIntegration().formatCompact(COST));
        lore.add("&eMaterial: &c" + MATERIAL_AMOUNT + "x &cCrucible Flux");
        lore.add("&8━━━━━━━━━━━━━━━━━━━━");

        return createItem(Material.END_CRYSTAL, "&5&lInfernal Crucible", lore.toArray(new String[0]));
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
