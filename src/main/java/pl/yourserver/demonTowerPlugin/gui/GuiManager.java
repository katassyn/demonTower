package pl.yourserver.demonTowerPlugin.gui;

import org.bukkit.entity.Player;
import pl.yourserver.demonTowerPlugin.DemonTowerPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GuiManager {

    private final DemonTowerPlugin plugin;
    private final Map<UUID, GuiType> openGuis = new HashMap<>();

    public enum GuiType {
        LOBBY,
        FLOOR_TRANSITION,
        FLOOR_MECHANIC,
        INFO,
        CORRUPTED_BLACKSMITH,
        DEMONIC_SMELTER,
        DIVINE_SOURCE,
        INFERNAL_CRUCIBLE,
        FLOOR_SELECTION,
        FLOOR_DROPS_PREVIEW,
        ADMIN_FLOOR_SELECTION,
        DROPS_ADMIN
    }

    // Store active mechanic GUIs for item handling
    private final Map<UUID, CorruptedBlacksmithGui> blacksmithGuis = new HashMap<>();
    private final Map<UUID, DemonicSmelterGui> smelterGuis = new HashMap<>();
    private final Map<UUID, DivineSourceGui> divineSourceGuis = new HashMap<>();
    private final Map<UUID, InfernalCrucibleGui> crucibleGuis = new HashMap<>();
    private final Map<UUID, DropsAdminGui> dropsAdminGuis = new HashMap<>();
    private final Map<UUID, Integer> floorDropsPreviewFloor = new HashMap<>();

    public GuiManager(DemonTowerPlugin plugin) {
        this.plugin = plugin;
    }

    public void openLobbyGui(Player player) {
        LobbyGui gui = new LobbyGui(plugin, player);
        gui.open();
        openGuis.put(player.getUniqueId(), GuiType.LOBBY);
    }

    public void openFloorTransitionGui(Player player, int nextFloor) {
        FloorTransitionGui gui = new FloorTransitionGui(plugin, player, nextFloor);
        gui.open();
        openGuis.put(player.getUniqueId(), GuiType.FLOOR_TRANSITION);
    }

    public void openFloorMechanicGui(Player player) {
        FloorMechanicGui gui = new FloorMechanicGui(plugin, player);
        gui.open();
        openGuis.put(player.getUniqueId(), GuiType.FLOOR_MECHANIC);
    }

    public void openInfoGui(Player player) {
        InfoGui gui = new InfoGui(plugin, player);
        gui.open();
        openGuis.put(player.getUniqueId(), GuiType.INFO);
    }

    public void openCorruptedBlacksmithGui(Player player) {
        CorruptedBlacksmithGui gui = new CorruptedBlacksmithGui(plugin, player);
        gui.open();
        openGuis.put(player.getUniqueId(), GuiType.CORRUPTED_BLACKSMITH);
        blacksmithGuis.put(player.getUniqueId(), gui);
    }

    public void openDemonicSmelterGui(Player player) {
        DemonicSmelterGui gui = new DemonicSmelterGui(plugin, player);
        gui.open();
        openGuis.put(player.getUniqueId(), GuiType.DEMONIC_SMELTER);
        smelterGuis.put(player.getUniqueId(), gui);
    }

    public void openDivineSourceGui(Player player) {
        DivineSourceGui gui = new DivineSourceGui(plugin, player);
        gui.open();
        openGuis.put(player.getUniqueId(), GuiType.DIVINE_SOURCE);
        divineSourceGuis.put(player.getUniqueId(), gui);
    }

    public void openInfernalCrucibleGui(Player player) {
        InfernalCrucibleGui gui = new InfernalCrucibleGui(plugin, player);
        gui.open();
        openGuis.put(player.getUniqueId(), GuiType.INFERNAL_CRUCIBLE);
        crucibleGuis.put(player.getUniqueId(), gui);
    }

    public void openFloorSelectionGui(Player player) {
        FloorSelectionGui gui = new FloorSelectionGui(plugin, player);
        gui.open();
        openGuis.put(player.getUniqueId(), GuiType.FLOOR_SELECTION);
    }

    public void openFloorDropsPreviewGui(Player player, int floor) {
        FloorDropsPreviewGui gui = new FloorDropsPreviewGui(plugin, player, floor);
        gui.open();
        openGuis.put(player.getUniqueId(), GuiType.FLOOR_DROPS_PREVIEW);
        floorDropsPreviewFloor.put(player.getUniqueId(), floor);
    }

    public void openAdminFloorSelectionGui(Player player) {
        AdminFloorSelectionGui gui = new AdminFloorSelectionGui(plugin, player);
        gui.open();
        openGuis.put(player.getUniqueId(), GuiType.ADMIN_FLOOR_SELECTION);
    }

    public void openDropsAdminGui(Player player, int floor) {
        DropsAdminGui gui = new DropsAdminGui(plugin, player, floor);
        gui.open();
        openGuis.put(player.getUniqueId(), GuiType.DROPS_ADMIN);
        dropsAdminGuis.put(player.getUniqueId(), gui);
    }

    public CorruptedBlacksmithGui getBlacksmithGui(Player player) {
        return blacksmithGuis.get(player.getUniqueId());
    }

    public DemonicSmelterGui getSmelterGui(Player player) {
        return smelterGuis.get(player.getUniqueId());
    }

    public DivineSourceGui getDivineSourceGui(Player player) {
        return divineSourceGuis.get(player.getUniqueId());
    }

    public InfernalCrucibleGui getCrucibleGui(Player player) {
        return crucibleGuis.get(player.getUniqueId());
    }

    public DropsAdminGui getDropsAdminGui(Player player) {
        return dropsAdminGuis.get(player.getUniqueId());
    }

    public int getFloorDropsPreviewFloor(Player player) {
        return floorDropsPreviewFloor.getOrDefault(player.getUniqueId(), 1);
    }

    public GuiType getOpenGui(Player player) {
        return openGuis.get(player.getUniqueId());
    }

    public boolean hasOpenGui(Player player) {
        return openGuis.containsKey(player.getUniqueId());
    }

    public void closeGui(Player player) {
        UUID uuid = player.getUniqueId();
        GuiType type = openGuis.remove(uuid);

        // Clean up mechanic GUIs
        if (type != null) {
            switch (type) {
                case CORRUPTED_BLACKSMITH:
                    CorruptedBlacksmithGui bsGui = blacksmithGuis.remove(uuid);
                    if (bsGui != null) bsGui.handleClose();
                    break;
                case DEMONIC_SMELTER:
                    DemonicSmelterGui smGui = smelterGuis.remove(uuid);
                    if (smGui != null) smGui.handleClose();
                    break;
                case DIVINE_SOURCE:
                    DivineSourceGui dsGui = divineSourceGuis.remove(uuid);
                    if (dsGui != null) dsGui.handleClose();
                    break;
                case INFERNAL_CRUCIBLE:
                    InfernalCrucibleGui icGui = crucibleGuis.remove(uuid);
                    if (icGui != null) icGui.handleClose();
                    break;
                case DROPS_ADMIN:
                    DropsAdminGui daGui = dropsAdminGuis.remove(uuid);
                    if (daGui != null) daGui.handleClose();
                    break;
                case FLOOR_DROPS_PREVIEW:
                    floorDropsPreviewFloor.remove(uuid);
                    break;
            }
        }
    }

    public boolean isGuiInventory(String title) {
        return title != null && (
            title.contains("Demon Tower") ||
            title.contains("Lobby") ||
            title.contains("Advance") ||
            title.contains("Floor") ||
            title.contains("Information") ||
            title.contains("Corrupted Blacksmith") ||
            title.contains("Demonic Smelter") ||
            title.contains("Divine Source") ||
            title.contains("Infernal Crucible") ||
            title.contains("Drop Preview") ||
            title.contains("Admin Edit") ||
            title.contains("Admin Drops") ||
            title.contains("Floor Mechanic")
        );
    }
}
