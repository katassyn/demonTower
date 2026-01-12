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
    public static final int SLOT_PROCEED = 24;         // Proceed button (roll and apply immediately)
    public static final int SLOT_REROLL_STONE = 40;    // Info about reroll stone
    public static final int SLOT_INFO = 4;             // Info display

    // State
    private ItemStack inputItem = null;
    private ItemStack originalItemForReroll = null;  // Original item stored for reroll
    private ItemStack resultItem = null;              // Result item in reroll state
    private boolean hasRerolled = false;
    private boolean hasSmithStone = false;

    // Cost
    public static final double COST = 1_000_000;       // 1m$
    public static final String MATERIAL_ID = "blacksmith_scrap";
    public static final int MATERIAL_AMOUNT = 10;

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
            "&7Protection stat is never affected.",
            "",
            "&eClick or drag item here"));

        // Proceed button (disabled initially)
        inventory.setItem(SLOT_PROCEED, createItem(Material.GRAY_CONCRETE, "&7&lWaiting for item...",
            "&7Place an item first."));

        // Reroll stone info
        updateRerollStoneInfo();

        player.openInventory(inventory);
    }

    /**
     * Try to place an item in the GUI.
     * @return true if item was accepted, false if rejected (item stays in player inventory)
     */
    public boolean handleItemPlace(ItemStack item) {
        ItemManipulator manipulator = plugin.getItemManipulator();

        // Require unstacked item (amount = 1)
        if (item.getAmount() > 1) {
            player.sendMessage(LEGACY.deserialize("&c&lCorrupted Blacksmith: &7You must place a single item, not a stack! Unstack your items first."));
            return false;
        }

        // Check if item is already corrupted
        ItemState state = manipulator.getItemState(item);
        if (state == ItemState.CORRUPTED) {
            player.sendMessage(LEGACY.deserialize("&c&lCorrupted Blacksmith: &7This item is already CORRUPTED and cannot be modified!"));
            return false;
        }

        // Check if item is ULTIMATE
        if (state == ItemState.ULTIMATE) {
            player.sendMessage(LEGACY.deserialize("&c&lCorrupted Blacksmith: &7ULTIMATE items cannot be corrupted!"));
            return false;
        }

        inputItem = item;
        hasRerolled = false;

        // Update GUI - show proceed button
        updateGui();
        return true;
    }

    private void updateGui() {
        // Show input item
        inventory.setItem(SLOT_INPUT, inputItem);

        // Check balance and materials
        boolean hasBalance = plugin.getVaultIntegration().hasBalance(player, COST);
        boolean hasMaterials = plugin.getPouchIntegration().hasItem(player, MATERIAL_ID, MATERIAL_AMOUNT);
        String costDisplay = plugin.getVaultIntegration().formatCompact(COST);
        int currentMaterials = plugin.getPouchIntegration().getItemAmount(player, MATERIAL_ID);

        // Proceed button - NO PREVIEW, just proceed
        if (hasBalance && hasMaterials) {
            inventory.setItem(SLOT_PROCEED, createItem(Material.LIME_CONCRETE, "&a&lCorrupt Item",
                "&7Apply corruption immediately.",
                "",
                "&c&lWARNING: Result is RANDOM!",
                "&7You will NOT see a preview.",
                "&7The result is FINAL!",
                "",
                "&eCost: &c" + costDisplay,
                "&eMaterial: &c" + MATERIAL_AMOUNT + "x &6Blacksmith Scrap",
                "&7You have: &a" + currentMaterials,
                "",
                "&aClick to corrupt!"));
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
                lore.add("&c✗ &7Material: &c" + MATERIAL_AMOUNT + "x &6Blacksmith Scrap");
                lore.add("  &7You have: &c" + currentMaterials);
            } else {
                lore.add("&a✓ &7Material: &a" + MATERIAL_AMOUNT + "x &6Blacksmith Scrap");
            }
            inventory.setItem(SLOT_PROCEED, createItem(Material.GRAY_CONCRETE, "&c&lMissing Requirements",
                lore.toArray(new String[0])));
        }

        updateRerollStoneInfo();
    }

    private void updateRerollStoneInfo() {
        if (hasSmithStone && !hasRerolled) {
            inventory.setItem(SLOT_REROLL_STONE, createItem(Material.STONE, "&6&lReroll Available!",
                "&7You have a Corrupted Smithing Stone!",
                "",
                "&7If you get a bad result,",
                "&7you can use it to reroll ONCE.",
                "",
                "&eStone will be consumed on reroll."));
        } else if (hasRerolled) {
            inventory.setItem(SLOT_REROLL_STONE, createItem(Material.BARRIER, "&c&lReroll Used",
                "&7You already used your reroll."));
        } else {
            inventory.setItem(SLOT_REROLL_STONE, createItem(Material.GRAY_STAINED_GLASS_PANE, "&7&lNo Reroll Stone",
                "&7Get a Corrupted Smithing Stone",
                "&7for a chance to reroll."));
        }
    }

    public void handleProceed() {
        if (inputItem == null) return;

        // Check balance
        if (!plugin.getVaultIntegration().hasBalance(player, COST)) {
            player.sendMessage(LEGACY.deserialize("&c&lCorrupted Blacksmith: &7You don't have enough money!"));
            return;
        }

        // Check materials from pouch
        if (!plugin.getPouchIntegration().hasItem(player, MATERIAL_ID, MATERIAL_AMOUNT)) {
            player.sendMessage(LEGACY.deserialize("&c&lCorrupted Blacksmith: &7You don't have enough Blacksmith Scrap!"));
            return;
        }

        // Withdraw money
        if (!plugin.getVaultIntegration().withdraw(player, COST)) {
            player.sendMessage(LEGACY.deserialize("&c&lCorrupted Blacksmith: &7Transaction failed!"));
            return;
        }

        // Remove materials from pouch
        if (!plugin.getPouchIntegration().removeItem(player, MATERIAL_ID, MATERIAL_AMOUNT)) {
            // Refund money
            plugin.getVaultIntegration().deposit(player, COST);
            player.sendMessage(LEGACY.deserialize("&c&lCorrupted Blacksmith: &7Failed to consume materials!"));
            return;
        }

        // Store the original item for potential reroll
        ItemStack originalItem = inputItem.clone();

        // Roll corruption and apply immediately
        int corruptionType = random.nextInt(4);
        ItemManipulator manipulator = plugin.getItemManipulator();
        ItemStack result;

        switch (corruptionType) {
            case 0: // Upgrade stat by 30%
                result = manipulator.upgradeRandomStat(inputItem, 0.30);
                player.sendMessage(LEGACY.deserialize("&4&lCorrupted Blacksmith: &a+30% to a random stat!"));
                break;
            case 1: // Remove random stat
                result = manipulator.removeRandomStat(inputItem);
                player.sendMessage(LEGACY.deserialize("&4&lCorrupted Blacksmith: &eA stat was removed."));
                break;
            case 2: // Add new stat
                result = manipulator.addRandomStat(inputItem);
                player.sendMessage(LEGACY.deserialize("&4&lCorrupted Blacksmith: &bA new stat was added!"));
                break;
            case 3: // Remove ALL stats
            default:
                result = manipulator.removeAllStats(inputItem);
                player.sendMessage(LEGACY.deserialize("&4&lCorrupted Blacksmith: &c&lALL stats removed!"));
                break;
        }

        // IMPORTANT: Clear inputItem BEFORE closing to prevent duplication in handleClose()
        inputItem = null;

        // Check if player has reroll stone and can reroll
        if (hasSmithStone && !hasRerolled) {
            // Show result and ask if they want to reroll
            // Store original for reroll capability
            this.originalItemForReroll = originalItem;
            showRerollOption(result, corruptionType);
        } else {
            // Give result to player and close
            player.getInventory().addItem(result);
            player.sendMessage(LEGACY.deserialize("&4&lCorrupted Blacksmith: &7Your item has been corrupted!"));
            player.closeInventory();
        }
    }

    private void showRerollOption(ItemStack result, int corruptionType) {
        // Store result item for accept/reroll handling
        this.resultItem = result;

        // Clear GUI
        ItemStack blackPane = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 9; i < 45; i++) {
            inventory.setItem(i, blackPane);
        }

        // Show result
        inventory.setItem(20, result);

        // Show corruption type
        String outcomeText;
        switch (corruptionType) {
            case 0:
                outcomeText = "&a+30% to a random stat";
                break;
            case 1:
                outcomeText = "&eA stat was removed";
                break;
            case 2:
                outcomeText = "&bA new stat was added";
                break;
            default:
                outcomeText = "&c&lALL stats removed!";
                break;
        }

        inventory.setItem(SLOT_INFO, createItem(Material.PAPER, "&e&lCorruption Result",
            "&7Result: " + outcomeText,
            "",
            "&7You can accept this result",
            "&7or use your Smithing Stone",
            "&7to reroll ONCE."));

        // Accept button
        inventory.setItem(22, createItem(Material.LIME_CONCRETE, "&a&lAccept Result",
            "&7Take this corrupted item.",
            "",
            "&aClick to accept!"));

        // Reroll button
        inventory.setItem(24, createItem(Material.ORANGE_CONCRETE, "&6&lReroll (Use Stone)",
            "&7Use your Smithing Stone",
            "&7to try a different outcome.",
            "",
            "&c&lThis consumes the stone!",
            "",
            "&6Click to reroll!"));
    }

    public void handleAcceptResult() {
        if (resultItem == null) return;

        player.getInventory().addItem(resultItem);
        player.sendMessage(LEGACY.deserialize("&4&lCorrupted Blacksmith: &7Your item has been corrupted!"));

        // Clear state before closing
        resultItem = null;
        originalItemForReroll = null;

        player.closeInventory();
    }

    public void handleReroll() {
        if (originalItemForReroll == null || hasRerolled || !hasSmithStone) return;

        // Remove smith stone
        if (!plugin.getMythicMobsIntegration().removeItem(player, "smith_stone", 1)) {
            player.sendMessage(LEGACY.deserialize("&c&lCorrupted Blacksmith: &7Failed to consume smithing stone!"));
            return;
        }

        hasRerolled = true;
        hasSmithStone = false;

        // Roll NEW corruption on the ORIGINAL item (not the already corrupted one)
        int corruptionType = random.nextInt(4);
        ItemManipulator manipulator = plugin.getItemManipulator();
        ItemStack newResult;

        switch (corruptionType) {
            case 0: // Upgrade stat by 30%
                newResult = manipulator.upgradeRandomStat(originalItemForReroll, 0.30);
                player.sendMessage(LEGACY.deserialize("&6&lReroll: &a+30% to a random stat!"));
                break;
            case 1: // Remove random stat
                newResult = manipulator.removeRandomStat(originalItemForReroll);
                player.sendMessage(LEGACY.deserialize("&6&lReroll: &eA stat was removed."));
                break;
            case 2: // Add new stat
                newResult = manipulator.addRandomStat(originalItemForReroll);
                player.sendMessage(LEGACY.deserialize("&6&lReroll: &bA new stat was added!"));
                break;
            case 3: // Remove ALL stats
            default:
                newResult = manipulator.removeAllStats(originalItemForReroll);
                player.sendMessage(LEGACY.deserialize("&6&lReroll: &c&lALL stats removed!"));
                break;
        }

        player.sendMessage(LEGACY.deserialize("&6&lCorrupted Blacksmith: &7You used a Corrupted Smithing Stone!"));
        player.getInventory().addItem(newResult);

        // Clear state before closing
        resultItem = null;
        originalItemForReroll = null;

        player.closeInventory();
    }

    public void handleClose() {
        // Return input item if exists (only if not processed yet)
        if (inputItem != null) {
            returnItemToPlayer(inputItem);
            inputItem = null;
        }

        // If in reroll state, give the result item to player (they closed during reroll decision)
        if (resultItem != null) {
            returnItemToPlayer(resultItem);
            resultItem = null;
        }

        // Clear other state
        originalItemForReroll = null;
    }

    private void returnItemToPlayer(ItemStack item) {
        if (item == null || item.getType().isAir()) return;

        if (player.isOnline()) {
            var leftover = player.getInventory().addItem(item);
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        } else {
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
        lore.add("&eMaterial: &c" + MATERIAL_AMOUNT + "x &6Blacksmith Scrap");
        lore.add("");
        if (hasSmithStone) {
            lore.add("&6You have a Smithing Stone!");
            lore.add("&7Use it for one reroll.");
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

    public int getProceedSlot() {
        return SLOT_PROCEED;
    }

    public boolean isInRerollState() {
        // Check if we're in the reroll decision state
        ItemStack acceptButton = inventory.getItem(22);
        return acceptButton != null && acceptButton.getType() == Material.LIME_CONCRETE;
    }
}
