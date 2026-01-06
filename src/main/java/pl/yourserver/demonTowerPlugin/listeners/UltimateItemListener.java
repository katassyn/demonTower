package pl.yourserver.demonTowerPlugin.listeners;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import pl.yourserver.demonTowerPlugin.DemonTowerPlugin;
import pl.yourserver.demonTowerPlugin.mechanics.ItemManipulator;
import pl.yourserver.demonTowerPlugin.mechanics.ItemState;

/**
 * Prevents players from equipping more than one ULTIMATE item at a time.
 * This includes armor, weapons, and off-hand items.
 */
public class UltimateItemListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private final DemonTowerPlugin plugin;

    public UltimateItemListener(DemonTowerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        // Check if clicking on armor/equipment slots
        int slot = event.getRawSlot();
        InventoryType.SlotType slotType = event.getSlotType();

        // We need to check armor slot clicks and shift-clicks
        boolean isArmorSlot = slotType == InventoryType.SlotType.ARMOR;
        boolean isOffHandSlot = slot == 45; // Off-hand slot in player inventory

        ItemStack clickedItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();

        // Case 1: Placing an ULTIMATE item into armor/offhand slot
        if ((isArmorSlot || isOffHandSlot) && cursorItem != null && !cursorItem.getType().isAir()) {
            if (isUltimate(cursorItem)) {
                if (hasOtherUltimateEquipped(player, slot)) {
                    event.setCancelled(true);
                    sendWarning(player);
                    return;
                }
            }
        }

        // Case 2: Shift-clicking an ULTIMATE item (will try to equip to armor slot)
        if (event.isShiftClick() && clickedItem != null && !clickedItem.getType().isAir()) {
            if (isUltimate(clickedItem)) {
                // Check if it could go to an armor slot
                if (isArmorItem(clickedItem) && hasAnyUltimateEquipped(player)) {
                    // The item being shifted is ULTIMATE and player already has one equipped
                    event.setCancelled(true);
                    sendWarning(player);
                    return;
                }
            }
        }

        // Case 3: Swapping items (clicking armor slot with item in hand)
        if (isArmorSlot && cursorItem != null && !cursorItem.getType().isAir()) {
            if (isUltimate(cursorItem)) {
                // Check if removing the current armor reveals another ULTIMATE
                int ultimateCount = countUltimateEquipped(player);
                if (clickedItem != null && isUltimate(clickedItem)) {
                    // Swapping ULTIMATE for ULTIMATE is fine
                    return;
                }
                if (ultimateCount > 0) {
                    event.setCancelled(true);
                    sendWarning(player);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHand = event.getMainHandItem();
        ItemStack offHand = event.getOffHandItem();

        // If swapping would result in 2 ULTIMATE items being held/equipped
        if (mainHand != null && isUltimate(mainHand) && offHand != null && isUltimate(offHand)) {
            // Both items are ULTIMATE - this swap is fine as count stays the same
            return;
        }

        // Check if new arrangement violates the rule
        if (offHand != null && isUltimate(offHand)) {
            // Moving ULTIMATE to main hand, check if any armor is ULTIMATE
            int armorUltimate = countUltimateArmor(player);
            if (armorUltimate > 0) {
                event.setCancelled(true);
                sendWarning(player);
                return;
            }
        }

        if (mainHand != null && isUltimate(mainHand)) {
            // Moving ULTIMATE to off-hand, check if any armor is ULTIMATE
            int armorUltimate = countUltimateArmor(player);
            if (armorUltimate > 0) {
                event.setCancelled(true);
                sendWarning(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onItemHeldChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        int newSlot = event.getNewSlot();

        ItemStack newItem = player.getInventory().getItem(newSlot);
        if (newItem == null || !isUltimate(newItem)) return;

        // Player is switching to an ULTIMATE weapon
        // Check if they have any other ULTIMATE equipped
        if (hasAnyUltimateEquipped(player, newSlot)) {
            event.setCancelled(true);
            sendWarning(player);
        }
    }

    private boolean isUltimate(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemManipulator manipulator = plugin.getItemManipulator();
        return manipulator.getItemState(item) == ItemState.ULTIMATE;
    }

    private boolean hasAnyUltimateEquipped(Player player) {
        return countUltimateEquipped(player) > 0;
    }

    private boolean hasAnyUltimateEquipped(Player player, int excludeHotbarSlot) {
        PlayerInventory inv = player.getInventory();

        // Check armor slots
        for (ItemStack armor : inv.getArmorContents()) {
            if (isUltimate(armor)) return true;
        }

        // Check off-hand
        if (isUltimate(inv.getItemInOffHand())) return true;

        // Check main hand (current slot) if different from excluded
        int currentSlot = inv.getHeldItemSlot();
        if (currentSlot != excludeHotbarSlot && isUltimate(inv.getItemInMainHand())) {
            return true;
        }

        return false;
    }

    private boolean hasOtherUltimateEquipped(Player player, int excludeSlot) {
        PlayerInventory inv = player.getInventory();

        // Map raw slot to equipment
        // Armor slots: 5-8 (helmet=5, chest=6, legs=7, boots=8)
        // Off-hand: 45

        ItemStack[] armor = inv.getArmorContents();
        int[] armorRawSlots = {8, 7, 6, 5}; // boots, legs, chest, helmet in array order

        for (int i = 0; i < armor.length; i++) {
            if (armorRawSlots[i] == excludeSlot) continue;
            if (isUltimate(armor[i])) return true;
        }

        // Check off-hand
        if (excludeSlot != 45 && isUltimate(inv.getItemInOffHand())) return true;

        // Check main hand
        if (isUltimate(inv.getItemInMainHand())) return true;

        return false;
    }

    private int countUltimateEquipped(Player player) {
        int count = 0;
        PlayerInventory inv = player.getInventory();

        // Check armor
        for (ItemStack armor : inv.getArmorContents()) {
            if (isUltimate(armor)) count++;
        }

        // Check main hand and off-hand
        if (isUltimate(inv.getItemInMainHand())) count++;
        if (isUltimate(inv.getItemInOffHand())) count++;

        return count;
    }

    private int countUltimateArmor(Player player) {
        int count = 0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (isUltimate(armor)) count++;
        }
        return count;
    }

    private boolean isArmorItem(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        String name = type.name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") ||
               name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS") ||
               name.equals("ELYTRA") || name.equals("SHIELD");
    }

    private void sendWarning(Player player) {
        player.sendMessage(LEGACY.deserialize(
            "&5&lULTIMATE: &cYou can only have ONE ULTIMATE item equipped at a time!"));
    }
}
