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
    public static final int SLOT_INPUT_1 = 20;         // First identical item
    public static final int SLOT_INPUT_2 = 24;         // Second identical item
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
            "&7Place the first identical item."));

        inventory.setItem(SLOT_INPUT_2, createItem(Material.END_CRYSTAL, "&5&lPlace Second Item",
            "&7Place the second identical item.",
            "&7Must be the same as the first!"));

        // Fusion indicator
        inventory.setItem(22, createItem(Material.DRAGON_EGG, "&5&l=", "&7Fuse into ULTIMATE!"));

        // Preview slot
        inventory.setItem(SLOT_PREVIEW, createItem(Material.BARRIER, "&7&lNo Preview",
            "&7Place two identical items first."));

        // Cancel button
        inventory.setItem(SLOT_CANCEL, createItem(Material.RED_CONCRETE, "&c&lCancel",
            "&7Close without changes."));

        // Confirm button (disabled initially)
        inventory.setItem(SLOT_CONFIRM, createItem(Material.GRAY_CONCRETE, "&7&lWaiting for items...",
            "&7Place two identical items."));

        player.openInventory(inventory);
    }

    public void handleItemPlace(int slot, ItemStack item) {
        ItemManipulator manipulator = plugin.getItemManipulator();
        ItemState state = manipulator.getItemState(item);

        // Check restrictions
        if (state == ItemState.CORRUPTED) {
            player.sendMessage(LEGACY.deserialize("&5&lInfernal Crucible: &7CORRUPTED items cannot be fused!"));
            returnItemToPlayer(item);
            return;
        }

        if (state == ItemState.SMELTED) {
            player.sendMessage(LEGACY.deserialize("&5&lInfernal Crucible: &7Smelted items cannot be fused!"));
            returnItemToPlayer(item);
            return;
        }

        if (state == ItemState.SANCTIFIED) {
            player.sendMessage(LEGACY.deserialize("&5&lInfernal Crucible: &7Sanctified items cannot be fused!"));
            returnItemToPlayer(item);
            return;
        }

        if (state == ItemState.ULTIMATE) {
            player.sendMessage(LEGACY.deserialize("&5&lInfernal Crucible: &7ULTIMATE items cannot be fused again!"));
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
            createItem(Material.END_CRYSTAL, "&5&lPlace First Item",
                "&7Place the first identical item."));
        inventory.setItem(SLOT_INPUT_2, inputItem2 != null ? inputItem2 :
            createItem(Material.END_CRYSTAL, "&5&lPlace Second Item",
                "&7Place the second identical item.",
                "&7Must be the same as the first!"));

        ItemManipulator manipulator = plugin.getItemManipulator();

        // Check if items are identical
        if (inputItem1 != null && inputItem2 != null) {
            if (!manipulator.areItemsIdentical(inputItem1, inputItem2)) {
                inventory.setItem(SLOT_PREVIEW, createItem(Material.BARRIER, "&c&lItems Not Identical!",
                    "&7Both items must be the same",
                    "&7type and name to fuse."));
                inventory.setItem(SLOT_CONFIRM, createItem(Material.GRAY_CONCRETE, "&c&lCannot Fuse",
                    "&7Items must be identical."));
                previewItem = null;
                return;
            }

            // Generate preview (only once)
            if (previewItem == null) {
                previewItem = manipulator.fuseItems(inputItem1, inputItem2);
            }
            inventory.setItem(SLOT_PREVIEW, previewItem);

            // Check balance
            boolean hasBalance = plugin.getVaultIntegration().hasBalance(player, COST);
            String costDisplay = plugin.getVaultIntegration().formatCompact(COST);

            if (hasBalance) {
                inventory.setItem(SLOT_CONFIRM, createItem(Material.LIME_CONCRETE, "&a&lConfirm Fusion",
                    "&7Create an ULTIMATE item!",
                    "",
                    "&5&lAll stats will be DOUBLED!",
                    "",
                    "&eCost: &c" + costDisplay,
                    "",
                    "&c&lWARNING: Only 1 ULTIMATE",
                    "&c&litem can be equipped at once!",
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
                "&7Place two identical items first."));
            inventory.setItem(SLOT_CONFIRM, createItem(Material.GRAY_CONCRETE, "&7&lWaiting for items...",
                "&7Place two identical items."));
        }
    }

    public void handleConfirm() {
        if (inputItem1 == null || inputItem2 == null || previewItem == null) return;

        ItemManipulator manipulator = plugin.getItemManipulator();

        // Verify items are still identical
        if (!manipulator.areItemsIdentical(inputItem1, inputItem2)) {
            player.sendMessage(LEGACY.deserialize("&5&lInfernal Crucible: &7Items must be identical!"));
            return;
        }

        // Check balance
        if (!plugin.getVaultIntegration().hasBalance(player, COST)) {
            player.sendMessage(LEGACY.deserialize("&5&lInfernal Crucible: &7You don't have enough money!"));
            return;
        }

        // Withdraw money
        if (!plugin.getVaultIntegration().withdraw(player, COST)) {
            player.sendMessage(LEGACY.deserialize("&5&lInfernal Crucible: &7Transaction failed!"));
            return;
        }

        // Give ULTIMATE item to player
        player.getInventory().addItem(previewItem);

        player.sendMessage(LEGACY.deserialize("&5&lInfernal Crucible: &7An ULTIMATE item has been forged!"));
        player.sendMessage(LEGACY.deserialize("&c&lRemember: &7Only ONE ULTIMATE item can be equipped at a time!"));
        player.closeInventory();

        // Clear state (both items consumed)
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
        lore.add("&7The Infernal Crucible fuses");
        lore.add("&7two identical items into");
        lore.add("&7one ULTIMATE item!");
        lore.add("");
        lore.add("&e&lEffects:");
        lore.add("&5ALL STATS x2!");
        lore.add("");
        lore.add("&c&lRestrictions:");
        lore.add("&7- Items must be IDENTICAL");
        lore.add("&7- No CORRUPTED items");
        lore.add("&7- No Smelted items");
        lore.add("&7- No Sanctified items");
        lore.add("");
        lore.add("&c&l!!! WARNING !!!");
        lore.add("&7Only ONE ULTIMATE item");
        lore.add("&7can be equipped at once!");
        lore.add("&7(Including weapons)");
        lore.add("");
        lore.add("&eCost: &c" + plugin.getVaultIntegration().formatCompact(COST));
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
}
