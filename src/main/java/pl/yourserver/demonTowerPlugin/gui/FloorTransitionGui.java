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
import pl.yourserver.demonTowerPlugin.config.FloorConfig;

import java.util.ArrayList;
import java.util.List;

public class FloorTransitionGui {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final DemonTowerPlugin plugin;
    private final Player player;
    private final int nextFloor;
    private Inventory inventory;

    public static final int SLOT_CONTINUE = 20;
    public static final int SLOT_EXIT = 24;
    public static final int SLOT_MECHANIC = 40;  // Center bottom for mechanic

    public FloorTransitionGui(DemonTowerPlugin plugin, Player player, int nextFloor) {
        this.plugin = plugin;
        this.player = player;
        this.nextFloor = nextFloor;
    }

    public void open() {
        inventory = Bukkit.createInventory(null, 45,
            LEGACY.deserialize("&6&lAdvance to Floor " + nextFloor));

        // Fill background
        ItemStack background = createItem(Material.ORANGE_STAINED_GLASS_PANE, " ");
        ItemStack darkPane = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 45; i++) {
            if (i < 9 || i >= 36) {
                inventory.setItem(i, darkPane);
            } else {
                inventory.setItem(i, background);
            }
        }

        FloorConfig floor = plugin.getConfigManager().getFloor(nextFloor);
        boolean canAdvance = checkRequirements(floor);

        // Check if transition is already in progress
        var session = plugin.getSessionManager().getCurrentSession();
        boolean transitionInProgress = session != null && session.isFloorTransitionInProgress();

        // Continue button
        if (transitionInProgress) {
            // Show countdown info
            List<String> lore = new ArrayList<>();
            lore.add("&6Transition already initiated!");
            lore.add("");
            lore.add("&7Time remaining: &e" + session.getFloorTransitionRemaining() + "s");
            lore.add("");
            lore.add("&7Use the mechanic button below");
            lore.add("&7before time runs out!");

            inventory.setItem(SLOT_CONTINUE, createItem(Material.CLOCK,
                "&6&lTransition In Progress", lore.toArray(new String[0])));
        } else if (canAdvance) {
            List<String> lore = new ArrayList<>();
            lore.add("&7Click to start &e60-second &7countdown");
            lore.add("&7to advance to floor " + nextFloor);
            lore.add("");
            lore.add("&eRequirements:");
            lore.add("&7- Level: &a" + floor.getRequiredLevel() + " &7(Yours: &a" + player.getLevel() + "&7)");

            if (floor.requiresKey()) {
                boolean hasKey = plugin.getMythicMobsIntegration().hasItem(player, floor.getRequiredKey());
                String keyStatus = hasKey ? "&a" : "&c";
                lore.add("&7- Key: " + keyStatus + floor.getRequiredKey());
            }

            lore.add("");
            lore.add("&e&lAll players will be moved after 60s!");
            lore.add("&a&lClick to initiate transition!");

            inventory.setItem(SLOT_CONTINUE, createItem(Material.LIME_CONCRETE,
                "&a&lStart Transition", lore.toArray(new String[0])));
        } else {
            List<String> lore = new ArrayList<>();
            lore.add("&cYou don't meet the requirements!");
            lore.add("");

            if (player.getLevel() < floor.getRequiredLevel()) {
                lore.add("&c- Need level " + floor.getRequiredLevel());
            }

            if (floor.requiresKey() && !plugin.getMythicMobsIntegration().hasItem(player, floor.getRequiredKey())) {
                lore.add("&c- Missing key: " + floor.getRequiredKey());
            }

            inventory.setItem(SLOT_CONTINUE, createItem(Material.BARRIER,
                "&c&lCannot Advance", lore.toArray(new String[0])));
        }

        // Exit button
        inventory.setItem(SLOT_EXIT, createItem(Material.RED_CONCRETE, "&c&lEnd Session",
            "&7Click to end and exit",
            "",
            "&cWarning: Ends session for everyone!"));

        // Info in center
        inventory.setItem(22, createFloorInfoItem(floor));

        // Mechanic button based on completed floor
        int completedFloor = nextFloor - 1;
        addMechanicButton(completedFloor);

        player.openInventory(inventory);
    }

    private void addMechanicButton(int completedFloor) {
        switch (completedFloor) {
            case 1: // Corrupted Blacksmith
                inventory.setItem(SLOT_MECHANIC, createItem(Material.ANVIL, "&c&lCorrupted Blacksmith",
                    "&8━━━━━━━━━━━━━━━━━━━━",
                    "&7Corrupt your items for",
                    "&7unpredictable results!",
                    "",
                    "&e25% &7- Upgrade stat +30%",
                    "&e25% &7- Remove a stat",
                    "&e25% &7- Add new stat",
                    "&c25% &7- Remove ALL stats!",
                    "",
                    "&eCost: &c" + plugin.getVaultIntegration().formatCompact(CorruptedBlacksmithGui.COST),
                    "&8━━━━━━━━━━━━━━━━━━━━",
                    "",
                    "&6Click to open!"));
                break;
            case 2: // Demonic Smelter
                inventory.setItem(SLOT_MECHANIC, createItem(Material.BLAST_FURNACE, "&6&lDemonic Smelter",
                    "&8━━━━━━━━━━━━━━━━━━━━",
                    "&7Merge two items into one!",
                    "&7Stats randomly selected.",
                    "",
                    "&eCost: &c" + plugin.getVaultIntegration().formatCompact(DemonicSmelterGui.COST),
                    "&8━━━━━━━━━━━━━━━━━━━━",
                    "",
                    "&6Click to open!"));
                break;
            case 3: // Divine Source
                inventory.setItem(SLOT_MECHANIC, createItem(Material.BEACON, "&9&lDivine Source",
                    "&8━━━━━━━━━━━━━━━━━━━━",
                    "&7Sanctify an accessory!",
                    "",
                    "&b+50% &7to one random stat",
                    "&b+25% &7to all other stats",
                    "",
                    "&7Accessories only (no Boss Souls)",
                    "",
                    "&eCost: &c" + plugin.getVaultIntegration().formatCompact(DivineSourceGui.COST),
                    "&8━━━━━━━━━━━━━━━━━━━━",
                    "",
                    "&6Click to open!"));
                break;
            default:
                // No mechanic or floor 4 (which shows at victory)
                inventory.setItem(SLOT_MECHANIC, createItem(Material.GRAY_STAINED_GLASS_PANE, " "));
                break;
        }
    }

    private boolean checkRequirements(FloorConfig floor) {
        if (floor == null) return false;

        // Check level
        if (player.getLevel() < floor.getRequiredLevel()) {
            return false;
        }

        // Check key
        if (floor.requiresKey()) {
            if (!plugin.getMythicMobsIntegration().hasItem(player, floor.getRequiredKey())) {
                return false;
            }
        }

        return true;
    }

    private ItemStack createFloorInfoItem(FloorConfig floor) {
        List<String> lore = new ArrayList<>();
        lore.add("&8━━━━━━━━━━━━━━━━━━━━");
        lore.add("");
        lore.add("&7Required level: &e" + floor.getRequiredLevel());

        if (floor.requiresKey()) {
            lore.add("&7Required key: &e" + floor.getRequiredKey());
        }

        lore.add("");
        lore.add("&7Number of stages: &e" + floor.getStageCount());
        lore.add("");
        lore.add("&8━━━━━━━━━━━━━━━━━━━━");

        return createItem(Material.NETHER_STAR, "&6&lFloor " + nextFloor, lore.toArray(new String[0]));
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

    public int getNextFloor() {
        return nextFloor;
    }
}
