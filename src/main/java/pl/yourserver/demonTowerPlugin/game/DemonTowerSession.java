package pl.yourserver.demonTowerPlugin.game;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import pl.yourserver.demonTowerPlugin.DemonTowerPlugin;
import pl.yourserver.demonTowerPlugin.config.FloorConfig;
import pl.yourserver.demonTowerPlugin.config.StageConfig;
import pl.yourserver.demonTowerPlugin.integration.MythicMobsIntegration;
import pl.yourserver.demonTowerPlugin.utils.MessageUtils;

import java.util.*;

public class DemonTowerSession {

    private final DemonTowerPlugin plugin;
    private final Set<UUID> players = new HashSet<>();
    private final Set<UUID> aliveMobs = new HashSet<>();
    private UUID bossId = null;
    private UUID partyLeader = null; // The player who used the key to start the session

    private int currentFloor;
    private int currentStage;
    private SessionState state;
    private int timeRemaining;
    private int collectedItems;

    private int totalMobCount = 0;

    private BukkitTask timerTask;
    private BukkitTask countdownTask;
    private BukkitTask lobbyCountdownTask;
    private BukkitTask stageWaitTask;
    private BukkitTask floorTransitionTask;

    private boolean lobbyCountdownStarted = false;
    private boolean stageCleared = false;
    private int stageWaitRemaining = 0;
    private boolean floorTransitionInitiated = false;
    private int floorTransitionRemaining = 0;

    // Debug mode tracking
    private int debugKillCount = 0;

    // Mechanic timeout (5 min to use mechanic or advance)
    private BukkitTask mechanicTimeoutTask;
    private int mechanicTimeRemaining = 0;

    public static final int FLOOR_TRANSITION_TIME = 60; // seconds
    public static final int MECHANIC_TIMEOUT = 300; // 5 minutes

    public DemonTowerSession(DemonTowerPlugin plugin) {
        this.plugin = plugin;
        this.currentFloor = 1;
        this.currentStage = 1;
        this.state = SessionState.WAITING;
        this.timeRemaining = 0;
        this.collectedItems = 0;
    }

    public boolean addPlayer(Player player) {
        if (state != SessionState.WAITING) {
            MessageUtils.sendMessage(player, plugin.getConfigManager().getMessage("joining_disabled"));
            return false;
        }

        FloorConfig floor = plugin.getConfigManager().getFloor(currentFloor);
        if (floor == null) return false;

        // Check level requirement
        if (player.getLevel() < floor.getRequiredLevel()) {
            MessageUtils.sendMessage(player, plugin.getConfigManager().getMessage("not_enough_level", "level", floor.getRequiredLevel()));
            return false;
        }

        // Check key requirement
        if (floor.requiresKey()) {
            if (!plugin.getMythicMobsIntegration().hasItem(player, floor.getRequiredKey())) {
                MessageUtils.sendMessage(player, plugin.getConfigManager().getMessage("missing_key", "key", floor.getRequiredKey()));
                return false;
            }
        }

        if (players.add(player.getUniqueId())) {
            // First player becomes party leader (the one who used the key)
            if (partyLeader == null) {
                partyLeader = player.getUniqueId();
            }

            // Teleport to lobby
            StageConfig lobbyStage = floor.getFirstStage();
            if (lobbyStage != null) {
                plugin.getEssentialsIntegration().warpPlayer(player, lobbyStage.getWarp());
            }

            // Send join message only to the player who joined (not to whole server/session)
            MessageUtils.sendMessage(player, plugin.getConfigManager().getMessage("player_joined", "player", player.getName()));

            // Start lobby countdown when first player joins
            if (!lobbyCountdownStarted && lobbyStage != null && lobbyStage.getType() == StageType.LOBBY) {
                startLobbyCountdown();
            }

            return true;
        }
        return false;
    }

    public void removePlayer(UUID playerId) {
        if (players.remove(playerId)) {
            // If no players left, end session
            if (players.isEmpty()) {
                endSession(false);
            }
        }
    }

    private void startLobbyCountdown() {
        if (lobbyCountdownStarted) return;
        lobbyCountdownStarted = true;

        // In debug mode, lobby is only 5 seconds
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

                // Show countdown in action bar
                broadcastActionBar(plugin.getConfigManager().getMessage("lobby_countdown",
                    "time", MessageUtils.formatTime(remaining[0])));

                // Warning titles at specific times
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
        // Block new players from joining
        plugin.getSessionManager().setBlocked(true);

        // Start wave on the same lobby stage (don't skip to stage 2)
        FloorConfig floor = plugin.getConfigManager().getFloor(currentFloor);
        if (floor == null) return;

        // currentStage stays at 1 (LOBBY) - we spawn mobs here
        startCountdown(5, this::beginLobbyWave);
    }

    private void beginLobbyWave() {
        FloorConfig floor = plugin.getConfigManager().getFloor(currentFloor);
        if (floor == null) return;

        StageConfig stage = floor.getStage(currentStage); // Still stage 1 (LOBBY)
        if (stage == null) return;

        // Start wave on lobby area
        state = SessionState.ACTIVE;
        aliveMobs.clear();
        bossId = null;
        totalMobCount = 0;
        debugKillCount = 0; // Reset debug kill count

        spawnMobs(stage);
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
        debugKillCount = 0; // Reset debug kill count

        switch (stage.getType()) {
            case LOBBY:
                state = SessionState.WAITING;
                break;
            case WAVE:
                state = SessionState.ACTIVE;
                teleportAllPlayers(stage.getWarp());
                spawnMobs(stage);
                startTimer(stage.getTimeSeconds());
                broadcastTitle("&c&lWAVE " + currentStage,
                    "&7Time: &e" + MessageUtils.formatTime(stage.getTimeSeconds()), 10, 40, 10);
                break;
            case BOSS:
                state = SessionState.BOSS;
                teleportAllPlayers(stage.getWarp());
                spawnMobs(stage);
                spawnBoss(stage);
                startTimer(stage.getTimeSeconds());
                // Format boss name nicely for title
                broadcastTitle("&4&lBOSS", "&c" + formatMobName(stage.getBoss()), 10, 60, 10);
                break;
            case COLLECT:
                state = SessionState.COLLECTING;
                teleportAllPlayers(stage.getWarp());
                spawnMobs(stage);
                // Start timer for COLLECT stages too - shows progress in action bar
                startCollectTimer(stage);
                broadcastTitle("&6&lCOLLECTING",
                    "&7Collect &e" + stage.getCollectAmount() + " &7items", 10, 40, 10);
                break;
        }
    }

    private void spawnMobs(StageConfig stage) {
        double spawnRadius = plugin.getConfigManager().getSpawnRadius();
        MythicMobsIntegration mythicMobs = plugin.getMythicMobsIntegration();

        for (Map.Entry<String, Integer> entry : stage.getMobs().entrySet()) {
            String mobType = entry.getKey();
            int count = entry.getValue();

            if (mythicMobs.isEliteMob(mobType)) {
                // Elites: spawn 3 at EACH of the 3 spawn points = 9 total
                spawnElitesAtAllPoints(mobType, stage, spawnRadius);
            } else if (mythicMobs.isMiniBoss(mobType)) {
                if (stage.hasBoss()) {
                    // Boss stage: spawn 1 mini boss at point 1 and 1 at point 2
                    spawnMiniBossAtPoints1And2(mobType, stage, spawnRadius);
                } else {
                    // Non-boss stage: spawn 1 mini boss at point 3 (index 2)
                    spawnMiniBossAtPoint3(mobType, stage, spawnRadius);
                }
            } else {
                // Normal mobs: spawn randomly across all points
                for (int i = 0; i < count; i++) {
                    UUID mobId = spawnMobAtRandomPoint(mobType, stage, spawnRadius);
                    if (mobId != null) {
                        aliveMobs.add(mobId);
                        totalMobCount++;
                    }
                }
            }
        }
    }

    /**
     * Spawn elites: 3 at each spawn point (9 total if 3 points)
     */
    private void spawnElitesAtAllPoints(String mobType, StageConfig stage, double radius) {
        int pointCount = stage.getSpawnPointCount();
        for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
            // Spawn 3 elites at each point
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

    /**
     * Spawn mini boss: 1 at point 3 (index 2) - for non-boss stages
     */
    private void spawnMiniBossAtPoint3(String mobType, StageConfig stage, double radius) {
        // Point 3 is index 2 (0-based)
        Location loc = stage.getSpawnLocationAt(2, radius);
        if (loc != null) {
            UUID mobId = plugin.getMythicMobsIntegration().spawnMobAtLocation(mobType, loc);
            if (mobId != null) {
                aliveMobs.add(mobId);
                totalMobCount++;
                plugin.getLogger().info("Spawned mini-boss " + mobType + " at point 3");
            }
        } else {
            // Fallback to warp if point 3 doesn't exist
            UUID mobId = plugin.getMythicMobsIntegration().spawnMob(mobType, stage.getWarp());
            if (mobId != null) {
                aliveMobs.add(mobId);
                totalMobCount++;
            }
        }
    }

    /**
     * Spawn mini bosses at points 1 and 2 (for boss stages) - 2 total
     */
    private void spawnMiniBossAtPoints1And2(String mobType, StageConfig stage, double radius) {
        // Point 1 is index 0, Point 2 is index 1
        for (int pointIndex = 0; pointIndex < 2; pointIndex++) {
            Location loc = stage.getSpawnLocationAt(pointIndex, radius);
            if (loc != null) {
                UUID mobId = plugin.getMythicMobsIntegration().spawnMobAtLocation(mobType, loc);
                if (mobId != null) {
                    aliveMobs.add(mobId);
                    totalMobCount++;
                    plugin.getLogger().info("Spawned mini-boss " + mobType + " at point " + (pointIndex + 1));
                }
            }
        }
    }

    /**
     * Spawn normal mob at a random spawn point
     */
    private UUID spawnMobAtRandomPoint(String mobType, StageConfig stage, double radius) {
        if (stage.hasSpawnPoints()) {
            Location loc = stage.getRandomMobSpawnLocation(radius);
            if (loc != null) {
                return plugin.getMythicMobsIntegration().spawnMobAtLocation(mobType, loc);
            }
        }
        // Fallback to warp location
        return plugin.getMythicMobsIntegration().spawnMob(mobType, stage.getWarp());
    }

    private void spawnBoss(StageConfig stage) {
        if (stage.hasBoss()) {
            // Use boss spawn point if configured - spawn at EXACT location (no offset)
            if (stage.hasBossSpawnPoint()) {
                Location loc = stage.getBossSpawnLocation();
                if (loc != null) {
                    bossId = plugin.getMythicMobsIntegration().spawnMobAtExactLocation(stage.getBoss(), loc);
                    plugin.getLogger().info("Spawned boss " + stage.getBoss() + " at exact location: " +
                        loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
                }
            }
            // Fallback to warp
            if (bossId == null) {
                bossId = plugin.getMythicMobsIntegration().spawnMob(stage.getBoss(), stage.getWarp());
                plugin.getLogger().warning("Boss spawn point not found, using warp fallback for: " + stage.getBoss());
            }

            if (bossId != null) {
                aliveMobs.add(bossId);
                totalMobCount++;
            }
            // Format boss name nicely (remove underscores, capitalize words)
            String formattedBossName = formatMobName(stage.getBoss());
            broadcastMessage(plugin.getConfigManager().getMessage("boss_spawned", "boss", formattedBossName));
        }
    }

    /**
     * Format a MythicMobs mob ID into a nice display name.
     * Converts "valgroth_dark_monarch" to "Valgroth Dark Monarch"
     */
    private String formatMobName(String mobId) {
        if (mobId == null || mobId.isEmpty()) return mobId;

        // Replace underscores with spaces and capitalize each word
        String[] words = mobId.replace("_", " ").split(" ");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (!word.isEmpty()) {
                // Capitalize first letter, lowercase rest
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

                // Show time and progress in action bar
                int killedMobs = totalMobCount - aliveMobs.size();
                int percent = totalMobCount > 0 ? (int) ((killedMobs / (double) totalMobCount) * 100) : 0;
                broadcastActionBar("&cTime: " + MessageUtils.formatTimeColored(timeRemaining) +
                    " &7| &eMobs: &a" + killedMobs + "&7/&a" + totalMobCount + " &7(&e" + percent + "%&7)");

                // Warning titles at specific times
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

    /**
     * Start a collect progress timer that shows progress in action bar
     */
    private void startCollectTimer(StageConfig stage) {
        timeRemaining = stage.getTimeSeconds();
        int requiredAmount = stage.getCollectAmount();

        timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Check if collect stage is complete
                if (collectedItems >= requiredAmount) {
                    cancel();
                    return;
                }

                if (timeRemaining <= 0) {
                    cancel();
                    onTimeUp();
                    return;
                }

                // Show collect progress in action bar
                int killedMobs = totalMobCount - aliveMobs.size();
                int percent = requiredAmount > 0 ? (int) ((collectedItems / (double) requiredAmount) * 100) : 0;
                broadcastActionBar("&cTime: " + MessageUtils.formatTimeColored(timeRemaining) +
                    " &7| &6Collected: &a" + collectedItems + "&7/&a" + requiredAmount + " &7(&e" + percent + "%&7)" +
                    " &7| &eMobs: &a" + killedMobs + "&7/&a" + totalMobCount);

                // Warning titles at specific times
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
            // In debug mode, check for quick stage completion
            if (plugin.getConfigManager().isDebugMode()) {
                debugKillCount++;
                checkDebugStageComplete(mobId);
            } else {
                checkStageComplete();
            }
        }
    }

    /**
     * Debug mode: stage completes immediately after killing boss, mini-boss, or 5 mobs
     */
    private void checkDebugStageComplete(UUID killedMobId) {
        if (stageCleared) return; // Already cleared

        FloorConfig floor = plugin.getConfigManager().getFloor(currentFloor);
        if (floor == null) return;

        StageConfig stage = floor.getStage(currentStage);
        if (stage == null) return;

        boolean shouldComplete = false;
        String reason = "";

        // Check if boss was killed
        if (killedMobId.equals(bossId)) {
            shouldComplete = true;
            reason = "&c[DEBUG] &aBoss killed - stage complete!";
        }

        // Check if 5+ mobs killed
        if (!shouldComplete && debugKillCount >= 5) {
            shouldComplete = true;
            reason = "&c[DEBUG] &a5 mobs killed - stage complete!";
        }

        if (shouldComplete) {
            broadcastMessage(reason);
            // Kill remaining mobs
            plugin.getMythicMobsIntegration().killMobs(aliveMobs);
            aliveMobs.clear();
            completeStage();
        }
    }

    public int getCollectedItems() {
        return collectedItems;
    }

    public void onItemCollected(int amount) {
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[DemonTowerSession DEBUG] onItemCollected called with amount=" + amount);
        }

        FloorConfig floor = plugin.getConfigManager().getFloor(currentFloor);
        if (floor == null) {
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("[DemonTowerSession DEBUG] Floor is null, returning");
            }
            return;
        }

        StageConfig stage = floor.getStage(currentStage);
        if (stage == null || stage.getType() != StageType.COLLECT) {
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("[DemonTowerSession DEBUG] Stage is null or not COLLECT type, returning");
            }
            return;
        }

        collectedItems += amount;
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[DemonTowerSession DEBUG] Collected items: " + collectedItems + "/" + stage.getCollectAmount());
        }

        // ZMIANA: Dodano wiadomość na czacie (cyferki)
        broadcastMessage("&e[DT] &7Collected item: &a" + collectedItems + "&7/&a" + stage.getCollectAmount());

        broadcastActionBar(plugin.getConfigManager().getMessage("collect_progress",
            "current", collectedItems, "required", stage.getCollectAmount()));

        if (collectedItems >= stage.getCollectAmount()) {
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("[DemonTowerSession DEBUG] Collect complete! Completing stage.");
            }
            broadcastMessage(plugin.getConfigManager().getMessage("collect_complete"));
            completeStage();
        }
    }

    private void checkStageComplete() {
        FloorConfig floor = plugin.getConfigManager().getFloor(currentFloor);
        if (floor == null) return;

        StageConfig stage = floor.getStage(currentStage);
        if (stage == null) return;

        // For BOSS stages - boss MUST be killed (100%)
        if (stage.getType() == StageType.BOSS) {
            // Boss must be dead
            if (bossId != null && aliveMobs.contains(bossId)) {
                return; // Boss still alive
            }
            // Boss is dead - stage complete
            broadcastMessage(plugin.getConfigManager().getMessage("boss_defeated"));
            completeStage();
            return;
        }

        // For WAVE stages - 95% of mobs must be killed
        if (stage.getType() == StageType.WAVE) {
            double completionPercentage = plugin.getConfigManager().getWaveCompletionPercentage();
            int killedMobs = totalMobCount - aliveMobs.size();
            double currentPercentage = totalMobCount > 0 ? (killedMobs / (double) totalMobCount) : 1.0;

            if (currentPercentage >= completionPercentage) {
                // Kill remaining mobs
                plugin.getMythicMobsIntegration().killMobs(aliveMobs);
                aliveMobs.clear();
                completeStage();
            }
            return;
        }

        // For COLLECT stages
        if (stage.getType() == StageType.COLLECT) {
            if (collectedItems >= stage.getCollectAmount()) {
                completeStage();
            } else {
                // Check if 80% of mobs killed but not enough drops - respawn mobs
                int killedMobs = totalMobCount - aliveMobs.size();
                double currentPercentage = totalMobCount > 0 ? (killedMobs / (double) totalMobCount) : 0.0;

                if (currentPercentage >= 0.80) {
                    // Kill remaining mobs and respawn all
                    plugin.getMythicMobsIntegration().killMobs(aliveMobs);
                    aliveMobs.clear();
                    totalMobCount = 0;

                    broadcastMessage("&e&l[!] &7Not enough drops collected. Respawning mobs...");
                    broadcastTitle("&6RESPAWNING", "&7Collect more items!", 10, 40, 10);

                    // Respawn mobs after a short delay
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            spawnMobs(stage);
                        }
                    }.runTaskLater(plugin, 40L); // 2 second delay
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

        // Kill any remaining mobs
        plugin.getMythicMobsIntegration().killMobs(aliveMobs);
        aliveMobs.clear();

        // Check if this was the last stage of the floor
        if (currentStage >= floor.getStageCount()) {
            stopTimer();
            completeFloor();
        } else {
            // In debug mode, advance immediately without waiting
            if (plugin.getConfigManager().isDebugMode()) {
                stopTimer();
                broadcastMessage("&c[DEBUG] &7Advancing to next stage immediately...");
                advanceToNextStage();
            } else {
                // Must wait full stage time even if cleared early
                // If timer is still running, wait for it to complete
                if (timeRemaining > 0) {
                    stageWaitRemaining = timeRemaining;
                    stopTimer();
                    startStageWaitTimer();
                } else {
                    // Timer already expired, advance immediately
                    advanceToNextStage();
                }
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

                // Show waiting countdown
                broadcastActionBar(plugin.getConfigManager().getMessage("waiting_next_stage",
                    "time", stageWaitRemaining));

                if (stageWaitRemaining <= 5) {
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
        // Check if there's a next floor
        FloorConfig nextFloor = plugin.getConfigManager().getFloor(currentFloor + 1);
        if (nextFloor != null) {
            // Floor completed - players can now use mechanics and initiate transition
            state = SessionState.FLOOR_COMPLETED;
            floorTransitionInitiated = false;

            broadcastTitle("&a&lFLOOR " + currentFloor + " COMPLETED!", "", 10, 60, 20);
            broadcastMessage(plugin.getConfigManager().getMessage("floor_complete", "floor", currentFloor));
            broadcastMessage("&7Click the &eNPC &7to access floor mechanics!");

            // Start mechanic timeout (5 minutes)
            startMechanicTimeout();

            // Don't auto-open GUI - player must click NPC or use /dt_gui command
        } else {
            // This was the final floor - victory!
            state = SessionState.COMPLETED;
            broadcastTitle("&6&lVICTORY!", "&aDemon Tower completed!", 20, 100, 20);
            broadcastMessage("&7Click the &eNPC &7to access floor mechanics!");

            // Start mechanic timeout for final floor too
            startMechanicTimeout();
            // Don't auto-open GUI - player must click NPC or use /dt_gui command
        }
    }

    private void startMechanicTimeout() {
        // In debug mode, timeout is 30 seconds instead of 5 minutes
        mechanicTimeRemaining = plugin.getConfigManager().isDebugMode() ? 30 : MECHANIC_TIMEOUT;

        mechanicTimeoutTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (mechanicTimeRemaining <= 0) {
                    cancel();
                    onMechanicTimeout();
                    return;
                }

                // Warnings at specific times
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

        // Teleport all players to spawn
        for (UUID playerId : players) {
            Player p = plugin.getServer().getPlayer(playerId);
            if (p != null && p.isOnline()) {
                p.closeInventory();
                plugin.getEssentialsIntegration().teleportToSpawn(p);
            }
        }

        // End session
        endSession(false);
    }

    public int getMechanicTimeRemaining() {
        return mechanicTimeRemaining;
    }

    /**
     * Initiate floor transition countdown.
     * Called when a player clicks "Advance" in the transition GUI.
     * After FLOOR_TRANSITION_TIME seconds, all players are moved to the next floor.
     *
     * @param initiator The player who initiated the transition
     * @return true if transition was initiated, false if already initiated
     */
    public boolean initiateFloorTransition(Player initiator) {
        if (floorTransitionInitiated) {
            MessageUtils.sendMessage(initiator, "&cTransition already in progress!");
            return false;
        }

        if (state != SessionState.FLOOR_COMPLETED) {
            MessageUtils.sendMessage(initiator, "&cCannot initiate transition now!");
            return false;
        }

        // FIX: Remove keys IMMEDIATELY when transition is initiated (not after 60s)
        // This prevents players from hiding/giving keys for free passage
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

        // Stop mechanic timeout since transition was initiated
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

                // Show countdown in action bar
                broadcastActionBar("&6Transition in: &e" + floorTransitionRemaining + "s");

                // Warning titles at specific times
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
        // Close any open GUIs for players
        for (UUID playerId : players) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                player.closeInventory();
            }
        }

        // Advance to next floor
        advanceToNextFloor();
    }

    public void advanceToNextFloor() {
        FloorConfig nextFloor = plugin.getConfigManager().getFloor(currentFloor + 1);
        if (nextFloor == null) {
            endSession(true);
            return;
        }

        // Keys are now removed in initiateFloorTransition() immediately
        // No longer need to remove keys here (after 60s delay)

        currentFloor++;
        currentStage = 1;
        state = SessionState.WAITING;

        FloorConfig floor = plugin.getConfigManager().getFloor(currentFloor);
        StageConfig firstStage = floor.getFirstStage();

        // Get party leader name for the message
        String leaderName = getPartyLeaderName();
        broadcastMessage("&6[DT] &e" + leaderName + "&7's party is attempting &cfloor " + currentFloor + " &7of Demon Tower!");

        if (firstStage.getType() == StageType.LOBBY) {
            // Wait in lobby
            teleportAllPlayers(firstStage.getWarp());
        } else {
            // Start directly
            beginStage();
        }
    }

    private void failSession() {
        state = SessionState.FAILED;

        // Kill all players
        for (UUID playerId : players) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                player.setHealth(0);
            }
        }

        // Kill remaining mobs
        plugin.getMythicMobsIntegration().killMobs(aliveMobs);

        endSession(false);
    }

    public void endSession(boolean success) {
        stopAllTimers();

        // Kill remaining mobs
        plugin.getMythicMobsIntegration().killMobs(aliveMobs);
        aliveMobs.clear();

        players.clear();
        state = success ? SessionState.COMPLETED : SessionState.FAILED;

        plugin.getSessionManager().endSession();
    }

    public void reset() {
        stopAllTimers();

        plugin.getMythicMobsIntegration().killMobs(aliveMobs);
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

    // Broadcast methods
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

    // Getters
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

    /**
     * Force start the wave (skip lobby countdown)
     * Used by admin commands
     */
    public void startWave() {
        if (state != SessionState.WAITING) return;

        // Cancel lobby countdown if running
        if (lobbyCountdownTask != null && !lobbyCountdownTask.isCancelled()) {
            lobbyCountdownTask.cancel();
            lobbyCountdownTask = null;
        }

        // Block new players
        plugin.getSessionManager().setBlocked(true);

        // Start with countdown
        FloorConfig floor = plugin.getConfigManager().getFloor(currentFloor);
        if (floor == null) return;

        StageConfig firstStage = floor.getFirstStage();
        if (firstStage != null && firstStage.getType() == StageType.LOBBY) {
            currentStage = 2; // Skip lobby
        }

        startCountdown(5, this::beginStage);
    }
}
