package pl.yourserver.demonTowerPlugin.database;

import org.bukkit.inventory.ItemStack;
import pl.yourserver.demonTowerPlugin.DemonTowerPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FloorDropsManager {

    private final DemonTowerPlugin plugin;
    private final DatabaseManager databaseManager;

    // Cache: floor -> slot -> item
    private final Map<Integer, Map<Integer, ItemStack>> dropsCache = new ConcurrentHashMap<>();

    // Maximum slots per floor for drops display
    public static final int MAX_SLOTS = 45; // 5 rows of items

    public FloorDropsManager(DemonTowerPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public void loadAllDrops() {
        if (!databaseManager.isEnabled()) {
            plugin.getLogger().info("Database disabled - floor drops will not be loaded.");
            return;
        }

        // Load drops for all 4 floors
        for (int floor = 1; floor <= 4; floor++) {
            loadFloorDrops(floor);
        }
        plugin.getLogger().info("Floor drops loaded from database.");
    }

    private void loadFloorDrops(int floor) {
        List<DatabaseManager.FloorDropEntry> entries = databaseManager.loadFloorDrops(floor);
        Map<Integer, ItemStack> floorDrops = new ConcurrentHashMap<>();

        for (DatabaseManager.FloorDropEntry entry : entries) {
            floorDrops.put(entry.getSlot(), entry.getItem());
        }

        dropsCache.put(floor, floorDrops);
    }

    public Map<Integer, ItemStack> getFloorDrops(int floor) {
        return dropsCache.getOrDefault(floor, new HashMap<>());
    }

    public List<ItemStack> getFloorDropsList(int floor) {
        Map<Integer, ItemStack> drops = getFloorDrops(floor);
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (drops.containsKey(i)) {
                list.add(drops.get(i));
            }
        }
        return list;
    }

    public void setFloorDrop(int floor, int slot, ItemStack item) {
        Map<Integer, ItemStack> floorDrops = dropsCache.computeIfAbsent(floor, k -> new ConcurrentHashMap<>());

        if (item == null || item.getType().isAir()) {
            floorDrops.remove(slot);
            databaseManager.deleteFloorDrop(floor, slot);
        } else {
            floorDrops.put(slot, item.clone());
            databaseManager.saveFloorDrop(floor, slot, item);
        }
    }

    public void removeFloorDrop(int floor, int slot) {
        Map<Integer, ItemStack> floorDrops = dropsCache.get(floor);
        if (floorDrops != null) {
            floorDrops.remove(slot);
        }
        databaseManager.deleteFloorDrop(floor, slot);
    }

    public void saveFloorDrops(int floor, Map<Integer, ItemStack> drops) {
        dropsCache.put(floor, new ConcurrentHashMap<>(drops));

        List<DatabaseManager.FloorDropEntry> entries = new ArrayList<>();
        for (Map.Entry<Integer, ItemStack> entry : drops.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().getType().isAir()) {
                entries.add(new DatabaseManager.FloorDropEntry(entry.getKey(), entry.getValue()));
            }
        }
        databaseManager.saveAllFloorDrops(floor, entries);
    }

    public int getDropCount(int floor) {
        Map<Integer, ItemStack> drops = dropsCache.get(floor);
        if (drops == null) return 0;
        return (int) drops.values().stream()
                .filter(item -> item != null && !item.getType().isAir())
                .count();
    }

    public String getFloorName(int floor) {
        return switch (floor) {
            case 1 -> "Floor I";
            case 2 -> "Floor II";
            case 3 -> "Floor III";
            case 4 -> "Floor IV";
            default -> "Floor " + floor;
        };
    }

    public String getRomanNumeral(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> String.valueOf(number);
        };
    }

    public void reloadDrops() {
        dropsCache.clear();
        loadAllDrops();
    }
}
