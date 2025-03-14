package org.frizzlenpop.frizzlenStore.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.frizzlenpop.frizzlenStore.FrizzlenStore;
import org.frizzlenpop.frizzlenStore.util.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages all configuration files for the plugin
 */
public class ConfigManager {
    private final FrizzlenStore plugin;
    private FileConfiguration config;
    private final Map<String, FileConfiguration> configFiles;
    
    // Default configuration values
    private static final String DEFAULT_API_URL = "http://localhost:3000";
    private static final int DEFAULT_API_PORT = 8080;
    private static final String DEFAULT_DATABASE_TYPE = "mysql";
    private static final String DEFAULT_MARIADB_TYPE = "mariadb";
    private static final String DEFAULT_DATABASE_HOST = "localhost";
    private static final int DEFAULT_DATABASE_PORT = 3306;
    private static final String DEFAULT_DATABASE_NAME = "frizzlenstore";
    private static final String DEFAULT_DATABASE_USER = "root";
    private static final String DEFAULT_DATABASE_PASSWORD = "";
    private static final boolean DEFAULT_DEBUG_MODE = false;
    private static final boolean DEFAULT_ALLOW_CORS = true;
    private static final String DEFAULT_CORS_ORIGINS = "*";
    private static final String DEFAULT_SERVER_URL = "http://localhost:8081";
    
    public ConfigManager(FrizzlenStore plugin) {
        this.plugin = plugin;
        this.configFiles = new HashMap<>();
    }
    
    /**
     * Load all configuration files
     */
    public void loadConfigs() {
        // Load main config
        loadDefaultConfig();
        
        // Load other configs
        loadCustomConfig("database.yml");
        loadCustomConfig("payment-gateways.yml");
        loadCustomConfig("messages.yml");
        loadCustomConfig("store-categories.yml");
        
        Logger.info("All configuration files loaded successfully");
    }
    
    /**
     * Load the default config.yml
     */
    private void loadDefaultConfig() {
        plugin.saveDefaultConfig();
        config = plugin.getConfig();
        
        // Set default values if they don't exist
        if (!config.contains("api.url")) {
            config.set("api.url", DEFAULT_API_URL);
        }
        
        if (!config.contains("api.port")) {
            config.set("api.port", DEFAULT_API_PORT);
        }
        
        if (!config.contains("api.allow-cors")) {
            config.set("api.allow-cors", DEFAULT_ALLOW_CORS);
        }
        
        if (!config.contains("api.cors-origins")) {
            config.set("api.cors-origins", DEFAULT_CORS_ORIGINS);
        }
        
        if (!config.contains("api.server-url")) {
            config.set("api.server-url", DEFAULT_SERVER_URL);
        }
        
        if (!config.contains("debug")) {
            config.set("debug", DEFAULT_DEBUG_MODE);
        }
        
        plugin.saveConfig();
    }
    
    /**
     * Load a custom configuration file
     * @param fileName The file name to load
     */
    private void loadCustomConfig(String fileName) {
        File configFile = new File(plugin.getDataFolder(), fileName);
        
        // Create file if it doesn't exist
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            plugin.saveResource(fileName, false);
        }
        
        // Load configuration
        FileConfiguration fileConfig = YamlConfiguration.loadConfiguration(configFile);
        
        // Check for defaults in jar
        InputStream defaultStream = plugin.getResource(fileName);
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream));
            fileConfig.setDefaults(defaultConfig);
        }
        
        configFiles.put(fileName, fileConfig);
    }
    
    /**
     * Get a custom configuration file
     * @param fileName The name of the file to get
     * @return The file configuration
     */
    public FileConfiguration getCustomConfig(String fileName) {
        return configFiles.getOrDefault(fileName, null);
    }
    
    /**
     * Save a custom configuration file
     * @param fileName The name of the file to save
     */
    public void saveCustomConfig(String fileName) {
        if (!configFiles.containsKey(fileName)) {
            Logger.warning("Attempted to save non-existent config: " + fileName);
            return;
        }
        
        try {
            File configFile = new File(plugin.getDataFolder(), fileName);
            configFiles.get(fileName).save(configFile);
        } catch (IOException e) {
            Logger.severe("Could not save config to " + fileName + ": " + e.getMessage());
        }
    }
    
    /**
     * Get the main config.yml
     * @return The main configuration
     */
    public FileConfiguration getConfig() {
        return config;
    }
    
    /**
     * Get the API URL from config
     * @return The API URL
     */
    public String getApiUrl() {
        return config.getString("api.url", DEFAULT_API_URL);
    }
    
    /**
     * Get the API port from config
     * @return The API port
     */
    public int getApiPort() {
        return config.getInt("api.port", DEFAULT_API_PORT);
    }
    
    /**
     * Check if debug mode is enabled
     * @return True if debug mode is enabled
     */
    public boolean isDebugEnabled() {
        return config.getBoolean("debug", DEFAULT_DEBUG_MODE);
    }
    
    /**
     * Get database configuration
     * @return DatabaseConfig object with all database settings
     */
    public DatabaseConfig getDatabaseConfig() {
        FileConfiguration dbConfig = getCustomConfig("database.yml");
        
        String type = dbConfig.getString("type", DEFAULT_DATABASE_TYPE);
        // Validate database type
        if (!type.equalsIgnoreCase("mysql") && 
            !type.equalsIgnoreCase("mariadb") && 
            !type.equalsIgnoreCase("sqlite")) {
            Logger.warning("Invalid database type: " + type + ". Using default: " + DEFAULT_DATABASE_TYPE);
            type = DEFAULT_DATABASE_TYPE;
        }
        
        String host = dbConfig.getString("host", DEFAULT_DATABASE_HOST);
        int port = dbConfig.getInt("port", DEFAULT_DATABASE_PORT);
        String name = dbConfig.getString("database", DEFAULT_DATABASE_NAME);
        String user = dbConfig.getString("username", DEFAULT_DATABASE_USER);
        String password = dbConfig.getString("password", DEFAULT_DATABASE_PASSWORD);
        
        return new DatabaseConfig(type, host, port, name, user, password);
    }
} 