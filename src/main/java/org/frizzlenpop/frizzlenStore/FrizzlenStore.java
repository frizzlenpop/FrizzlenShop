package org.frizzlenpop.frizzlenStore;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.frizzlenpop.frizzlenStore.api.ApiManager;
import org.frizzlenpop.frizzlenStore.commands.CommandManager;
import org.frizzlenpop.frizzlenStore.config.ConfigManager;
import org.frizzlenpop.frizzlenStore.database.DatabaseManager;
import org.frizzlenpop.frizzlenStore.listeners.PlayerListener;
import org.frizzlenpop.frizzlenStore.payment.PaymentManager;
import org.frizzlenpop.frizzlenStore.purchase.PurchaseManager;
import org.frizzlenpop.frizzlenStore.util.Logger;

public final class FrizzlenStore extends JavaPlugin {
    
    private static FrizzlenStore instance;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private ApiManager apiManager;
    private PaymentManager paymentManager;
    private PurchaseManager purchaseManager;
    private CommandManager commandManager;
    
    @Override
    public void onEnable() {
        // Set instance for static access
        instance = this;
        
        // Initialize logger with plugin name
        Logger.init(this);
        Logger.info("Enabling FrizzlenStore...");
        
        // Load configuration
        configManager = new ConfigManager(this);
        configManager.loadConfigs();
        
        // Initialize database connection
        databaseManager = new DatabaseManager(this);
        if (!databaseManager.initialize()) {
            Logger.severe("Database connection failed! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Initialize API manager (for web communication)
        apiManager = new ApiManager(this);
        apiManager.startApiServer();
        
        // Initialize payment systems
        paymentManager = new PaymentManager(this);
        
        // Initialize purchase manager
        purchaseManager = new PurchaseManager(this);
        
        // Register commands
        commandManager = new CommandManager(this);
        commandManager.registerCommands();
        
        // Register listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        
        Logger.info("FrizzlenStore has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        Logger.info("Disabling FrizzlenStore...");
        
        // Close API connections
        if (apiManager != null) {
            apiManager.stopApiServer();
        }
        
        // Close database connections
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        
        Logger.info("FrizzlenStore has been disabled.");
    }
    
    /**
     * Get the plugin instance
     * @return the FrizzlenStore instance
     */
    public static FrizzlenStore getInstance() {
        return instance;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    public ApiManager getApiManager() {
        return apiManager;
    }
    
    public PaymentManager getPaymentManager() {
        return paymentManager;
    }
    
    public PurchaseManager getPurchaseManager() {
        return purchaseManager;
    }
    
    public CommandManager getCommandManager() {
        return commandManager;
    }
}
