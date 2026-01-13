package pl.yourserver.demonTowerPlugin.game;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import pl.yourserver.demonTowerPlugin.DemonTowerPlugin;
import pl.yourserver.demonTowerPlugin.config.FloorConfig;
import pl.yourserver.demonTowerPlugin.config.SpawnPoint;
import pl.yourserver.demonTowerPlugin.config.StageConfig;
import pl.yourserver.demonTowerPlugin.integration.MythicMobsIntegration;
import pl.yourserver.demonTowerPlugin.utils.MessageUtils;

import java.util.*;

public class DemonTowerSession {
    private final DemonTowerPlugin plugin;
    private final Set<UUID> players = new HashSet<>();
    private final Set<UUID> aliveMobs = new HashSet<>();
    private UUID bossId = null;
    private UUID partyLeader = null;

    private int currentFloor;
    private int currentStage;
    private SessionState state;
    private int timeRemaining;

    // Zmienne dropu
    private int collectedItems;
    private int killsSinceLastDrop;

    private int totalMobCount = 0;

    private BukkitTask timerTask;
    private BukkitTask countdownTask;
    private BukkitTask lobbyCountdownTask;
    private BukkitTask stageWaitTask;
    private BukkitTask floorTransitionTask;
    private BukkitTask sanityTask;

    private boolean lobbyCountdownStarted = false;
    private boolean stageCleared = false;
    private int stageWaitRemaining = 0;
    private boolean floorTransitionInitiated = false;
    private int floorTransitionRemaining = 0;

    private int debugKillCount = 0;
    private BukkitTask mechanicTimeoutTask;
    private int mechanicTimeRemaining = 0;

    public static final int FLOOR_TRANSITION_TIME = 60;
    public static final int MECHANIC_TIMEOUT = 300;

    public DemonTowerSession(DemonTowerPlugin plugin) {
        this.plugin = plugin;
        this.currentFloor = 1;
        this.currentStage = 1;
        this.state = SessionState.WAITING;
        this.timeRemaining = 0;
        this.collectedItems = 0;
        this.killsSinceLastDrop = 0;
    }

    public boolean addPlayer(Player player) {
        if (state != SessionState.WAITING) {
            MessageUtils.sendMessage(player, plugin.getConfigManager().getMessage("joining_disabled"));
            return false;
        }

        FloorConfig floor = plugin.getConfigManager().getFloor(currentFloor);
        if (floor == null) return false;

        if (player.getLevel() < floor.getRequiredLevel()) {
            MessageUtils.sendMessage(player, plugin.getConfigManager().getMessage("not_enough_level", "level", floor.getRequiredLevel()));
            return false;
        }

        if (floor.requiresKey()) {
            if (!plugin.getMythicMobsIntegration().hasItem(player, floor.getRequiredKey())) {
                MessageUtils.sendMessage(player, plugin.getConfigManager().getMessage("missing_key", "key", floor.getRequiredKey()));
                return false;
            }
        }

        if (players.add(player.getUniqueId())) {
            if (partyLeader == null) partyLeader = player.getUniqueId();
            StageConfig lobbyStage = floor.getFirstStage();
            if (lobbyStage != null) {
                plugin.getEssentialsIntegration().warpPlayer(player, lobbyStage.getWarp());
            }
            MessageUtils.sendMessage(player, plugin.getConfigManager().getMessage("player_joined", "player", player.getName()));
            if (!lobbyCountdownStarted && lobbyStage != null && lobbyStage.getType() == StageType.LOBBY) {
                startLobbyCountdown();
            }
            return true;
        }
        return false;
    }

    public void removePlayer(UUID playerId) {
        if (players.remove(playerId)) {
            if (players.isEmpty()) {
                endSession(false);
            }
        }
    }

    private void startLobbyCountdown() {
        if (lobbyCountdownStarted) return;
        lobbyCountdownStarted = true;

        int lobbyTime = plugin.getConfigManager().isDebugMode() ? 5 : plugin.getConfigManager().getLobbyTime();
        final int[] remaining = {lobbyTime};

        lobbyCountdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (remaining[0] <= 0) {
                    cancel();
                    onLobbyTimeUp();
                    return;
                }
                broadcastActionBar(plugin.getConfigManager().getMessage("lobby_countdown",
                        "time", MessageUtils.formatTime(remaining[0])));

                if (remaining[0] == 60 || remaining[0] == 30 || remaining[0] == 10) {
                    broadcastTitle("&e" + remaining[0], "&7seconds until wave starts!", 5, 20, 5);
                }
                if (remaining[0] <= 5) {
                    broadcastTitle("&c&l" + remaining[0], "&eGet ready!", 0, 20, 0);
                }
                remaining[0]--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void onLobbyTimeUp() {
        plugin.getSessionManager().setBlocked(true);
        FloorConfig floor = plugin.getConfigManager().getFloor(currentFloor);
        if (floor == null) return;
        startCountdown(5, this::beginLobbyWave);
    }

    private void beginLobbyWave() {
        FloorConfig floor = plugin.getConfigManager().getFloor(currentFloor);
        if (floor == null) return;
        StageConfig stage = floor.getStage(currentStage);
        if (stage == null) return;

        state = SessionState.ACTIVE;
        aliveMobs.clear();
        bossId = null;
        totalMobCount = 0;
        debugKillCount = 0;

        spawnMobs(stage);
        startSanityCheck();
        startTimer(stage.getTimeSeconds());
        broadcastTitle("&c&lWAVE STARTED!",
                "&7Kill &e95%&7 of mobs! Time: &e" + MessageUtils.formatTime(stage.getTimeSeconds()), 10, 60, 10);
    }

    private void startCountdown(int seconds, Runnable onComplete) {
        final int[] remaining = {seconds};
        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (remaining[0] <= 0) {
                    cancel();
                    onComplete.run();
                    return;
                }
                broadcastTitle("&c" + remaining[0], "&7Get ready...", 0, 25, 0);
                remaining[0]--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void beginStage() {
        FloorConfig floor = plugin.getConfigManager().getFloor(currentFloor);
        if (floor == null) return;

        StageConfig stage = floor.getStage(currentStage);
        if (stage == null) return;

        aliveMobs.clear();
        bossId = null;
        totalMobCount = 0;
        collectedItems = 0;
        killsSinceLastDrop = 0;
        debugKillCount = 0;
        stageCleared = false;

        switch (stage.getType()) {
            case LOBBY:
                state = SessionState.WAITING;
                break;
            case WAVE:
                state = SessionState.ACTIVE;
                teleportAllPlayers(stage.getWarp());
                spawnMobs(stage);
                startSanityCheck();
                startTimer(stage.getTimeSeconds());
                broadcastTitle("&c&lWAVE " + currentStage,
                        "&7Time: &e" + MessageUtils.formatTime(stage.getTimeSeconds()), 10, 40, 10);
                break;
            case BOSS:
                state = SessionState.BOSS;
                teleportAllPlayers(stage.getWarp());
                spawnMobs(stage);
                spawnBoss(stage);
                startSanityCheck();
                startTimer(stage.getTimeSeconds());
                broadcastTitle("&4&lBOSS", "&c" + formatMobName(stage.getBoss()), 10, 60, 10);
                break;
            case COLLECT:
                state = SessionState.COLLECTING;
                teleportAllPlayers(stage.getWarp());
                spawnMobs(stage);
                startSanityCheck();
                startCollectTimer(stage);
                broadcastTitle("&6&lCOLLECTING",
                        "&7Collect &e" + stage.getCollectAmount() + " &7items", 10, 40, 10);
                break;
        }
    }

    private void startSanityCheck() {
        if (sanityTask != null && !sanityTask.isCancelled()) sanityTask.cancel();

        sanityTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != SessionState.ACTIVE && state != SessionState.COLLECTING && state != SessionState.BOSS) return;

                Iterator<UUID> it = aliveMobs.iterator();
                boolean changed = false;

                while (it.hasNext()) {
                    UUID mobId = it.next();
                    if (!plugin.getMythicMobsIntegration().isMobValid(mobId)) {
                        it.remove();
                        // Zostawiamy totalMobCount bez zmian, aby zniknięte moby liczyły się jako postęp
                        changed = true;

                        if (plugin.getConfigManager().isDebugMode()) {
                            plugin.getLogger().info("[DT Sanity] Removed ghost mob: " + mobId);
                        }
                    }
                }

                if (changed) {
                    checkStageComplete();
                }
            }
        }.runTaskTimer(plugin, 100L, 100L);
    }

    public int getCollectedItems() { return collectedItems; }
    public int getKillsSinceLastDrop() { return killsSinceLastDrop; }
    public void incrementKillsSinceLastDrop() { this.killsSinceLastDrop++; }

    private void spawnMobs(StageConfig stage) {
        double spawnRadius = plugin.getConfigManager().getSpawnRadius() + 5.0;
        MythicMobsIntegration mythicMobs = plugin.getMythicMobsIntegration();

        for (Map.Entry<String, Integer> entry : stage.getMobs().entrySet()) {
            String mobType = entry.getKey();
            int countNeeded = entry.getValue();

            if (mythicMobs.isEliteMob(mobType)) {
                spawnElitesAtAllPoints(mobType, stage, spawnRadius);
            } else if (mythicMobs.isMiniBoss(mobType)) {
                if (stage.hasBoss()) spawnMiniBossAtPoints1And2(mobType, stage, spawnRadius);
                else spawnMiniBossAtPoint3(mobType, stage, spawnRadius);
            } else {
                int spawnedTotal = 0;

                if (stage.hasSpawnPoints()) {
                    List<SpawnPoint> points = stage.getMobSpawnPoints();
                    int pointsCount = points.size();

                    if (pointsCount > 0) {
                        int mobsPerPoint = countNeeded / pointsCount;

                        for (int i = 0; i < pointsCount; i++) {
                            int spawnedAtThisPoint = 0;
                            int attempts = 0;
                            int limitAttempts = (mobsPerPoint > 0 ? mobsPerPoint : 1) * 20;

                            while (spawnedAtThisPoint < mobsPerPoint && attempts < limitAttempts) {
                                attempts++;
                                Location loc = stage.getSpawnLocationAt(i, spawnRadius);
                                if (loc != null) {
                                    UUID mobId = plugin.getMythicMobsIntegration().spawnMobAtLocation(mobType, loc);
                                    if (mobId != null) {
                                        aliveMobs.add(mobId);
                                        totalMobCount++;
                                        spawnedAtThisPoint++;
                                        spawnedTotal++;
                                    }
                                }
                            }
                            if (spawnedAtThisPoint == 0 && mobsPerPoint > 0) {
                                plugin.getLogger().warning("[DemonTower] Spawn Point Index " + i + " failed to spawn any mobs! Check coordinates.");
                            }
                        }
                    }
                }

                int remaining = countNeeded - spawnedTotal;
                int attempts = 0;
                int maxAttempts = remaining * 50;

                while (remaining > 0 && attempts < maxAttempts) {
                    attempts++;
                    UUID mobId = spawnMobAtRandomPoint(mobType, stage, spawnRadius);
                    if (mobId != null) {
                        aliveMobs.add(mobId);
                        totalMobCount++;
                        spawnedTotal++;
                        remaining--;
                    } else if (attempts > maxAttempts - remaining) {
                        UUID forcedMobId = mythicMobs.spawnMob(mobType, stage.getWarp());
                        if (forcedMobId != null) {
                            aliveMobs.add(forcedMobId);
                            totalMobCount++;
                            spawnedTotal++;
                            remaining--;
                        }
                    }
                }

                if (spawnedTotal < countNeeded) {
                    plugin.getLogger().severe("[DemonTower] CRITICAL: Failed to spawn correct mob amount! Requested: " + countNeeded + ", Spawned: " + spawnedTotal);
                }
            }
        }
    }

    private void spawnElitesAtAllPoints(String mobType, StageConfig stage, double radius) {
        int pointCount = stage.getSpawnPointCount();
        for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
            for (int i = 0; i < 3; i++) {
                Location loc = stage.getSpawnLocationAt(pointIndex, radius);
                if (loc != null) {
                    UUID mobId = plugin.getMythicMobsIntegration().spawnMobAtLocation(mobType, loc);
                    if (mobId != null) {
                        aliveMobs.add(mobId);
                        totalMobCount++;
                    }
                }
            }
        }
    }

    private void spawnMiniBossAtPoint3(String mobType, StageConfig stage, double radius) {
        Location loc = stage.getSpawnLocationAt(2, radius);
        if (loc != null) {
            UUID mobId = plugin.getMythicMobsIntegration().spawnMobAtLocation(mobType, loc);
            if (mobId != null) {
                aliveMobs.add(mobId);
                totalMobCount++;
            }
        } else {
            UUID mobId = plugin.getMythicMobsIntegration().spawnMob(mobType, stage.getWarp());
            if (mobId != null) {
                aliveMobs.add(mobId);
                totalMobCount++;
            }
        }
    }

    private void spawnMiniBossAtPoints1And2(String mobType, StageConfig stage, double radius) {
        for (int pointIndex = 0; pointIndex < 2; pointIndex++) {
            Location loc = stage.getSpawnLocationAt(pointIndex, radius);
            if (loc != null) {
                UUID mobId = plugin.getMythicMobsIntegration().spawnMobAtLocation(mobType, loc);
                if (mobId != null) {
                    aliveMobs.add(mobId);
                    totalMobCount++;
                }
            }
        }
    }

    private UUID spawnMobAtRandomPoint(String mobType, StageConfig stage, double radius) {
        if (stage.hasSpawnPoints()) {
            Location loc = stage.getRandomMobSpawnLocation(radius);
            if (loc != null) {
                return plugin.getMythicMobsIntegration().spawnMobAtLocation(mobType, loc);
            }
        }
        return plugin.getMythicMobsIntegration().spawnMob(mobType, stage.getWarp());
    }

    private void spawnBoss(StageConfig stage) {
        if (stage.hasBoss()) {
            if (stage.hasBossSpawnPoint()) {
                Location loc = stage.getBossSpawnLocation();
                if (loc != null) {
                    bossId = plugin.getMythicMobsIntegration().spawnMobAtExactLocation(stage.getBoss(), loc);
                }
            }
            if (bossId == null) {
                bossId = plugin.getMythicMobsIntegration().spawnMob(stage.getBoss(), stage.getWarp());
            }
            if (bossId != null) {
                aliveMobs.add(bossId);
                totalMobCount++;
            }
            String formattedBossName = formatMobName(stage.getBoss());
            broadcastMessage(plugin.getConfigManager().getMessage("boss_spawned", "boss", formattedBossName));
        }
    }

    private String formatMobName(String mobId) {
        if (mobId == null || mobId.isEmpty()) return mobId;
        String[] words = mobId.replace("_", " ").split(" ");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
                if (i < words.length - 1) {
                    result.append(" ");
                }
            }
        }
        return result.toString();
    }

    private void startTimer(int seconds) {
        timeRemaining = seconds;
        timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (timeRemaining <= 0) {
                    cancel();
                    onTimeUp();
                    return;
                }
                int killedMobs = totalMobCount - aliveMobs.size();
                int percent = totalMobCount > 0 ? (int) ((killedMobs / (double) totalMobCount) * 100) : 0;
                broadcastActionBar("&cTime: " + MessageUtils.formatTimeColored(timeRemaining) +
                        " &7| &eMobs: &a" + killedMobs + "&7/&a" + totalMobCount + " &7(&e" + percent + "%&7)");

                if (timeRemaining == 60 || timeRemaining == 30 || timeRemaining == 10) {
                    broadcastTitle("&c" + timeRemaining, "&7seconds remaining!", 5, 20, 5);
                }
                if (timeRemaining <= 5) {
                    broadcastTitle("&4&l" + timeRemaining, "", 0, 20, 0);
                }
                timeRemaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void startCollectTimer(StageConfig stage) {
        timeRemaining = stage.getTimeSeconds();
        int requiredAmount = stage.getCollectAmount();

        timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (collectedItems >= requiredAmount) {
                    cancel();
                    return;
                }
                if (timeRemaining <= 0) {
                    cancel();
                    onTimeUp();
                    return;
                }
                int killedMobs = totalMobCount - aliveMobs.size();
                int percent = requiredAmount > 0 ? (int) ((collectedItems / (double) requiredAmount) * 100) : 0;
                broadcastActionBar("&cTime: " + MessageUtils.formatTimeColored(timeRemaining) +
                        " &7| &6Collected: &a" + collectedItems + "&7/&a" + requiredAmount + " &7(&e" + percent + "%&7)" +
                        " &7| &eMobs: &a" + killedMobs + "&7/&a" + totalMobCount);

                if (timeRemaining == 60 || timeRemaining == 30 || timeRemaining == 10) {
                    broadcastTitle("&c" + timeRemaining, "&7seconds remaining!", 5, 20, 5);
                }
                if (timeRemaining <= 5) {
                    broadcastTitle("&4&l" + timeRemaining, "", 0, 20, 0);
                }
                timeRemaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void stopTimer() {
        if (timerTask != null && !timerTask.isCancelled()) {
            timerTask.cancel();
            timerTask = null;
        }
    }

    private void onTimeUp() {
        broadcastTitle("&4&lTIME'S UP!", plugin.getConfigManager().getMessage("time_up"), 10, 60, 20);
        failSession();
    }

    public void onMobKilled(UUID mobId) {
        if (aliveMobs.remove(mobId)) {
            if (plugin.getConfigManager().isDebugMode()) {
                debugKillCount++;
                broadcastMessage("&7[DEBUG] Kill registered: " + debugKillCount + "/5");
                checkDebugStageComplete(mobId);
            } else {
                checkStageComplete();
            }
        }
    }

    private void checkDebugStageComplete(UUID killedMobId) {
        if (stageCleared) return;
        FloorConfig floor = plugin.getConfigManager().getFloor(currentFloor);
        if (floor == null) return;
        StageConfig stage = floor.getStage(currentStage);
        if (stage == null) return;

        boolean shouldComplete = false;
        String reason = "";

        if (killedMobId.equals(bossId)) {
            shouldComplete = true;
            reason = "&c[DEBUG] &aBoss killed - stage complete!";
        }
        if (!shouldComplete && debugKillCount >= 5) {
            shouldComplete = true;
            reason = "&c[DEBUG] &a5 mobs killed - stage complete!";
        }
        if (shouldComplete) {
            broadcastMessage(reason);

            // FIX: Zmieniona kolejność - najpierw czyścimy listę, potem zabijamy resztę.
            // Zapobiega to pętli błędów, gdzie wyjątek przy zabijaniu (killMobs) powodował,
            // że lista nigdy nie była czyszczona, a licznik rósł w nieskończoność (123/5).
            List<UUID> mobsToKill = new ArrayList<>(aliveMobs);
            aliveMobs.clear();

            try {
                plugin.getMythicMobsIntegration().killMobs(mobsToKill);
            } catch (Exception e) {
                // Logujemy błąd, ale pozwalamy na zakończenie etapu
                plugin.getLogger().warning("[DT Debug] Error killing remaining mobs: " + e.getMessage());
            }

            completeStage();
        }
    }

    public void onItemCollected(int amount) {
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[DemonTowerSession DEBUG] onItemCollected called with amount=" + amount);
        }

        killsSinceLastDrop = 0;

        FloorConfig floor = plugin.getConfigManager().getFloor(currentFloor);
        if (floor == null) return;
        StageConfig stage = floor.getStage(currentStage);
        if (stage == null || stage.getType() != StageType.COLLECT) return;

        collectedItems += amount;

        broadcastMessage("&e[DT] &7Collected item: &a" + collectedItems + "&7/&a" + stage.getCollectAmount());

        broadcastActionBar(plugin.getConfigManager().getMessage("collect_progress",
                "current", collectedItems, "required", stage.getCollectAmount()));

        if (collectedItems >= stage.getCollectAmount()) {
            broadcastMessage(plugin.getConfigManager().getMessage("collect_complete"));
            completeStage();
        }
    }

    private void checkStageComplete() {
        if (stageCleared) return;

        FloorConfig floor = plugin.getConfigManager().getFloor(currentFloor);
        if (floor == null) return;
        StageConfig stage = floor.getStage(currentStage);
        if (stage == null) return;

        if (stage.getType() == StageType.BOSS) {
            if (bossId != null && aliveMobs.contains(bossId)) {
                return;
            }
            broadcastMessage(plugin.getConfigManager().getMessage("boss_defeated"));
            completeStage();
            return;
        }

        if (stage.getType() == StageType.WAVE) {
            // FIX: Jeśli nie ma żywych mobów, etap MUSI się zakończyć.
            // Naprawia problem "123/123", gdzie błędy w matematyce procentowej mogły blokować postęp.
            if (aliveMobs.isEmpty()) {
                completeStage();
                return;
            }

            double completionPercentage = plugin.getConfigManager().getWaveCompletionPercentage();
            int killedMobs = totalMobCount - aliveMobs.size();
            double currentPercentage = totalMobCount > 0 ? (killedMobs / (double) totalMobCount) : 1.0;

            if (currentPercentage >= completionPercentage) {
                // Kopiujemy listę przed czyszczeniem dla bezpieczeństwa
                List<UUID> mobsToKill = new ArrayList<>(aliveMobs);
                aliveMobs.clear();
                try {
                    plugin.getMythicMobsIntegration().killMobs(mobsToKill);
                } catch (Exception e) {
                    plugin.getLogger().warning("Error clearing wave: " + e.getMessage());
                }
                completeStage();
            }
            return;
        }

        if (stage.getType() == StageType.COLLECT) {
            if (collectedItems >= stage.getCollectAmount()) {
                completeStage();
            } else {
                int mobsAlive = aliveMobs.size();
                int itemsNeeded = stage.getCollectAmount() - collectedItems;

                if (mobsAlive < itemsNeeded && mobsAlive == 0) {
                    broadcastMessage("&e&l[!] &7Respawning mobs to allow collection...");
                    spawnMobs(stage);
                }
            }
        }
    }

    private void completeStage() {
        stageCleared = true;
        broadcastTitle("&a&lSTAGE CLEARED!", "", 10, 40, 10);
        broadcastMessage(plugin.getConfigManager().getMessage("stage_cleared"));

        FloorConfig floor = plugin.getConfigManager().getFloor(currentFloor);
        if (floor == null) return;

        // Upewniamy się, że moby są martwe i lista czysta
        if (!aliveMobs.isEmpty()) {
            List<UUID> mobsToKill = new ArrayList<>(aliveMobs);
            aliveMobs.clear();
            plugin.getMythicMobsIntegration().killMobs(mobsToKill);
        }

        if (currentStage >= floor.getStageCount()) {
            stopTimer();
            completeFloor();
        } else {
            if (plugin.getConfigManager().isDebugMode()) {
                stopTimer();
                broadcastMessage("&c[DEBUG] &7Advancing to next stage immediately...");
                advanceToNextStage();
            } else {
                // FIX: Krótki czas oczekiwania (5s) zamiast pełnego timera
                stopTimer();
                stageWaitRemaining = 5;
                startStageWaitTimer();
            }
        }
    }

    private void startStageWaitTimer() {
        stageWaitTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (stageWaitRemaining <= 0) {
                    cancel();
                    advanceToNextStage();
                    return;
                }
                broadcastActionBar(plugin.getConfigManager().getMessage("waiting_next_stage",
                        "time", stageWaitRemaining));
                if (stageWaitRemaining <= 3) {
                    broadcastTitle("&e" + stageWaitRemaining, "&7Next stage starting...", 0, 20, 0);
                }
                stageWaitRemaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void advanceToNextStage() {
        stageCleared = false;
        currentStage++;
        beginStage();
    }

    private void completeFloor() {
        FloorConfig nextFloor = plugin.getConfigManager().getFloor(currentFloor + 1);
        if (nextFloor != null) {
            state = SessionState.FLOOR_COMPLETED;
            floorTransitionInitiated = false;
            broadcastTitle("&a&lFLOOR " + currentFloor + " COMPLETED!", "", 10, 60, 20);
            broadcastMessage(plugin.getConfigManager().getMessage("floor_complete", "floor", currentFloor));
            broadcastMessage("&7Click the &eNPC &7to access floor mechanics!");
            startMechanicTimeout();
        } else {
            state = SessionState.COMPLETED;
            broadcastTitle("&6&lVICTORY!", "&aDemon Tower completed!", 20, 100, 20);
            broadcastMessage("&7Click the &eNPC &7to access floor mechanics!");
            startMechanicTimeout();
        }
    }

    private void startMechanicTimeout() {
        mechanicTimeRemaining = plugin.getConfigManager().isDebugMode() ? 30 : MECHANIC_TIMEOUT;
        mechanicTimeoutTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (mechanicTimeRemaining <= 0) {
                    cancel();
                    onMechanicTimeout();
                    return;
                }
                if (mechanicTimeRemaining == 60) {
                    broadcastMessage("&c&l[!] &e1 minute &7remaining! Use the mechanic or advance!");
                } else if (mechanicTimeRemaining == 30) {
                    broadcastMessage("&c&l[!] &c30 seconds &7remaining!");
                } else if (mechanicTimeRemaining <= 10) {
                    broadcastTitle("&c" + mechanicTimeRemaining, "&7Time is running out!", 0, 20, 0);
                }
                mechanicTimeRemaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void stopMechanicTimeout() {
        if (mechanicTimeoutTask != null && !mechanicTimeoutTask.isCancelled()) {
            mechanicTimeoutTask.cancel();
            mechanicTimeoutTask = null;
        }
    }

    private void onMechanicTimeout() {
        broadcastTitle("&4&lTIME'S UP!", "&cYou are being teleported to spawn...", 10, 60, 20);
        broadcastMessage("&c&l[!] &7Mechanic time has ended! Session is being reset.");
        for (UUID playerId : players) {
            Player p = plugin.getServer().getPlayer(playerId);
            if (p != null && p.isOnline()) {
                p.closeInventory();
                plugin.getEssentialsIntegration().teleportToSpawn(p);
            }
        }
        endSession(false);
    }

    public int getMechanicTimeRemaining() {
        return mechanicTimeRemaining;
    }

    public boolean initiateFloorTransition(Player initiator) {
        if (floorTransitionInitiated) {
            MessageUtils.sendMessage(initiator, "&cTransition already in progress!");
            return false;
        }
        if (state != SessionState.FLOOR_COMPLETED) {
            MessageUtils.sendMessage(initiator, "&cCannot initiate transition now!");
            return false;
        }

        FloorConfig nextFloor = plugin.getConfigManager().getFloor(currentFloor + 1);
        if (nextFloor != null && nextFloor.requiresKey()) {
            for (UUID playerId : players) {
                Player player = plugin.getServer().getPlayer(playerId);
                if (player != null) {
                    plugin.getMythicMobsIntegration().removeItem(player, nextFloor.getRequiredKey(), 1);
                }
            }
        }

        floorTransitionInitiated = true;
        state = SessionState.FLOOR_TRANSITION;
        floorTransitionRemaining = FLOOR_TRANSITION_TIME;
        stopMechanicTimeout();

        broadcastTitle("&6&lTRANSITION INITIATED!",
                "&e" + initiator.getName() + " &7started the countdown", 10, 60, 20);
        broadcastMessage("&e" + initiator.getName() + " &7initiated transition to Floor " + (currentFloor + 1) + "!");
        broadcastMessage("&7You have &e" + FLOOR_TRANSITION_TIME + " seconds &7to use floor mechanics!");

        startFloorTransitionCountdown();
        return true;
    }

    private void startFloorTransitionCountdown() {
        floorTransitionTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (floorTransitionRemaining <= 0) {
                    cancel();
                    executeFloorTransition();
                    return;
                }
                broadcastActionBar("&6Transition in: &e" + floorTransitionRemaining + "s");
                if (floorTransitionRemaining == 30 || floorTransitionRemaining == 15) {
                    broadcastTitle("&e" + floorTransitionRemaining + "s", "&7until floor transition!", 5, 20, 5);
                }
                if (floorTransitionRemaining <= 5) {
                    broadcastTitle("&c&l" + floorTransitionRemaining, "&eMoving soon!", 0, 20, 0);
                }
                floorTransitionRemaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void executeFloorTransition() {
        for (UUID playerId : players) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                player.closeInventory();
            }
        }
        advanceToNextFloor();
    }

    public void advanceToNextFloor() {
        FloorConfig nextFloor = plugin.getConfigManager().getFloor(currentFloor + 1);
        if (nextFloor == null) {
            endSession(true);
            return;
        }

        currentFloor++;
        currentStage = 1;
        state = SessionState.WAITING;

        FloorConfig floor = plugin.getConfigManager().getFloor(currentFloor);
        StageConfig firstStage = floor.getFirstStage();
        String leaderName = getPartyLeaderName();
        broadcastMessage("&6[DT] &e" + leaderName + "&7's party is attempting &cfloor " + currentFloor + " &7of Demon Tower!");

        if (firstStage.getType() == StageType.LOBBY) {
            teleportAllPlayers(firstStage.getWarp());
        } else {
            beginStage();
        }
    }

    private void failSession() {
        state = SessionState.FAILED;
        for (UUID playerId : players) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                player.setHealth(0);
            }
        }
        plugin.getMythicMobsIntegration().killMobs(new ArrayList<>(aliveMobs));
        endSession(false);
    }

    public void endSession(boolean success) {
        stopAllTimers();
        plugin.getMythicMobsIntegration().killMobs(new ArrayList<>(aliveMobs));
        aliveMobs.clear();
        players.clear();
        state = success ? SessionState.COMPLETED : SessionState.FAILED;
        plugin.getSessionManager().endSession();
    }

    public void reset() {
        stopAllTimers();
        plugin.getMythicMobsIntegration().killMobs(new ArrayList<>(aliveMobs));
        aliveMobs.clear();
        currentFloor = 1;
        currentStage = 1;
        state = SessionState.WAITING;
        timeRemaining = 0;
        collectedItems = 0;
        totalMobCount = 0;
        bossId = null;
        partyLeader = null;
        lobbyCountdownStarted = false;
        stageCleared = false;
        stageWaitRemaining = 0;
        floorTransitionInitiated = false;
        floorTransitionRemaining = 0;
        debugKillCount = 0;
        mechanicTimeRemaining = 0;
    }

    private void stopAllTimers() {
        stopTimer();
        stopMechanicTimeout();
        if (sanityTask != null && !sanityTask.isCancelled()) {
            sanityTask.cancel();
            sanityTask = null;
        }
        if (countdownTask != null && !countdownTask.isCancelled()) {
            countdownTask.cancel();
            countdownTask = null;
        }
        if (lobbyCountdownTask != null && !lobbyCountdownTask.isCancelled()) {
            lobbyCountdownTask.cancel();
            lobbyCountdownTask = null;
        }
        if (stageWaitTask != null && !stageWaitTask.isCancelled()) {
            stageWaitTask.cancel();
            stageWaitTask = null;
        }
        if (floorTransitionTask != null && !floorTransitionTask.isCancelled()) {
            floorTransitionTask.cancel();
            floorTransitionTask = null;
        }
    }

    private void teleportAllPlayers(String warp) {
        for (UUID playerId : players) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                plugin.getEssentialsIntegration().warpPlayer(player, warp);
            }
        }
    }

    private void broadcastMessage(String message) {
        MessageUtils.sendMessage(getOnlinePlayers(), message);
    }

    private void broadcastTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        MessageUtils.sendTitle(getOnlinePlayers(), title, subtitle, fadeIn, stay, fadeOut);
    }

    private void broadcastActionBar(String message) {
        MessageUtils.sendActionBar(getOnlinePlayers(), message);
    }

    private Collection<Player> getOnlinePlayers() {
        List<Player> onlinePlayers = new ArrayList<>();
        for (UUID playerId : players) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null && player.isOnline()) {
                onlinePlayers.add(player);
            }
        }
        return onlinePlayers;
    }

    public Set<UUID> getPlayers() {
        return Collections.unmodifiableSet(players);
    }

    public UUID getPartyLeader() {
        return partyLeader;
    }

    public String getPartyLeaderName() {
        if (partyLeader == null) return "Unknown";
        Player leader = plugin.getServer().getPlayer(partyLeader);
        return leader != null ? leader.getName() : "Unknown";
    }

    public int getCurrentFloor() {
        return currentFloor;
    }

    public int getCurrentStage() {
        return currentStage;
    }

    public SessionState getState() {
        return state;
    }

    public int getTimeRemaining() {
        return timeRemaining;
    }

    public boolean hasPlayer(Player player) {
        return players.contains(player.getUniqueId());
    }

    public boolean isActive() {
        return state == SessionState.ACTIVE || state == SessionState.BOSS || state == SessionState.COLLECTING;
    }

    public boolean isFloorCompleted() {
        return state == SessionState.FLOOR_COMPLETED;
    }

    public boolean isFloorTransitionInProgress() {
        return state == SessionState.FLOOR_TRANSITION;
    }

    public boolean canUseMechanics() {
        return state == SessionState.FLOOR_COMPLETED || state == SessionState.FLOOR_TRANSITION;
    }

    public int getFloorTransitionRemaining() {
        return floorTransitionRemaining;
    }

    public boolean canJoin() {
        return state == SessionState.WAITING;
    }

    public int getPlayerCount() {
        return players.size();
    }

    public Set<UUID> getAliveMobs() {
        return Collections.unmodifiableSet(aliveMobs);
    }

    public int getTotalMobCount() {
        return totalMobCount;
    }

    public void startWave() {
        if (state != SessionState.WAITING) return;
        if (lobbyCountdownTask != null && !lobbyCountdownTask.isCancelled()) {
            lobbyCountdownTask.cancel();
            lobbyCountdownTask = null;
        }
        plugin.getSessionManager().setBlocked(true);
        FloorConfig floor = plugin.getConfigManager().getFloor(currentFloor);
        if (floor == null) return;
        StageConfig firstStage = floor.getFirstStage();
        if (firstStage != null && firstStage.getType() == StageType.LOBBY) {
            currentStage = 2;
        }
        startCountdown(5, this::beginStage);
    }
}