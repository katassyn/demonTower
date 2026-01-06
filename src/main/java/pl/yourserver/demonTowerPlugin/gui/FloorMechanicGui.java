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
import pl.yourserver.demonTowerPlugin.game.DemonTowerSession;

import java.util.ArrayList;
import java.util.List;

/**
 * Universal Floor Mechanic GUI - shows current floor mechanic and advance option
 * Opens with /dt gui (no parameters needed)
 */
public class FloorMechanicGui {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    public static final String TITLE = "&6&lDemon Tower";

    private final DemonTowerPlugin plugin;
    private final Player player;
    private Inventory inventory;
    private int completedFloor;

    // Slot positions
    public static final int SLOT_MECHANIC = 20;      // Floor mechanic button
    public static final int SLOT_INFO = 4;            // Info paper with description
    public static final int SLOT_ADVANCE = 24;        // Advance to next floor
    public static final int SLOT_STATUS = 40;         // Current status/timer

    public FloorMechanicGui(DemonTowerPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        DemonTowerSession session = plugin.getSessionManager().getCurrentSession();
        if (session == null || !session.hasPlayer(player)) {
            player.sendMessage(LEGACY.deserialize("&cNie jestes w Demon Tower!"));
            return;
        }

        if (!session.canUseMechanics()) {
            player.sendMessage(LEGACY.deserialize("&cNie mozesz teraz uzyc mechanik! Najpierw pokonaj bossa."));
            return;
        }

        // Determine completed floor (current floor if floor completed, or current-1 if transitioning)
        completedFloor = session.getCurrentFloor();

        inventory = Bukkit.createInventory(null, 45, LEGACY.deserialize(TITLE));

        // Fill background
        ItemStack orangePane = createItem(Material.ORANGE_STAINED_GLASS_PANE, " ");
        ItemStack grayPane = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 45; i++) {
            if (i < 9 || i >= 36) {
                inventory.setItem(i, grayPane);
            } else {
                inventory.setItem(i, orangePane);
            }
        }

        // Info paper with detailed mechanic description
        inventory.setItem(SLOT_INFO, createMechanicInfoPaper(completedFloor));

        // Mechanic button
        addMechanicButton(completedFloor);

        // Advance button
        addAdvanceButton(session);

        // Status display
        addStatusDisplay(session);

        player.openInventory(inventory);
    }

    private void addMechanicButton(int floor) {
        switch (floor) {
            case 1: // Corrupted Blacksmith
                inventory.setItem(SLOT_MECHANIC, createItem(Material.ANVIL, "&4&lCorrupted Blacksmith",
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
                    "&a>>> Click to open <<<"));
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
                    "&a>>> Click to open <<<"));
                break;
            case 3: // Divine Source
                inventory.setItem(SLOT_MECHANIC, createItem(Material.BEACON, "&9&lDivine Source",
                    "&8━━━━━━━━━━━━━━━━━━━━",
                    "&7Sanctify an accessory!",
                    "",
                    "&b+50% &7to one random stat",
                    "&b+25% &7to all other stats",
                    "",
                    "&eCost: &c" + plugin.getVaultIntegration().formatCompact(DivineSourceGui.COST),
                    "&8━━━━━━━━━━━━━━━━━━━━",
                    "",
                    "&a>>> Click to open <<<"));
                break;
            case 4: // Infernal Crucible
                inventory.setItem(SLOT_MECHANIC, createItem(Material.SMITHING_TABLE, "&5&lInfernal Crucible",
                    "&8━━━━━━━━━━━━━━━━━━━━",
                    "&7Fuse two identical items",
                    "&7to create an ULTIMATE item!",
                    "",
                    "&d2x &7all stats on result",
                    "",
                    "&eCost: &c" + plugin.getVaultIntegration().formatCompact(InfernalCrucibleGui.COST),
                    "&8━━━━━━━━━━━━━━━━━━━━",
                    "",
                    "&a>>> Click to open <<<"));
                break;
            default:
                inventory.setItem(SLOT_MECHANIC, createItem(Material.BARRIER, "&c&lNo Mechanic",
                    "&7This floor has no mechanic."));
                break;
        }
    }

    private ItemStack createMechanicInfoPaper(int floor) {
        List<String> lore = new ArrayList<>();
        lore.add("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        lore.add("");

        switch (floor) {
            case 1: // Corrupted Blacksmith
                lore.add("&4&lCORRUPTED BLACKSMITH");
                lore.add("");
                lore.add("&7This ancient forge is tainted");
                lore.add("&7by demonic corruption.");
                lore.add("");
                lore.add("&e&lHOW IT WORKS:");
                lore.add("&71. Place any equipment item");
                lore.add("&72. Preview the corruption result");
                lore.add("&73. Confirm or cancel");
                lore.add("");
                lore.add("&c&lPOSSIBLE OUTCOMES (25% each):");
                lore.add("&a+ Upgrade &7- One stat gains +30%");
                lore.add("&e- Remove &7- One random stat removed");
                lore.add("&b+ Add &7- New random stat added");
                lore.add("&4! Destroy &7- ALL stats removed!");
                lore.add("");
                lore.add("&d&lCORRUPTED SMITHING STONE:");
                lore.add("&7Drops from mobs in Demon Tower.");
                lore.add("&7Use it to REROLL the result once!");
                lore.add("&7(before confirming)");
                lore.add("");
                lore.add("&7Protection stat is &aNEVER&7 affected.");
                lore.add("&cCORRUPTED items cannot be modified!");
                break;

            case 2: // Demonic Smelter
                lore.add("&6&lDEMONIC SMELTER");
                lore.add("");
                lore.add("&7This hellish furnace can fuse");
                lore.add("&7two items into one.");
                lore.add("");
                lore.add("&e&lHOW IT WORKS:");
                lore.add("&71. Place first equipment item");
                lore.add("&72. Place second equipment item");
                lore.add("&73. Both must be SAME ITEM TYPE");
                lore.add("&74. Result randomly picks stats");
                lore.add("&7   from both source items");
                lore.add("");
                lore.add("&c&lRULES:");
                lore.add("&7- Items must be same type");
                lore.add("&7- CORRUPTED items not allowed");
                lore.add("&7- ULTIMATE items not allowed");
                lore.add("&7- Both items are consumed!");
                lore.add("");
                lore.add("&a&lTIP: &7Use items with different");
                lore.add("&7stats for best results!");
                break;

            case 3: // Divine Source
                lore.add("&9&lDIVINE SOURCE");
                lore.add("");
                lore.add("&7A holy spring of divine power");
                lore.add("&7that blesses accessories.");
                lore.add("");
                lore.add("&e&lHOW IT WORKS:");
                lore.add("&71. Place an accessory item");
                lore.add("&72. Divine blessing is applied");
                lore.add("&73. All stats are enhanced!");
                lore.add("");
                lore.add("&b&lBLESSING EFFECTS:");
                lore.add("&a+50% &7to ONE random stat");
                lore.add("&a+25% &7to ALL other stats");
                lore.add("");
                lore.add("&c&lRULES:");
                lore.add("&7- ONLY accessories allowed!");
                lore.add("&7- Boss Souls NOT allowed");
                lore.add("&7- CORRUPTED items not allowed");
                lore.add("&7- ULTIMATE items not allowed");
                lore.add("&7- Can only bless ONCE!");
                break;

            case 4: // Infernal Crucible
                lore.add("&5&lINFERNAL CRUCIBLE");
                lore.add("");
                lore.add("&7The ultimate forge powered by");
                lore.add("&7the flames of the abyss.");
                lore.add("");
                lore.add("&e&lHOW IT WORKS:");
                lore.add("&71. Place first equipment item");
                lore.add("&72. Place IDENTICAL second item");
                lore.add("&73. Items are fused into ULTIMATE!");
                lore.add("");
                lore.add("&d&lULTIMATE ITEM EFFECTS:");
                lore.add("&a2x &7ALL stats doubled!");
                lore.add("&5&lULTIMATE &7tag added to name");
                lore.add("");
                lore.add("&c&lRULES:");
                lore.add("&7- Items must be IDENTICAL");
                lore.add("&7  (same base item, same stats)");
                lore.add("&7- Both items consumed!");
                lore.add("&7- ULTIMATE items not allowed");
                lore.add("&7- CORRUPTED items not allowed");
                lore.add("");
                lore.add("&4&lWARNING: &7You can only EQUIP");
                lore.add("&7ONE Ultimate item at a time!");
                break;

            default:
                lore.add("&7No mechanic info available.");
                break;
        }

        lore.add("");
        lore.add("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        return createItem(Material.PAPER, "&e&lMechanic Information", lore.toArray(new String[0]));
    }

    private void addAdvanceButton(DemonTowerSession session) {
        FloorConfig nextFloor = plugin.getConfigManager().getFloor(completedFloor + 1);

        if (nextFloor == null) {
            // No next floor - this was the final floor!
            inventory.setItem(SLOT_ADVANCE, createItem(Material.NETHER_STAR, "&6&lVICTORY!",
                "&7You have completed all floors!",
                "",
                "&aClose this GUI when ready."));
            return;
        }

        boolean transitionInProgress = session.isFloorTransitionInProgress();

        if (transitionInProgress) {
            // Show countdown
            inventory.setItem(SLOT_ADVANCE, createItem(Material.CLOCK, "&6&lTransition In Progress",
                "&8━━━━━━━━━━━━━━━━━━━━",
                "&7Someone already started transition!",
                "",
                "&eTime remaining: &c" + session.getFloorTransitionRemaining() + "s",
                "",
                "&7All players will be moved to",
                "&7Floor " + (completedFloor + 1) + " when time expires.",
                "&8━━━━━━━━━━━━━━━━━━━━"));
        } else {
            // Show advance button
            boolean hasLevel = player.getLevel() >= nextFloor.getRequiredLevel();
            boolean hasKey = !nextFloor.requiresKey() ||
                plugin.getMythicMobsIntegration().hasItem(player, nextFloor.getRequiredKey());
            boolean canAdvance = hasLevel && hasKey;

            List<String> lore = new ArrayList<>();
            lore.add("&8━━━━━━━━━━━━━━━━━━━━");
            lore.add("");
            lore.add("&7Click to start &e60-second &7countdown");
            lore.add("&7to advance to Floor " + (completedFloor + 1));
            lore.add("");
            lore.add("&eRequirements:");
            String levelStatus = hasLevel ? "&a\u2714" : "&c\u2718";
            lore.add(levelStatus + " &7Level: &e" + nextFloor.getRequiredLevel() + " &7(You: &a" + player.getLevel() + "&7)");

            if (nextFloor.requiresKey()) {
                String keyStatus = hasKey ? "&a\u2714" : "&c\u2718";
                lore.add(keyStatus + " &7Key: &e" + nextFloor.getRequiredKey());
            }

            lore.add("");
            if (canAdvance) {
                lore.add("&a&l>>> Click to start transition <<<");
                lore.add("");
                lore.add("&7All players will be moved after 60s!");
            } else {
                lore.add("&cYou don't meet requirements!");
            }
            lore.add("&8━━━━━━━━━━━━━━━━━━━━");

            Material mat = canAdvance ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
            String name = canAdvance ? "&a&lAdvance to Floor " + (completedFloor + 1) : "&c&lCannot Advance";

            inventory.setItem(SLOT_ADVANCE, createItem(mat, name, lore.toArray(new String[0])));
        }
    }

    private void addStatusDisplay(DemonTowerSession session) {
        int timeRemaining = session.getMechanicTimeRemaining();

        List<String> lore = new ArrayList<>();
        lore.add("&8━━━━━━━━━━━━━━━━━━━━");
        lore.add("");
        lore.add("&7Current Floor: &e" + completedFloor);
        lore.add("&7Status: &aFloor Completed!");
        lore.add("");
        lore.add("&c&lTIME LIMIT: &e" + formatTime(timeRemaining));
        lore.add("");
        lore.add("&7Use the mechanic or advance");
        lore.add("&7before time runs out!");
        lore.add("");
        lore.add("&4If time expires, all players");
        lore.add("&4will be teleported to spawn!");
        lore.add("&8━━━━━━━━━━━━━━━━━━━━");

        inventory.setItem(SLOT_STATUS, createItem(Material.CLOCK, "&e&lTime Remaining", lore.toArray(new String[0])));
    }

    private String formatTime(int seconds) {
        int min = seconds / 60;
        int sec = seconds % 60;
        return String.format("%d:%02d", min, sec);
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

    public int getCompletedFloor() {
        return completedFloor;
    }
}
