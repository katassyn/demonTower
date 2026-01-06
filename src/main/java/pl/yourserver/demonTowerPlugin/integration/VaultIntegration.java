package pl.yourserver.demonTowerPlugin.integration;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import pl.yourserver.demonTowerPlugin.DemonTowerPlugin;

public class VaultIntegration {

    private final DemonTowerPlugin plugin;
    private Economy economy;
    private boolean enabled = false;

    public VaultIntegration(DemonTowerPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean setup() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault not found! Economy features will not work.");
            return false;
        }

        try {
            RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp == null) {
                plugin.getLogger().warning("No economy provider found!");
                return false;
            }
            economy = rsp.getProvider();
            enabled = economy != null;
            if (enabled) {
                plugin.getLogger().info("Vault economy integration enabled! Using: " + economy.getName());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to hook into Vault: " + e.getMessage());
            return false;
        }

        return enabled;
    }

    /**
     * Check if player has enough money
     */
    public boolean hasBalance(Player player, double amount) {
        if (!enabled) return false;
        return economy.has(player, amount);
    }

    /**
     * Get player's current balance
     */
    public double getBalance(Player player) {
        if (!enabled) return 0;
        return economy.getBalance(player);
    }

    /**
     * Withdraw money from player
     * @return true if successful
     */
    public boolean withdraw(Player player, double amount) {
        if (!enabled) return false;

        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }

    /**
     * Deposit money to player
     * @return true if successful
     */
    public boolean deposit(Player player, double amount) {
        if (!enabled) return false;

        EconomyResponse response = economy.depositPlayer(player, amount);
        return response.transactionSuccess();
    }

    /**
     * Format money amount for display
     */
    public String format(double amount) {
        if (!enabled) return String.format("$%.0f", amount);
        return economy.format(amount);
    }

    /**
     * Format money in compact form (1m, 50m, 1b)
     */
    public String formatCompact(double amount) {
        if (amount >= 1_000_000_000) {
            return String.format("%.1fb$", amount / 1_000_000_000);
        } else if (amount >= 1_000_000) {
            return String.format("%.0fm$", amount / 1_000_000);
        } else if (amount >= 1_000) {
            return String.format("%.0fk$", amount / 1_000);
        } else {
            return String.format("%.0f$", amount);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
