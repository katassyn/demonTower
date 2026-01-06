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
import java.util.Random;

public class CorruptedBlacksmithGui {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    public static final String TITLE = "&4&lCorrupted Blacksmith";

    private final DemonTowerPlugin plugin;
    private final Player player;
    private Inventory inventory;
    private final Random random = new Random();

    // Slot positions
    public static final int SLOT_INPUT = 20;           // Item to corrupt
    public static final int SLOT_PREVIEW = 24;         // Preview of result
    public static final int SLOT_CONFIRM = 38;         // Confirm button
    public static final int SLOT_REROLL = 40;          // Reroll button (if has stone)
    public static final int SLOT_CANCEL = 42;          // Cancel button
    public static final int SLOT_INFO = 4;             // Info display

    // State
    private ItemStack inputItem = null;
    private ItemStack previewItem = null;
    private int corruptionType = -1;                   // 0-3 for the 4 outcomes
    private boolean hasRerolled = false;
    private boolean hasSmithStone = false;

    // Cost
    public static final double COST = 1_000_000;       // 1m$

    public CorruptedBlacksmithGui(DemonTowerPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        inventory = Bukkit.createInventory(null, 54, LEGACY.deserialize(TITLE));

        // Fill background
        ItemStack redPane = createItem(Material.RED_STAINED_GLASS_PANE, " ");
        ItemStack blackPane = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");

        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45) {
                inventory.setItem(i, redPane);
            } else {
                inventory.setItem(i, blackPane);
            }
        }

        // Check if player has smith stone
        hasSmithStone = plugin.getMythicMobsIntegration().hasItem(player, "smith_stone");

        // Info display
        inventory.setItem(SLOT_INFO, createInfoItem());

        // Input slot marker
        inventory.setItem(SLOT_INPUT, createItem(Material.ANVIL, "&c&lPlace Item Here",
            "&7Place an item to corrupt it.",
            "",
            "&cWarning: This is unpredictable!",
            "&725% - Upgrade a stat by 30%",
            "&725% - Remove a random stat",
            "&725% - Add a new stat",
            "&725% - Remove ALL stats!",
            "",
            "&7Protection stat is never affected."));

        // Preview slot
        inventory.setItem(SLOT_PREVIEW, createItem(Material.BARRIER, "&7&lNo Preview",
            "&7Place an item first."));

        // Cancel button
        inventory.setItem(SLOT_CANCEL, createItem(Material.RED_CONCRETE, "&c&lCancel",
            "&7Close without changes."));

        player.openInventory(inventory);
    }

    public void handleItemPlace(ItemStack item) {
        ItemManipulator manipulator = plugin.getItemManipulator();

        // Check if item is already corrupted
        ItemState state = manipulator.getItemState(item);
        if (state == ItemState.CORRUPTED) {
            player.sendMessage(LEGACY.deserialize("&c&lCorrupted Blacksmith: &7This item is already CORRUPTED and cannot be modified!"));
            returnItemToPlayer(item);
            return;
        }

        // Check if item is ULTIMATE
        if (state == ItemState.ULTIMATE) {
            player.sendMessage(LEGACY.deserialize("&c&lCorrupted Blacksmith: &7ULTIMATE items cannot be corrupted!"));
            returnItemToPlayer(item);
            return;
        }

        inputItem = item;
        hasRerolled = false;

        // Roll corruption
        rollCorruption();

        // Update GUI
        updateGui();
    }

    private void rollCorruption() {
        corruptionType = random.nextInt(4);
        ItemManipulator manipulator = plugin.getItemManipulator();

        switch (corruptionType) {
            case 0: // Upgrade stat by 30%
                previewItem = manipulator.upgradeRandomStat(inputItem, 0.30);
                break;
            case 1: // Remove random stat
                previewItem = manipulator.removeRandomStat(inputItem);
                break;
            case 2: // Add new stat
                previewItem = manipulator.addRandomStat(inputItem);
                break;
            case 3: // Remove ALL stats
                previewItem = manipulator.removeAllStats(inputItem);
                break;
        }
    }

    private void updateGui() {
        // Show input item
        inventory.setItem(SLOT_INPUT, inputItem);

        // Show preview with corruption type info
        if (previewItem != null) {
            ItemStack displayPreview = previewItem.clone();
            ItemMeta meta = displayPreview.getItemMeta();
            if (meta != null) {
                List<Component> lore = meta.lore();
                if (lore == null) lore = new ArrayList<>();
                else lore = new ArrayList<>(lore);

                lore.add(Component.empty());
                lore.add(LEGACY.deserialize("&8━━━━━━━━━━━━━━━━"));
                lore.add(LEGACY.deserialize("&7Corruption result:"));

                switch (corruptionType) {
                    case 0:
                        lore.add(LEGACY.deserialize("&a+30% to a random stat!"));
                        break;
                    case 1:
                        lore.add(LEGACY.deserialize("&eA stat was removed."));
                        break;
                    case 2:
                        lore.add(LEGACY.deserialize("&bA new stat was added!"));
                        break;
                    case 3:
                        lore.add(LEGACY.deserialize("&c&lALL stats removed!"));
                        break;
                }

                meta.lore(lore);
                displayPreview.setItemMeta(meta);
            }
            inventory.setItem(SLOT_PREVIEW, displayPreview);
        }

        // Check balance
        boolean hasBalance = plugin.getVaultIntegration().hasBalance(player, COST);
        String costDisplay = plugin.getVaultIntegration().formatCompact(COST);

        // Confirm button
        if (hasBalance) {
            inventory.setItem(SLOT_CONFIRM, createItem(Material.LIME_CONCRETE, "&a&lConfirm Corruption",
                "&7Apply this corruption.",
                "",
                "&eCost: &c" + costDisplay,
                "",
                "&aClick to confirm!"));
        } else {
            inventory.setItem(SLOT_CONFIRM, createItem(Material.GRAY_CONCRETE, "&c&lInsufficient Funds",
                "&7You don't have enough money.",
                "",
                "&eCost: &c" + costDisplay,
                "&7Your balance: &c" + plugin.getVaultIntegration().formatCompact(plugin.getVaultIntegration().getBalance(player))));
        }

        // Reroll button
        if (!hasRerolled && hasSmithStone) {
            inventory.setItem(SLOT_REROLL, createItem(Material.STONE, "&6&lReroll (Smith Stone)",
                "&7Use your Corrupted Smithing Stone",
                "&7to try a different corruption.",
                "",
                "&eYou have a smith_stone!",
                "",
                "&6Click to reroll once!"));
        } else if (hasRerolled) {
            inventory.setItem(SLOT_REROLL, createItem(Material.BARRIER, "&c&lAlready Rerolled",
                "&7You already used your reroll."));
        } else {
            inventory.setItem(SLOT_REROLL, createItem(Material.GRAY_STAINED_GLASS_PANE, "&7&lNo Reroll Available",
                "&7You need a Corrupted Smithing Stone",
                "&7to reroll the corruption result."));
        }
    }

    public void handleConfirm() {
        if (inputItem == null || previewItem == null) return;

        // Check balance
        if (!plugin.getVaultIntegration().hasBalance(player, COST)) {
            player.sendMessage(LEGACY.deserialize("&c&lCorrupted Blacksmith: &7You don't have enough money!"));
            return;
        }

        // Withdraw money
        if (!plugin.getVaultIntegration().withdraw(player, COST)) {
            player.sendMessage(LEGACY.deserialize("&c&lCorrupted Blacksmith: &7Transaction failed!"));
            return;
        }

        // Give corrupted item to player
        player.getInventory().addItem(previewItem);

        player.sendMessage(LEGACY.deserialize("&4&lCorrupted Blacksmith: &7Your item has been corrupted!"));
        player.closeInventory();

        // Clear state
        inputItem = null;
        previewItem = null;
    }

    public void handleReroll() {
        if (inputItem == null || hasRerolled || !hasSmithStone) return;

        // Remove smith stone
        if (!plugin.getMythicMobsIntegration().removeItem(player, "smith_stone", 1)) {
            player.sendMessage(LEGACY.deserialize("&c&lCorrupted Blacksmith: &7Failed to consume smithing stone!"));
            return;
        }

        hasRerolled = true;
        hasSmithStone = false;

        // Reroll corruption
        rollCorruption();

        player.sendMessage(LEGACY.deserialize("&6&lCorrupted Blacksmith: &7You used a Corrupted Smithing Stone to reroll!"));

        // Update GUI
        updateGui();
    }

    public void handleCancel() {
        // Return input item if exists
        if (inputItem != null) {
            returnItemToPlayer(inputItem);
            inputItem = null;
        }
        player.closeInventory();
    }

    public void handleClose() {
        // Return input item if exists
        if (inputItem != null) {
            returnItemToPlayer(inputItem);
            inputItem = null;
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
        lore.add("&7The Corrupted Blacksmith can");
        lore.add("&7transform your items in");
        lore.add("&7unpredictable ways!");
        lore.add("");
        lore.add("&e&lPossible Outcomes:");
        lore.add("&a25% - Upgrade stat +30%");
        lore.add("&e25% - Remove a stat");
        lore.add("&b25% - Add new stat");
        lore.add("&c25% - Remove ALL stats!");
        lore.add("");
        lore.add("&7Protection is never affected.");
        lore.add("");
        lore.add("&eCost: &c" + plugin.getVaultIntegration().formatCompact(COST));
        lore.add("");
        if (hasSmithStone) {
            lore.add("&6You have a Smithing Stone!");
            lore.add("&7Use it for one free reroll.");
        }
        lore.add("&8━━━━━━━━━━━━━━━━━━━━");

        return createItem(Material.ANVIL, "&c&lCorrupted Blacksmith", lore.toArray(new String[0]));
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

    public ItemStack getInputItem() {
        return inputItem;
    }

    public int getInputSlot() {
        return SLOT_INPUT;
    }
}
