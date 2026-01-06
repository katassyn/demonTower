package pl.yourserver.demonTowerPlugin.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import pl.yourserver.demonTowerPlugin.DemonTowerPlugin;
import pl.yourserver.demonTowerPlugin.game.StageType;

import java.util.*;

public class ConfigManager {

    private final DemonTowerPlugin plugin;
    private final Map<Integer, FloorConfig> floors = new HashMap<>();
    private final Map<String, String> messages = new HashMap<>();
    private double waveCompletionPercentage = 0.95;
    private int lobbyTime = 300;
    private int stageWaitTime = 300;
    private int unblockFloor = 3;
    private double spawnRadius = 4.0;
    private boolean debugMode = false;

    public ConfigManager(DemonTowerPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        FileConfiguration config = plugin.getConfig();

        debugMode = config.getBoolean("debug_mode", false);
        waveCompletionPercentage = config.getDouble("demon_tower.wave_completion_percentage", 0.95);
        lobbyTime = config.getInt("demon_tower.lobby_time", 300);
        stageWaitTime = config.getInt("demon_tower.stage_wait_time", 300);
        unblockFloor = config.getInt("demon_tower.unblock_floor", 3);
        spawnRadius = config.getDouble("demon_tower.spawn_radius", 4.0);

        if (debugMode) {
            plugin.getLogger().warning("DEBUG MODE IS ENABLED! This should only be used for testing.");
        }

        loadFloors(config);
        loadMessages(config);
    }

    public double getWaveCompletionPercentage() {
        return waveCompletionPercentage;
    }

    public int getLobbyTime() {
        return lobbyTime;
    }

    public int getStageWaitTime() {
        return stageWaitTime;
    }

    public int getUnblockFloor() {
        return unblockFloor;
    }

    public double getSpawnRadius() {
        return spawnRadius;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    private void loadFloors(FileConfiguration config) {
        floors.clear();

        ConfigurationSection floorsSection = config.getConfigurationSection("demon_tower.floors");
        if (floorsSection == null) {
            plugin.getLogger().warning("No floors configuration found!");
            return;
        }

        for (String floorKey : floorsSection.getKeys(false)) {
            int floorNum = Integer.parseInt(floorKey);
            ConfigurationSection floorSection = floorsSection.getConfigurationSection(floorKey);

            if (floorSection == null) continue;

            int requiredLevel = floorSection.getInt("required_level", 1);
            String requiredKey = floorSection.getString("required_key", null);

            List<StageConfig> stages = new ArrayList<>();
            ConfigurationSection stagesSection = floorSection.getConfigurationSection("stages");

            if (stagesSection != null) {
                for (String stageKey : stagesSection.getKeys(false)) {
                    int stageNum = Integer.parseInt(stageKey);
                    ConfigurationSection stageSection = stagesSection.getConfigurationSection(stageKey);

                    if (stageSection == null) continue;

                    StageType type = StageType.valueOf(stageSection.getString("type", "WAVE").toUpperCase());
                    String warp = stageSection.getString("warp", "dt_" + floorNum + "_" + stageNum);
                    int time = stageSection.getInt("time", 300);
                    String boss = stageSection.getString("boss", null);
                    String collectItem = stageSection.getString("collect_item", null);
                    int collectAmount = stageSection.getInt("collect_amount", 10);

                    Map<String, Integer> mobs = new HashMap<>();
                    ConfigurationSection mobsSection = stageSection.getConfigurationSection("mobs");
                    if (mobsSection != null) {
                        for (String mobName : mobsSection.getKeys(false)) {
                            mobs.put(mobName, mobsSection.getInt(mobName));
                        }
                    }

                    // Parse spawn points
                    List<SpawnPoint> mobSpawnPoints = new ArrayList<>();
                    List<?> mobSpawnsList = stageSection.getList("spawn_points.mobs");
                    if (mobSpawnsList != null) {
                        for (Object obj : mobSpawnsList) {
                            SpawnPoint sp = parseSpawnPoint(obj);
                            if (sp != null) {
                                mobSpawnPoints.add(sp);
                            }
                        }
                    }

                    SpawnPoint bossSpawnPoint = null;
                    Object bossSpawnObj = stageSection.get("spawn_points.boss");
                    if (bossSpawnObj != null) {
                        bossSpawnPoint = parseSpawnPoint(bossSpawnObj);
                    }

                    stages.add(new StageConfig(floorNum, stageNum, type, warp, time, mobs, boss, collectItem, collectAmount,
                        mobSpawnPoints, bossSpawnPoint));
                }
            }

            // Sort stages by stage number
            stages.sort(Comparator.comparingInt(StageConfig::getStage));

            floors.put(floorNum, new FloorConfig(floorNum, requiredLevel, requiredKey, stages));
        }

        plugin.getLogger().info("Loaded " + floors.size() + " floors configuration.");
    }

    private void loadMessages(FileConfiguration config) {
        messages.clear();

        ConfigurationSection messagesSection = config.getConfigurationSection("demon_tower.messages");
        if (messagesSection != null) {
            for (String key : messagesSection.getKeys(false)) {
                messages.put(key, messagesSection.getString(key, ""));
            }
        }

        // Default messages (English)
        messages.putIfAbsent("wave_start", "&c&lWave started! You have %time% seconds!");
        messages.putIfAbsent("wave_complete", "&a&lWave completed!");
        messages.putIfAbsent("time_up", "&4&lTime's up! You have been defeated...");
        messages.putIfAbsent("boss_spawned", "&4&l%boss% has appeared!");
        messages.putIfAbsent("collect_progress", "&eCollected: &a%current%&7/&a%required%");
        messages.putIfAbsent("floor_complete", "&a&lFloor %floor% completed!");
        messages.putIfAbsent("player_joined", "&a%player% &7joined Demon Tower!");
        messages.putIfAbsent("player_left", "&c%player% &7left Demon Tower!");
        messages.putIfAbsent("not_enough_level", "&cYou need level &e%level%&c to enter this floor!");
        messages.putIfAbsent("missing_key", "&cYou need the key &e%key%&c to enter this floor!");
        messages.putIfAbsent("session_in_progress", "&cA Demon Tower session is in progress! Wait until it ends.");
        messages.putIfAbsent("countdown", "&e%time%");
        messages.putIfAbsent("stage_cleared", "&a&lStage cleared!");
        messages.putIfAbsent("joining_disabled", "&cJoining is disabled - wait until floor 3 is reached!");
        messages.putIfAbsent("lobby_countdown", "&eLobby time remaining: &c%time%");
        messages.putIfAbsent("lobby_starting", "&a&lWave starting in &c%time% &aseconds!");
        messages.putIfAbsent("waiting_next_stage", "&7Waiting for next stage... &e%time%s");
        messages.putIfAbsent("dt_blocked", "&cDemon Tower is blocked! Wait until floor 3 is reached.");
        messages.putIfAbsent("already_in_dt", "&cYou are already in Demon Tower!");
        messages.putIfAbsent("not_in_dt", "&cYou are not in Demon Tower!");
        messages.putIfAbsent("floor_transition", "&6&lAdvancing to Floor &c%floor%&6!");
        messages.putIfAbsent("boss_defeated", "&a&lBoss defeated! You may proceed!");
        messages.putIfAbsent("collect_complete", "&a&lAll required items collected!");
        messages.putIfAbsent("wave_progress", "&7Wave progress: &a%killed%&7/&a%total% &7(&e%percent%%&7)");
    }

    public FloorConfig getFloor(int floorNumber) {
        return floors.get(floorNumber);
    }

    public Map<Integer, FloorConfig> getAllFloors() {
        return Collections.unmodifiableMap(floors);
    }

    public int getFloorCount() {
        return floors.size();
    }

    public String getMessage(String key) {
        return messages.getOrDefault(key, "&cMissing message: " + key);
    }

    public String getMessage(String key, Object... replacements) {
        String message = getMessage(key);
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace("%" + replacements[i] + "%", String.valueOf(replacements[i + 1]));
            }
        }
        return message;
    }

    /**
     * Parse spawn point from config.
     * Supports formats:
     * - Map with world, x, y, z keys
     * - String "world,x,y,z"
     */
    @SuppressWarnings("unchecked")
    private SpawnPoint parseSpawnPoint(Object obj) {
        if (obj == null) return null;

        try {
            if (obj instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) obj;
                String world = String.valueOf(map.get("world"));
                double x = ((Number) map.get("x")).doubleValue();
                double y = ((Number) map.get("y")).doubleValue();
                double z = ((Number) map.get("z")).doubleValue();
                return new SpawnPoint(world, x, y, z);
            } else if (obj instanceof String) {
                // Format: "world,x,y,z"
                String[] parts = ((String) obj).split(",");
                if (parts.length >= 4) {
                    String world = parts[0].trim();
                    double x = Double.parseDouble(parts[1].trim());
                    double y = Double.parseDouble(parts[2].trim());
                    double z = Double.parseDouble(parts[3].trim());
                    return new SpawnPoint(world, x, y, z);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse spawn point: " + obj + " - " + e.getMessage());
        }

        return null;
    }
}
