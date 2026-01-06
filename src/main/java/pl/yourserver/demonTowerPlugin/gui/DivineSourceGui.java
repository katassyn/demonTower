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

public class DivineSourceGui {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    public static final String TITLE = "&9&lDivine Source";

    private final DemonTowerPlugin plugin;
    private final Player player;
    private Inventory inventory;

    // Slot positions
    public static final int SLOT_INPUT = 20;           // Accessory to sanctify
    public static final int SLOT_PREVIEW = 24;         // Preview of result
    public static final int SLOT_CONFIRM = 38;         // Confirm button
    public static final int SLOT_CANCEL = 42;          // Cancel button
    public static final int SLOT_INFO = 4;             // Info display

    // State
    private ItemStack inputItem = null;
    private ItemStack previewItem = null;

    // Cost
    public static final double COST = 300_000_000;     // 300m$

    public DivineSourceGui(DemonTowerPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        inventory = Bukkit.createInventory(null, 54, LEGACY.deserialize(TITLE));

        // Fill background
        ItemStack bluePane = createItem(Material.BLUE_STAINED_GLASS_PANE, " ");
        ItemStack lightBluePane = createItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ");

        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45) {
                inventory.setItem(i, bluePane);
            } else {
                inventory.setItem(i, lightBluePane);
            }
        }

        // Info display
        inventory.setItem(SLOT_INFO, createInfoItem());

        // Input slot marker
        inventory.setItem(SLOT_INPUT, createItem(Material.BEACON, "&9&lPlace Accessory Here",
            "&7Place an accessory to sanctify.",
            "",
            "&7Only accessories can be blessed!",
            "&7(No Boss Souls)"));

        // Arrow indicator
        inventory.setItem(22, createItem(Material.NETHER_STAR, "&b&l>>>", "&7Sanctify accessory"));

        // Preview slot
        inventory.setItem(SLOT_PREVIEW, createItem(Material.BARRIER, "&7&lNo Preview",
            "&7Place an accessory first."));

        // Cancel button
        inventory.setItem(SLOT_CANCEL, createItem(Material.RED_CONCRETE, "&c&lCancel",
            "&7Close without changes."));

        // Confirm button (disabled initially)
        inventory.setItem(SLOT_CONFIRM, createItem(Material.GRAY_CONCRETE, "&7&lWaiting for accessory...",
            "&7Place an accessory to sanctify."));

        player.openInventory(inventory);
    }

    public void handleItemPlace(ItemStack item) {
        ItemManipulator manipulator = plugin.getItemManipulator();

        // Check if it's an accessory
        if (!manipulator.isAccessory(item)) {
            player.sendMessage(LEGACY.deserialize("&9&lDivine Source: &7Only accessories can be sanctified!"));
            returnItemToPlayer(item);
            return;
        }

        // Check if it's a boss soul
        if (manipulator.isBossSoul(item)) {
            player.sendMessage(LEGACY.deserialize("&9&lDivine Source: &7Boss Souls cannot be sanctified!"));
            returnItemToPlayer(item);
            return;
        }

        ItemState state = manipulator.getItemState(item);

        // Check restrictions
        if (state == ItemState.CORRUPTED) {
            player.sendMessage(LEGACY.deserialize("&9&lDivine Source: &7CORRUPTED items cannot be sanctified!"));
            returnItemToPlayer(item);
            return;
        }

        if (state == ItemState.SANCTIFIED) {
            player.sendMessage(LEGACY.deserialize("&9&lDivine Source: &7This item is already sanctified!"));
            returnItemToPlayer(item);
            return;
        }

        if (state == ItemState.ULTIMATE) {
            player.sendMessage(LEGACY.deserialize("&9&lDivine Source: &7ULTIMATE items cannot be sanctified!"));
            returnItemToPlayer(item);
            return;
        }

        inputItem = item;

        // Generate preview
        previewItem = manipulator.sanctifyAccessory(item);

        updateGui();
    }

    private void updateGui() {
        // Show input item
        inventory.setItem(SLOT_INPUT, inputItem != null ? inputItem :
            createItem(Material.BEACON, "&9&lPlace Accessory Here",
                "&7Place an accessory to sanctify.",
                "",
                "&7Only accessories can be blessed!",
                "&7(No Boss Souls)"));

        if (previewItem != null) {
            inventory.setItem(SLOT_PREVIEW, previewItem);

            // Check balance
            boolean hasBalance = plugin.getVaultIntegration().hasBalance(player, COST);
            String costDisplay = plugin.getVaultIntegration().formatCompact(COST);

            if (hasBalance) {
                inventory.setItem(SLOT_CONFIRM, createItem(Material.LIME_CONCRETE, "&a&lConfirm Sanctification",
                    "&7Bless this accessory.",
                    "",
                    "&e+50% to one random stat!",
                    "&e+25% to all other stats!",
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
            inventory.setItem(SLOT_PREVIEW, createItem(Material.BARRIER, "&7&lNo Preview",
                "&7Place an accessory first."));
            inventory.setItem(SLOT_CONFIRM, createItem(Material.GRAY_CONCRETE, "&7&lWaiting for accessory...",
                "&7Place an accessory to sanctify."));
        }
    }

    public void handleConfirm() {
        if (inputItem == null || previewItem == null) return;

        // Check balance
        if (!plugin.getVaultIntegration().hasBalance(player, COST)) {
            player.sendMessage(LEGACY.deserialize("&9&lDivine Source: &7You don't have enough money!"));
            return;
        }

        // Withdraw money
        if (!plugin.getVaultIntegration().withdraw(player, COST)) {
            player.sendMessage(LEGACY.deserialize("&9&lDivine Source: &7Transaction failed!"));
            return;
        }

        // Give sanctified item to player
        player.getInventory().addItem(previewItem);

        player.sendMessage(LEGACY.deserialize("&9&lDivine Source: &7Your accessory has been blessed by divine power!"));
        player.closeInventory();

        // Clear state
        inputItem = null;
        previewItem = null;
    }

    public void handleCancel() {
        // Return input item
        if (inputItem != null) {
            returnItemToPlayer(inputItem);
            inputItem = null;
        }
        player.closeInventory();
    }

    public void handleClose() {
        // Return input item
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
        lore.add("&7The Divine Source blesses");
        lore.add("&7accessories with divine power!");
        lore.add("");
        lore.add("&e&lEffects:");
        lore.add("&b+50% &7to one random stat");
        lore.add("&b+25% &7to all other stats");
        lore.add("");
        lore.add("&c&lRestrictions:");
        lore.add("&7- Accessories only");
        lore.add("&7- No Boss Souls");
        lore.add("&7- No CORRUPTED items");
        lore.add("&7- Smelted items OK");
        lore.add("");
        lore.add("&eCost: &c" + plugin.getVaultIntegration().formatCompact(COST));
        lore.add("&8━━━━━━━━━━━━━━━━━━━━");

        return createItem(Material.BEACON, "&9&lDivine Source", lore.toArray(new String[0]));
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

    public int getInputSlot() {
        return SLOT_INPUT;
    }
}
