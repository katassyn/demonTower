package pl.yourserver.demonTowerPlugin.config;

import org.bukkit.Location;
import pl.yourserver.demonTowerPlugin.game.StageType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class StageConfig {
    private static final Random random = new Random();

    private final int floor;
    private final int stage;
    private final StageType type;
    private final String warp;
    private final int timeSeconds;
    private final Map<String, Integer> mobs;
    private final String boss;
    private final String collectItem;
    private final int collectAmount;

    // Spawn points
    private final List<SpawnPoint> mobSpawnPoints;
    private final SpawnPoint bossSpawnPoint;

    public StageConfig(int floor, int stage, StageType type, String warp, int timeSeconds,
                       Map<String, Integer> mobs, String boss, String collectItem, int collectAmount,
                       List<SpawnPoint> mobSpawnPoints, SpawnPoint bossSpawnPoint) {
        this.floor = floor;
        this.stage = stage;
        this.type = type;
        this.warp = warp;
        this.timeSeconds = timeSeconds;
        this.mobs = mobs;
        this.boss = boss;
        this.collectItem = collectItem;
        this.collectAmount = collectAmount;
        this.mobSpawnPoints = mobSpawnPoints != null ? mobSpawnPoints : new ArrayList<>();
        this.bossSpawnPoint = bossSpawnPoint;
    }

    public int getFloor() {
        return floor;
    }

    public int getStage() {
        return stage;
    }

    public StageType getType() {
        return type;
    }

    public String getWarp() {
        return warp;
    }

    public int getTimeSeconds() {
        return timeSeconds;
    }

    public Map<String, Integer> getMobs() {
        return mobs;
    }

    public String getBoss() {
        return boss;
    }

    public String getCollectItem() {
        return collectItem;
    }

    public int getCollectAmount() {
        return collectAmount;
    }

    public boolean hasTimer() {
        return type == StageType.WAVE || type == StageType.BOSS;
    }

    public boolean hasBoss() {
        return boss != null && !boss.isEmpty();
    }

    public String getStageId() {
        return floor + "_" + stage;
    }

    // Spawn point methods

    public List<SpawnPoint> getMobSpawnPoints() {
        return mobSpawnPoints;
    }

    public SpawnPoint getBossSpawnPoint() {
        return bossSpawnPoint;
    }

    public boolean hasSpawnPoints() {
        return !mobSpawnPoints.isEmpty();
    }

    public boolean hasBossSpawnPoint() {
        return bossSpawnPoint != null;
    }

    /**
     * Get a random mob spawn point from the list
     */
    public SpawnPoint getRandomMobSpawnPoint() {
        if (mobSpawnPoints.isEmpty()) return null;
        return mobSpawnPoints.get(random.nextInt(mobSpawnPoints.size()));
    }

    /**
     * Get a specific spawn point by index (0-based)
     * @param index The index of the spawn point (0, 1, 2, etc.)
     * @return The spawn point or null if index is out of bounds
     */
    public SpawnPoint getSpawnPoint(int index) {
        if (index < 0 || index >= mobSpawnPoints.size()) return null;
        return mobSpawnPoints.get(index);
    }

    /**
     * Get number of spawn points
     */
    public int getSpawnPointCount() {
        return mobSpawnPoints.size();
    }

    /**
     * Get location at specific spawn point with radius
     * @param index The spawn point index (0-based)
     * @param radius The radius for random offset
     * @return Location with random offset or null
     */
    public Location getSpawnLocationAt(int index, double radius) {
        SpawnPoint point = getSpawnPoint(index);
        if (point == null) return null;
        return point.getRandomLocation(radius);
    }

    /**
     * Get a random location for mob spawning
     * Uses one of the spawn points with a small random offset
     */
    public Location getRandomMobSpawnLocation(double radius) {
        SpawnPoint point = getRandomMobSpawnPoint();
        if (point == null) return null;
        return point.getRandomLocation(radius);
    }

    /**
     * Get boss spawn location
     */
    public Location getBossSpawnLocation() {
        if (bossSpawnPoint == null) return null;
        return bossSpawnPoint.toLocation();
    }
}
