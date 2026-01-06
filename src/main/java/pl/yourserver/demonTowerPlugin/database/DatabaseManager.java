package pl.yourserver.demonTowerPlugin.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import pl.yourserver.demonTowerPlugin.DemonTowerPlugin;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class DatabaseManager {

    private final DemonTowerPlugin plugin;
    private HikariDataSource dataSource;
    private boolean enabled = false;

    public DatabaseManager(DemonTowerPlugin plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        FileConfiguration config = plugin.getConfig();

        if (!config.getBoolean("database.enabled", false)) {
            plugin.getLogger().info("Database is disabled in config. Floor drops will not be saved.");
            return;
        }

        String host = config.getString("database.host", "localhost");
        int port = config.getInt("database.port", 3306);
        String database = config.getString("database.database", "demontower");
        String username = config.getString("database.username", "root");
        String password = config.getString("database.password", "");
        int poolSize = config.getInt("database.pool_size", 10);

        try {
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true");
            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);
            hikariConfig.setMaximumPoolSize(poolSize);
            hikariConfig.setMinimumIdle(2);
            hikariConfig.setIdleTimeout(60000);
            hikariConfig.setConnectionTimeout(30000);
            hikariConfig.setMaxLifetime(1800000);
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            dataSource = new HikariDataSource(hikariConfig);
            enabled = true;

            createTables();
            plugin.getLogger().info("Database connection established successfully!");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to database!", e);
            enabled = false;
        }
    }

    private void createTables() {
        String sql = """
            CREATE TABLE IF NOT EXISTS dt_floor_drops (
                id INT AUTO_INCREMENT PRIMARY KEY,
                floor_number INT NOT NULL,
                slot_index INT NOT NULL,
                item_data LONGTEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                UNIQUE KEY unique_floor_slot (floor_number, slot_index)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.execute();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create database tables!", e);
        }
    }

    public Connection getConnection() throws SQLException {
        if (!enabled || dataSource == null) {
            throw new SQLException("Database is not enabled or connected!");
        }
        return dataSource.getConnection();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database connection closed.");
        }
    }

    // Item serialization to Base64
    public String serializeItem(ItemStack item) {
        if (item == null) return null;

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeObject(item);
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to serialize item!", e);
            return null;
        }
    }

    // Item deserialization from Base64
    public ItemStack deserializeItem(String data) {
        if (data == null || data.isEmpty()) return null;

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            return (ItemStack) dataInput.readObject();
        } catch (IOException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to deserialize item!", e);
            return null;
        }
    }

    // Save a single drop item for a floor
    public void saveFloorDrop(int floor, int slot, ItemStack item) {
        if (!enabled) return;

        String sql = """
            INSERT INTO dt_floor_drops (floor_number, slot_index, item_data)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE item_data = VALUES(item_data), updated_at = CURRENT_TIMESTAMP
            """;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, floor);
                stmt.setInt(2, slot);
                stmt.setString(3, serializeItem(item));
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save floor drop!", e);
            }
        });
    }

    // Delete a single drop item
    public void deleteFloorDrop(int floor, int slot) {
        if (!enabled) return;

        String sql = "DELETE FROM dt_floor_drops WHERE floor_number = ? AND slot_index = ?";

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, floor);
                stmt.setInt(2, slot);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete floor drop!", e);
            }
        });
    }

    // Load all drops for a specific floor
    public List<FloorDropEntry> loadFloorDrops(int floor) {
        List<FloorDropEntry> drops = new ArrayList<>();
        if (!enabled) return drops;

        String sql = "SELECT slot_index, item_data FROM dt_floor_drops WHERE floor_number = ? ORDER BY slot_index";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, floor);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int slot = rs.getInt("slot_index");
                    String itemData = rs.getString("item_data");
                    ItemStack item = deserializeItem(itemData);
                    if (item != null) {
                        drops.add(new FloorDropEntry(slot, item));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load floor drops for floor " + floor + "!", e);
        }

        return drops;
    }

    // Save all drops for a floor (batch operation)
    public void saveAllFloorDrops(int floor, List<FloorDropEntry> drops) {
        if (!enabled) return;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            // First, delete all existing drops for this floor
            String deleteSql = "DELETE FROM dt_floor_drops WHERE floor_number = ?";
            try (Connection conn = getConnection();
                 PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                deleteStmt.setInt(1, floor);
                deleteStmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to clear floor drops!", e);
                return;
            }

            // Then insert all new drops
            if (drops.isEmpty()) return;

            String insertSql = "INSERT INTO dt_floor_drops (floor_number, slot_index, item_data) VALUES (?, ?, ?)";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(insertSql)) {

                for (FloorDropEntry entry : drops) {
                    stmt.setInt(1, floor);
                    stmt.setInt(2, entry.getSlot());
                    stmt.setString(3, serializeItem(entry.getItem()));
                    stmt.addBatch();
                }
                stmt.executeBatch();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save floor drops!", e);
            }
        });
    }

    // Inner class for drop entries
    public static class FloorDropEntry {
        private final int slot;
        private final ItemStack item;

        public FloorDropEntry(int slot, ItemStack item) {
            this.slot = slot;
            this.item = item;
        }

        public int getSlot() {
            return slot;
        }

        public ItemStack getItem() {
            return item;
        }
    }
}
