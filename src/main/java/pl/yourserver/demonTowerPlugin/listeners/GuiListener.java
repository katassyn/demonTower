package pl.yourserver.demonTowerPlugin.listeners;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import pl.yourserver.demonTowerPlugin.DemonTowerPlugin;
import pl.yourserver.demonTowerPlugin.config.FloorConfig;
import pl.yourserver.demonTowerPlugin.game.DemonTowerSession;
import pl.yourserver.demonTowerPlugin.gui.*;
import pl.yourserver.demonTowerPlugin.utils.MessageUtils;

public class GuiListener implements Listener {

    private final DemonTowerPlugin plugin;

    public GuiListener(DemonTowerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getInventory();

        // Get inventory title
        String title = PlainTextComponentSerializer.plainText()
            .serialize(event.getView().title());

        if (!plugin.getGuiManager().isGuiInventory(title)) return;

        int slot = event.getRawSlot();

        // Handle Drops Admin Edit GUI FIRST - needs special handling for item manipulation
        if (title.contains("Admin Edit")) {
            handleDropsAdminClick(player, event);
            return;
        }

        // Cancel all clicks in other GUIs (not DropsAdmin which handles its own cancellation)
        event.setCancelled(true);

        // Handle Lobby GUI
        if (title.contains("Lobby")) {
            handleLobbyClick(player, slot, event.getClick());
            return;
        }

        // Handle Admin Floor Selection GUI (must be before general "Demon Tower" check)
        if (title.contains("Admin Drops")) {
            handleAdminFloorSelectionClick(player, event);
            return;
        }

        // Handle Admin Drops Editing GUI
        if (title.contains("Drops Admin") || (title.contains("Admin") && title.contains("Drop"))) {
            handleDropsAdminClick(player, event);
            return;
        }

        // Handle Floor Selection GUI (Drop Preview) - must be before "Demon Tower" check
        if (title.contains("Drop Preview") && !title.contains("Admin")) {
            handleFloorSelectionClick(player, event);
            return;
        }

        // Handle Floor Drops Preview GUI
        if (title.contains("Floor") && title.contains("Drops") && !title.contains("Admin")) {
            handleFloorDropsPreviewClick(player, event);
            return;
        }

        // Handle Info GUI
        if (title.contains("Information")) {
            handleInfoClick(player, slot);
            return;
        }

        // Handle Corrupted Blacksmith GUI
        if (title.contains("Corrupted Blacksmith")) {
            handleCorruptedBlacksmithClick(player, event);
            return;
        }

        // Handle Demonic Smelter GUI
        if (title.contains("Demonic Smelter")) {
            handleDemonicSmelterClick(player, event);
            return;
        }

        // Handle Divine Source GUI
        if (title.contains("Divine Source")) {
            handleDivineSourceClick(player, event);
            return;
        }

        // Handle Infernal Crucible GUI
        if (title.contains("Infernal Crucible")) {
            handleInfernalCrucibleClick(player, event);
            return;
        }

        // Handle Floor Transition GUI
        if (title.contains("Advance")) {
            handleTransitionClick(player, slot, title);
            return;
        }

        // Handle Floor Mechanic GUI (universal) - must be AFTER all specific "Demon Tower" checks
        if (title.contains("Demon Tower") && !title.contains("Drop") && !title.contains("Admin")) {
            handleFloorMechanicClick(player, slot);
            return;
        }
    }

    private void handleLobbyClick(Player player, int slot, ClickType clickType) {
        // Check for right-click or middle-click anywhere in the GUI to open drop preview
        if (clickType == ClickType.RIGHT || clickType == ClickType.MIDDLE) {
            player.closeInventory();
            plugin.getGuiManager().openFloorSelectionGui(player);
            return;
        }

        switch (slot) {
            case LobbyGui.SLOT_JOIN:
                // Join session
                DemonTowerSession session = plugin.getSessionManager().getCurrentSession();
                boolean inSession = session != null && session.hasPlayer(player);

                if (!inSession) {
                    if (plugin.getSessionManager().joinSession(player)) {
                        // Just close GUI and let player play - don't reopen
                        player.closeInventory();
                    }
                    // Error messages are sent by SessionManager.joinSession()
                }
                break;

            case LobbyGui.SLOT_STATUS:
                // Status display - no action, just informational
                break;

            case LobbyGui.SLOT_INFO:
                // Open info GUI
                player.closeInventory();
                plugin.getGuiManager().openInfoGui(player);
                break;
        }
    }

    private void handleTransitionClick(Player player, int slot, String title) {
        DemonTowerSession session = plugin.getSessionManager().getCurrentSession();
        if (session == null) return;

        // Extract floor number from title
        int nextFloor = 2; // Default
        try {
            // Title format: "Advance to Floor X"
            String[] words = title.split(" ");
            for (int i = 0; i < words.length; i++) {
                try {
                    nextFloor = Integer.parseInt(words[i]);
                    break;
                } catch (NumberFormatException ignored) {}
            }
        } catch (Exception ignored) {}

        switch (slot) {
            case FloorTransitionGui.SLOT_CONTINUE:
                // Check if transition already in progress
                if (session.isFloorTransitionInProgress()) {
                    MessageUtils.sendMessage(player, "&cTransition already in progress! Wait " +
                        session.getFloorTransitionRemaining() + " seconds.");
                    player.closeInventory();
                    return;
                }

                // Check requirements
                FloorConfig floor = plugin.getConfigManager().getFloor(nextFloor);
                if (floor == null) return;

                // Check level
                if (player.getLevel() < floor.getRequiredLevel()) {
                    MessageUtils.sendMessage(player,
                        plugin.getConfigManager().getMessage("not_enough_level", "level", floor.getRequiredLevel()));
                    return;
                }

                // Check key
                if (floor.requiresKey()) {
                    if (!plugin.getMythicMobsIntegration().hasItem(player, floor.getRequiredKey())) {
                        MessageUtils.sendMessage(player,
                            plugin.getConfigManager().getMessage("missing_key", "key", floor.getRequiredKey()));
                        return;
                    }
                }

                player.closeInventory();
                // Initiate 60-second transition countdown instead of immediate advance
                session.initiateFloorTransition(player);
                break;

            case FloorTransitionGui.SLOT_EXIT:
                // End session
                player.closeInventory();
                if (session != null) {
                    session.endSession(true);
                }
                break;

            case FloorTransitionGui.SLOT_MECHANIC:
                // Open mechanic GUI based on completed floor
                int completedFloor = nextFloor - 1;
                player.closeInventory();
                openMechanicGui(player, completedFloor);
                break;
        }
    }

    private void openMechanicGui(Player player, int completedFloor) {
        switch (completedFloor) {
            case 1:
                plugin.getGuiManager().openCorruptedBlacksmithGui(player);
                break;
            case 2:
                plugin.getGuiManager().openDemonicSmelterGui(player);
                break;
            case 3:
                plugin.getGuiManager().openDivineSourceGui(player);
                break;
            case 4:
                plugin.getGuiManager().openInfernalCrucibleGui(player);
                break;
        }
    }

    private void handleInfoClick(Player player, int slot) {
        if (slot == 44) { // Close button
            player.closeInventory();
        }
    }

    private void handleFloorMechanicClick(Player player, int slot) {
        DemonTowerSession session = plugin.getSessionManager().getCurrentSession();
        if (session == null) return;

        switch (slot) {
            case FloorMechanicGui.SLOT_MECHANIC:
                // Open specific mechanic GUI based on floor
                int floor = session.getCurrentFloor();
                player.closeInventory();
                openMechanicGui(player, floor);
                break;

            case FloorMechanicGui.SLOT_ADVANCE:
                // Check if transition already in progress
                if (session.isFloorTransitionInProgress()) {
                    MessageUtils.sendMessage(player, "&cTransition already in progress! Wait " +
                        session.getFloorTransitionRemaining() + " seconds.");
                    player.closeInventory();
                    return;
                }

                // Check requirements
                int nextFloorNum = session.getCurrentFloor() + 1;
                FloorConfig nextFloor = plugin.getConfigManager().getFloor(nextFloorNum);
                if (nextFloor == null) {
                    // Final floor completed
                    MessageUtils.sendMessage(player, "&aCongratulations! You have completed all floors!");
                    return;
                }

                // Check level
                if (player.getLevel() < nextFloor.getRequiredLevel()) {
                    MessageUtils.sendMessage(player,
                        plugin.getConfigManager().getMessage("not_enough_level", "level", nextFloor.getRequiredLevel()));
                    return;
                }

                // Check key
                if (nextFloor.requiresKey()) {
                    if (!plugin.getMythicMobsIntegration().hasItem(player, nextFloor.getRequiredKey())) {
                        MessageUtils.sendMessage(player,
                            plugin.getConfigManager().getMessage("missing_key", "key", nextFloor.getRequiredKey()));
                        return;
                    }
                }

                player.closeInventory();
                // Initiate 60-second transition countdown
                session.initiateFloorTransition(player);
                break;

            case FloorMechanicGui.SLOT_INFO:
                // Info paper - no action, just viewing
                break;

            case FloorMechanicGui.SLOT_STATUS:
                // Status display - no action
                break;
        }
    }

    private void handleCorruptedBlacksmithClick(Player player, InventoryClickEvent event) {
        CorruptedBlacksmithGui gui = plugin.getGuiManager().getBlacksmithGui(player);
        if (gui == null) return;

        int slot = event.getRawSlot();
        boolean isTopInventory = slot < 54;

        // Handle button clicks
        if (isTopInventory) {
            event.setCancelled(true);

            // Check if in reroll decision state
            if (gui.isInRerollState()) {
                if (slot == 22) { // Accept button
                    gui.handleAcceptResult();
                } else if (slot == 24) { // Reroll button
                    gui.handleReroll();
                }
                return;
            }

            if (slot == CorruptedBlacksmithGui.SLOT_PROCEED) {
                gui.handleProceed();
            } else if (slot == CorruptedBlacksmithGui.SLOT_INPUT && gui.getInputItem() == null) {
                // Allow item placement - handle cursor
                ItemStack cursorItem = event.getCursor();
                if (cursorItem != null && !cursorItem.getType().isAir()) {
                    // Only clear cursor if item was accepted
                    if (gui.handleItemPlace(cursorItem.clone())) {
                        event.setCursor(null);
                    }
                }
            }
        } else {
            // Player inventory click - allow both shift-click and normal click to place
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && !clickedItem.getType().isAir() && gui.getInputItem() == null) {
                event.setCancelled(true);
                // Only remove item from inventory if it was accepted
                if (gui.handleItemPlace(clickedItem.clone())) {
                    event.setCurrentItem(null);
                }
            }
        }
    }

    private void handleDemonicSmelterClick(Player player, InventoryClickEvent event) {
        DemonicSmelterGui gui = plugin.getGuiManager().getSmelterGui(player);
        if (gui == null) return;

        int slot = event.getRawSlot();
        boolean isTopInventory = slot < 54;

        if (isTopInventory) {
            event.setCancelled(true);

            if (slot == DemonicSmelterGui.SLOT_CONFIRM) {
                gui.handleConfirm();
            } else if (slot == DemonicSmelterGui.SLOT_INPUT_1 || slot == DemonicSmelterGui.SLOT_INPUT_2) {
                // Allow item placement
                ItemStack cursorItem = event.getCursor();
                if (cursorItem != null && !cursorItem.getType().isAir()) {
                    // Only clear cursor if item was accepted
                    if (gui.handleItemPlace(slot, cursorItem.clone())) {
                        event.setCursor(null);
                    }
                }
            }
            // No cancel button - players close inventory to get items back
        } else {
            // Player inventory - allow both shift-click and normal click
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && !clickedItem.getType().isAir()) {
                event.setCancelled(true);
                // Determine which slot to fill
                int targetSlot = (gui.getInputItem1() == null) ?
                    DemonicSmelterGui.SLOT_INPUT_1 : DemonicSmelterGui.SLOT_INPUT_2;
                // Only remove item from inventory if it was accepted
                if (gui.handleItemPlace(targetSlot, clickedItem.clone())) {
                    event.setCurrentItem(null);
                }
            }
        }
    }

    private void handleDivineSourceClick(Player player, InventoryClickEvent event) {
        DivineSourceGui gui = plugin.getGuiManager().getDivineSourceGui(player);
        if (gui == null) return;

        int slot = event.getRawSlot();
        boolean isTopInventory = slot < 54;

        if (isTopInventory) {
            event.setCancelled(true);

            if (slot == DivineSourceGui.SLOT_PROCEED) {
                gui.handleProceed();
            } else if (slot == DivineSourceGui.SLOT_INPUT && gui.getInputItem() == null) {
                // Allow item placement
                ItemStack cursorItem = event.getCursor();
                if (cursorItem != null && !cursorItem.getType().isAir()) {
                    // Only clear cursor if item was accepted
                    if (gui.handleItemPlace(cursorItem.clone())) {
                        event.setCursor(null);
                    }
                }
            }
        } else {
            // Player inventory - allow both shift-click and normal click
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && !clickedItem.getType().isAir() && gui.getInputItem() == null) {
                event.setCancelled(true);
                // Only remove item from inventory if it was accepted
                if (gui.handleItemPlace(clickedItem.clone())) {
                    event.setCurrentItem(null);
                }
            }
        }
    }

    private void handleInfernalCrucibleClick(Player player, InventoryClickEvent event) {
        InfernalCrucibleGui gui = plugin.getGuiManager().getCrucibleGui(player);
        if (gui == null) return;

        int slot = event.getRawSlot();
        boolean isTopInventory = slot < 54;

        if (isTopInventory) {
            event.setCancelled(true);

            if (slot == InfernalCrucibleGui.SLOT_CONFIRM) {
                gui.handleConfirm();
            } else if (slot == InfernalCrucibleGui.SLOT_CANCEL) {
                gui.handleCancel();
            } else if (slot == InfernalCrucibleGui.SLOT_INPUT_1 || slot == InfernalCrucibleGui.SLOT_INPUT_2) {
                // Allow item placement
                ItemStack cursorItem = event.getCursor();
                if (cursorItem != null && !cursorItem.getType().isAir()) {
                    // Only clear cursor if item was accepted
                    if (gui.handleItemPlace(slot, cursorItem.clone())) {
                        event.setCursor(null);
                    }
                }
            }
        } else {
            // Player inventory - allow both shift-click and normal click
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && !clickedItem.getType().isAir()) {
                event.setCancelled(true);
                // Determine which slot to fill
                int targetSlot = (gui.getInputItem1() == null) ?
                    InfernalCrucibleGui.SLOT_INPUT_1 : InfernalCrucibleGui.SLOT_INPUT_2;
                // Only remove item from inventory if it was accepted
                if (gui.handleItemPlace(targetSlot, clickedItem.clone())) {
                    event.setCurrentItem(null);
                }
            }
        }
    }

    private void handleFloorSelectionClick(Player player, InventoryClickEvent event) {
        int slot = event.getRawSlot();

        switch (slot) {
            case FloorSelectionGui.SLOT_FLOOR_1:
                player.closeInventory();
                plugin.getGuiManager().openFloorDropsPreviewGui(player, 1);
                break;
            case FloorSelectionGui.SLOT_FLOOR_2:
                player.closeInventory();
                plugin.getGuiManager().openFloorDropsPreviewGui(player, 2);
                break;
            case FloorSelectionGui.SLOT_FLOOR_3:
                player.closeInventory();
                plugin.getGuiManager().openFloorDropsPreviewGui(player, 3);
                break;
            case FloorSelectionGui.SLOT_FLOOR_4:
                player.closeInventory();
                plugin.getGuiManager().openFloorDropsPreviewGui(player, 4);
                break;
            case FloorSelectionGui.SLOT_BACK:
                player.closeInventory();
                plugin.getGuiManager().openLobbyGui(player);
                break;
        }
    }

    private void handleFloorDropsPreviewClick(Player player, InventoryClickEvent event) {
        int slot = event.getRawSlot();

        if (slot == FloorDropsPreviewGui.SLOT_BACK) {
            player.closeInventory();
            plugin.getGuiManager().openFloorSelectionGui(player);
        }
        // All other clicks are just viewing - no action needed
    }

    private void handleAdminFloorSelectionClick(Player player, InventoryClickEvent event) {
        int slot = event.getRawSlot();

        switch (slot) {
            case AdminFloorSelectionGui.SLOT_FLOOR_1:
                player.closeInventory();
                plugin.getGuiManager().openDropsAdminGui(player, 1);
                break;
            case AdminFloorSelectionGui.SLOT_FLOOR_2:
                player.closeInventory();
                plugin.getGuiManager().openDropsAdminGui(player, 2);
                break;
            case AdminFloorSelectionGui.SLOT_FLOOR_3:
                player.closeInventory();
                plugin.getGuiManager().openDropsAdminGui(player, 3);
                break;
            case AdminFloorSelectionGui.SLOT_FLOOR_4:
                player.closeInventory();
                plugin.getGuiManager().openDropsAdminGui(player, 4);
                break;
            case AdminFloorSelectionGui.SLOT_CLOSE:
                player.closeInventory();
                break;
        }
    }

    private void handleDropsAdminClick(Player player, InventoryClickEvent event) {
        DropsAdminGui gui = plugin.getGuiManager().getDropsAdminGui(player);
        if (gui == null) {
            event.setCancelled(true);
            return;
        }

        int slot = event.getRawSlot();
        boolean isTopInventory = slot < 54;

        if (isTopInventory) {
            // Control row (slots 45-53) - cancel and handle buttons
            if (slot >= 45) {
                event.setCancelled(true);

                if (slot == DropsAdminGui.SLOT_SAVE) {
                    gui.handleSave();
                } else if (slot == DropsAdminGui.SLOT_CLEAR) {
                    gui.handleClear();
                } else if (slot == DropsAdminGui.SLOT_BACK) {
                    player.closeInventory();
                    plugin.getGuiManager().openAdminFloorSelectionGui(player);
                }
                return;
            }

            // Item slots (0-44) - allow normal item manipulation
            // Let Bukkit handle the click naturally, then sync our tracking
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                // Sync all items from inventory to pending items
                for (int i = 0; i < 45; i++) {
                    ItemStack item = gui.getInventory().getItem(i);
                    if (item != null && !item.getType().isAir()) {
                        gui.getPendingItems().put(i, item.clone());
                    } else {
                        gui.getPendingItems().remove(i);
                    }
                }
            }, 1L);

        } else {
            // Player inventory clicks
            if (event.isShiftClick()) {
                // Shift-click to add item to first empty slot in drops area
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem != null && !clickedItem.getType().isAir()) {
                    event.setCancelled(true);
                    // Find first empty slot in drops area
                    for (int i = 0; i < 45; i++) {
                        ItemStack existing = gui.getInventory().getItem(i);
                        if (existing == null || existing.getType().isAir()) {
                            gui.handleItemPlace(i, clickedItem.clone());
                            event.setCurrentItem(null);
                            break;
                        }
                    }
                }
            }
            // Normal clicks in player inventory are allowed (don't cancel)
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        String title = PlainTextComponentSerializer.plainText()
            .serialize(event.getView().title());

        if (!plugin.getGuiManager().isGuiInventory(title)) return;

        // Allow drag in DropsAdminGui for item slots (0-44)
        if (title.contains("Admin Edit")) {
            DropsAdminGui gui = plugin.getGuiManager().getDropsAdminGui(player);
            if (gui != null) {
                // Check if any dragged slot is in control row (45-53)
                boolean dragInControlRow = event.getRawSlots().stream()
                    .anyMatch(slot -> slot >= 45 && slot < 54);

                if (dragInControlRow) {
                    event.setCancelled(true);
                    return;
                }

                // Allow drag in item slots, sync after
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    for (int i = 0; i < 45; i++) {
                        ItemStack item = gui.getInventory().getItem(i);
                        if (item != null && !item.getType().isAir()) {
                            gui.getPendingItems().put(i, item.clone());
                        } else {
                            gui.getPendingItems().remove(i);
                        }
                    }
                }, 1L);
                return;
            }
        }

        // Cancel drag in all other GUIs
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();
        plugin.getGuiManager().closeGui(player);
    }
}
