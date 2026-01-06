package pl.yourserver.demonTowerPlugin.config;

import java.util.List;

public class FloorConfig {
    private final int floor;
    private final int requiredLevel;
    private final String requiredKey;
    private final List<StageConfig> stages;

    public FloorConfig(int floor, int requiredLevel, String requiredKey, List<StageConfig> stages) {
        this.floor = floor;
        this.requiredLevel = requiredLevel;
        this.requiredKey = requiredKey;
        this.stages = stages;
    }

    public int getFloor() {
        return floor;
    }

    public int getRequiredLevel() {
        return requiredLevel;
    }

    public String getRequiredKey() {
        return requiredKey;
    }

    public boolean requiresKey() {
        return requiredKey != null && !requiredKey.isEmpty();
    }

    public List<StageConfig> getStages() {
        return stages;
    }

    public StageConfig getStage(int stageNumber) {
        if (stageNumber < 1 || stageNumber > stages.size()) {
            return null;
        }
        return stages.get(stageNumber - 1);
    }

    public int getStageCount() {
        return stages.size();
    }

    public StageConfig getFirstStage() {
        return stages.isEmpty() ? null : stages.get(0);
    }

    public StageConfig getLastStage() {
        return stages.isEmpty() ? null : stages.get(stages.size() - 1);
    }
}
