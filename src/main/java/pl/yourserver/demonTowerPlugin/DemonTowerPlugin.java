package pl.yourserver.demonTowerPlugin;

import org.bukkit.plugin.java.JavaPlugin;
import pl.yourserver.demonTowerPlugin.commands.*;
import pl.yourserver.demonTowerPlugin.config.ConfigManager;
import pl.yourserver.demonTowerPlugin.database.DatabaseManager;
import pl.yourserver.demonTowerPlugin.database.FloorDropsManager;
import pl.yourserver.demonTowerPlugin.game.SessionManager;
import pl.yourserver.demonTowerPlugin.gui.GuiManager;
import pl.yourserver.demonTowerPlugin.integration.EssentialsIntegration;
import pl.yourserver.demonTowerPlugin.integration.MythicMobsIntegration;
import pl.yourserver.demonTowerPlugin.integration.VaultIntegration;
import pl.yourserver.demonTowerPlugin.mechanics.ItemManipulator;
import pl.yourserver.demonTowerPlugin.listeners.GuiListener;
import pl.yourserver.demonTowerPlugin.listeners.MobDeathListener;
import pl.yourserver.demonTowerPlugin.listeners.PlayerListener;
import pl.yourserver.demonTowerPlugin.listeners.UltimateItemListener;

public final class DemonTowerPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private SessionManager sessionManager;
    private GuiManager guiManager;
    private MythicMobsIntegration mythicMobsIntegration;
    private EssentialsIntegration essentialsIntegration;
    private VaultIntegration vaultIntegration;
    private ItemManipulator itemManipulator;
    private DatabaseManager databaseManager;
    private FloorDropsManager floorDropsManager;

    @Override
    public void onEnable() {
        // Initialize managers
        configManager = new ConfigManager(this);
        configManager.loadConfig();

        sessionManager = new SessionManager(this);
        guiManager = new GuiManager(this);

        // Initialize integrations
        mythicMobsIntegration = new MythicMobsIntegration(this);
        mythicMobsIntegration.setup();

        essentialsIntegration = new EssentialsIntegration(this);
        essentialsIntegration.setup();

        vaultIntegration = new VaultIntegration(this);
        vaultIntegration.setup();

        itemManipulator = new ItemManipulator(this);

        // Initialize database
        databaseManager = new DatabaseManager(this);
        databaseManager.setup();

        // Initialize floor drops manager
        floorDropsManager = new FloorDropsManager(this, databaseManager);
        floorDropsManager.loadAllDrops();

        // Register commands
        if (getCommand("dt_join") != null) {
            getCommand("dt_join").setExecutor(new DTJoinCommand(this));
        }

        if (getCommand("dt_reset") != null) {
            getCommand("dt_reset").setExecutor(new DTResetCommand(this));
        }

        if (getCommand("dt_reload") != null) {
            getCommand("dt_reload").setExecutor(new DTReloadCommand(this));
        }

        if (getCommand("whododt") != null) {
            getCommand("whododt").setExecutor(new WhoDoDTCommand(this));
        }

        if (getCommand("dt_drops") != null) {
            getCommand("dt_drops").setExecutor(new DTDropsCommand(this));
        }

        // Register mechanic commands (for NPC integration and testing)
        if (getCommand("dt_blacksmith") != null) {
            getCommand("dt_blacksmith").setExecutor(new DTBlacksmithCommand(this));
        }

        if (getCommand("dt_smelter") != null) {
            getCommand("dt_smelter").setExecutor(new DTSmelterCommand(this));
        }

        if (getCommand("dt_divine") != null) {
            getCommand("dt_divine").setExecutor(new DTDivineCommand(this));
        }

        if (getCommand("dt_crucible") != null) {
            getCommand("dt_crucible").setExecutor(new DTCrucibleCommand(this));
        }

        if (getCommand("dt_gui") != null) {
            getCommand("dt_gui").setExecutor(new DTGuiCommand(this));
        }

        // Register listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new UltimateItemListener(this), this);

        // Only register MobDeathListener if MythicMobs is available
        if (mythicMobsIntegration.isEnabled()) {
            getServer().getPluginManager().registerEvents(new MobDeathListener(this), this);
        }

        getLogger().info("DemonTower Plugin enabled!");
        getLogger().info("Loaded " + configManager.getFloorCount() + " floors.");
        getLogger().info("Wave completion: " + (int)(configManager.getWaveCompletionPercentage() * 100) + "%");
    }

    @Override
    public void onDisable() {
        // Clean up active session
        if (sessionManager != null && sessionManager.hasSession()) {
            sessionManager.resetSession();
        }

        // Close database connection
        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info("DemonTower Plugin disabled!");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public MythicMobsIntegration getMythicMobsIntegration() {
        return mythicMobsIntegration;
    }

    public EssentialsIntegration getEssentialsIntegration() {
        return essentialsIntegration;
    }

    public VaultIntegration getVaultIntegration() {
        return vaultIntegration;
    }

    public ItemManipulator getItemManipulator() {
        return itemManipulator;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public FloorDropsManager getFloorDropsManager() {
        return floorDropsManager;
    }
}
