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
    public static final int SLOT_PROCEED = 24;         // Proceed button
    public static final int SLOT_INFO = 4;             // Info display

    // State
    private ItemStack inputItem = null;

    // Cost
    public static final double COST = 300_000_000;     // 300m$
    public static final String MATERIAL_ID = "divine_droplet";
    public static final int MATERIAL_AMOUNT = 10;

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
            "&7(No Boss Souls)",
            "",
            "&e+50% &7to one random stat",
            "&e+25% &7to all other stats",
            "",
            "&eClick or drag item here"));

        // Arrow indicator
        inventory.setItem(22, createItem(Material.NETHER_STAR, "&b&l>>>", "&7Sanctify accessory"));

        // Proceed button (disabled initially)
        inventory.setItem(SLOT_PROCEED, createItem(Material.GRAY_CONCRETE, "&7&lWaiting for accessory...",
            "&7Place an accessory to sanctify."));

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
            player.sendMessage(LEGACY.deserialize("&9&lDivine Source: &7You must place a single item, not a stack! Unstack your items first."));
            return false; // Item stays in player inventory
        }

        // Check if it's an accessory
        if (!manipulator.isAccessory(item)) {
            player.sendMessage(LEGACY.deserialize("&9&lDivine Source: &7Only accessories can be sanctified!"));
            return false;
        }

        // Check if it's a boss soul
        if (manipulator.isBossSoul(item)) {
            player.sendMessage(LEGACY.deserialize("&9&lDivine Source: &7Boss Souls cannot be sanctified!"));
            return false;
        }

        ItemState state = manipulator.getItemState(item);

        // Check restrictions
        if (state == ItemState.CORRUPTED) {
            player.sendMessage(LEGACY.deserialize("&9&lDivine Source: &7CORRUPTED items cannot be sanctified!"));
            return false;
        }

        if (state == ItemState.SANCTIFIED) {
            player.sendMessage(LEGACY.deserialize("&9&lDivine Source: &7This item is already sanctified!"));
            return false;
        }

        if (state == ItemState.ULTIMATE) {
            player.sendMessage(LEGACY.deserialize("&9&lDivine Source: &7ULTIMATE items cannot be sanctified!"));
            return false;
        }

        inputItem = item;
        updateGui();
        return true; // Item accepted
    }

    private void updateGui() {
        // Show input item
        inventory.setItem(SLOT_INPUT, inputItem);

        // Check balance and materials
        boolean hasBalance = plugin.getVaultIntegration().hasBalance(player, COST);
        boolean hasMaterials = plugin.getPouchIntegration().hasItem(player, MATERIAL_ID, MATERIAL_AMOUNT);
        String costDisplay = plugin.getVaultIntegration().formatCompact(COST);
        int currentMaterials = plugin.getPouchIntegration().getItemAmount(player, MATERIAL_ID);

        if (hasBalance && hasMaterials) {
            inventory.setItem(SLOT_PROCEED, createItem(Material.LIME_CONCRETE, "&a&lSanctify Accessory",
                "&7Bless this accessory with divine power.",
                "",
                "&e+50% to one random stat!",
                "&e+25% to all other stats!",
                "",
                "&eCost: &c" + costDisplay,
                "&eMaterial: &c" + MATERIAL_AMOUNT + "x &6Divine Droplet",
                "&7You have: &a" + currentMaterials,
                "",
                "&aClick to sanctify!"));
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
                lore.add("&c✗ &7Material: &c" + MATERIAL_AMOUNT + "x &6Divine Droplet");
                lore.add("  &7You have: &c" + currentMaterials);
            } else {
                lore.add("&a✓ &7Material: &a" + MATERIAL_AMOUNT + "x &6Divine Droplet");
            }
            inventory.setItem(SLOT_PROCEED, createItem(Material.GRAY_CONCRETE, "&c&lMissing Requirements",
                lore.toArray(new String[0])));
        }
    }

    public void handleProceed() {
        if (inputItem == null) return;

        // Check balance
        if (!plugin.getVaultIntegration().hasBalance(player, COST)) {
            player.sendMessage(LEGACY.deserialize("&9&lDivine Source: &7You don't have enough money!"));
            return;
        }

        // Check materials from pouch
        if (!plugin.getPouchIntegration().hasItem(player, MATERIAL_ID, MATERIAL_AMOUNT)) {
            player.sendMessage(LEGACY.deserialize("&9&lDivine Source: &7You don't have enough Divine Droplets!"));
            return;
        }

        // Withdraw money
        if (!plugin.getVaultIntegration().withdraw(player, COST)) {
            player.sendMessage(LEGACY.deserialize("&9&lDivine Source: &7Transaction failed!"));
            return;
        }

        // Remove materials from pouch
        if (!plugin.getPouchIntegration().removeItem(player, MATERIAL_ID, MATERIAL_AMOUNT)) {
            // Refund money
            plugin.getVaultIntegration().deposit(player, COST);
            player.sendMessage(LEGACY.deserialize("&9&lDivine Source: &7Failed to consume materials!"));
            return;
        }

        // Apply sanctification
        ItemManipulator manipulator = plugin.getItemManipulator();
        ItemStack result = manipulator.sanctifyAccessory(inputItem);

        // IMPORTANT: Clear state BEFORE closing to prevent duplication in handleClose()
        inputItem = null;

        // Give sanctified item to player
        player.getInventory().addItem(result);

        player.sendMessage(LEGACY.deserialize("&9&lDivine Source: &7Your accessory has been blessed by divine power!"));
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
        lore.add("&eMaterial: &c" + MATERIAL_AMOUNT + "x &6Divine Droplet");
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

    public ItemStack getInputItem() {
        return inputItem;
    }

    public int getInputSlot() {
        return SLOT_INPUT;
    }

    public int getProceedSlot() {
        return SLOT_PROCEED;
    }
}
