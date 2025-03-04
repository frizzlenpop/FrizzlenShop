package org.frizzlenpop.frizzlenStore.database;

import org.frizzlenpop.frizzlenStore.FrizzlenStore;
import org.frizzlenpop.frizzlenStore.config.DatabaseConfig;
import org.frizzlenpop.frizzlenStore.util.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages database connections and operations
 */
public class DatabaseManager {
    private final FrizzlenStore plugin;
    private DatabaseConfig dbConfig;
    private Connection connection;
    private ExecutorService executor;
    
    // SQL statements for table creation
    private static final String CREATE_PRODUCTS_TABLE = 
            "CREATE TABLE IF NOT EXISTS products (" +
            "id INT AUTO_INCREMENT PRIMARY KEY, " +
            "name VARCHAR(128) NOT NULL, " +
            "description TEXT, " +
            "price DECIMAL(10, 2) NOT NULL, " +
            "sale_price DECIMAL(10, 2), " +
            "category_id INT, " +
            "commands TEXT NOT NULL, " +
            "image_url VARCHAR(255), " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
            "active BOOLEAN DEFAULT TRUE" +
            ")";
    
    private static final String CREATE_CATEGORIES_TABLE = 
            "CREATE TABLE IF NOT EXISTS categories (" +
            "id INT AUTO_INCREMENT PRIMARY KEY, " +
            "name VARCHAR(64) NOT NULL, " +
            "description TEXT, " +
            "display_order INT DEFAULT 0, " +
            "image_url VARCHAR(255), " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
            "active BOOLEAN DEFAULT TRUE" +
            ")";
    
    private static final String CREATE_PURCHASES_TABLE = 
            "CREATE TABLE IF NOT EXISTS purchases (" +
            "id INT AUTO_INCREMENT PRIMARY KEY, " +
            "transaction_id VARCHAR(64) NOT NULL UNIQUE, " +
            "player_name VARCHAR(32) NOT NULL, " +
            "player_uuid VARCHAR(36), " +
            "product_id INT NOT NULL, " +
            "price_paid DECIMAL(10, 2) NOT NULL, " +
            "payment_method VARCHAR(32) NOT NULL, " +
            "payment_status VARCHAR(16) NOT NULL, " +
            "purchase_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
            "ip_address VARCHAR(45), " +
            "delivered BOOLEAN DEFAULT FALSE, " +
            "delivery_time TIMESTAMP NULL" +
            ")";
    
    private static final String CREATE_COUPONS_TABLE = 
            "CREATE TABLE IF NOT EXISTS coupons (" +
            "id INT AUTO_INCREMENT PRIMARY KEY, " +
            "code VARCHAR(32) NOT NULL UNIQUE, " +
            "discount_type ENUM('percentage', 'fixed') NOT NULL, " +
            "discount_value DECIMAL(10, 2) NOT NULL, " +
            "start_date TIMESTAMP NULL, " +
            "end_date TIMESTAMP NULL, " +
            "uses_limit INT DEFAULT 0, " +
            "used_count INT DEFAULT 0, " +
            "minimum_purchase DECIMAL(10, 2) DEFAULT 0, " +
            "product_ids TEXT, " +
            "category_ids TEXT, " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
            "active BOOLEAN DEFAULT TRUE" +
            ")";
    
    /**
     * Create a new database manager
     * @param plugin The plugin instance
     */
    public DatabaseManager(FrizzlenStore plugin) {
        this.plugin = plugin;
        this.executor = Executors.newFixedThreadPool(4);
    }
    
    /**
     * Initialize the database connection and tables
     * @return True if successful, false otherwise
     */
    public boolean initialize() {
        try {
            // Get database configuration
            dbConfig = plugin.getConfigManager().getDatabaseConfig();
            
            // Connect to database
            if (!connect()) {
                return false;
            }
            
            // Create tables
            createTables();
            
            return true;
        } catch (Exception e) {
            Logger.severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Connect to the database
     * @return True if successful, false otherwise
     */
    private boolean connect() {
        try {
            // Load driver
            if ("mysql".equalsIgnoreCase(dbConfig.getType())) {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } else if ("sqlite".equalsIgnoreCase(dbConfig.getType())) {
                Class.forName("org.sqlite.JDBC");
            }
            
            // Establish connection
            connection = DriverManager.getConnection(
                    dbConfig.getJdbcUrl(),
                    dbConfig.getUsername(),
                    dbConfig.getPassword()
            );
            
            Logger.info("Connected to database successfully");
            return true;
        } catch (ClassNotFoundException | SQLException e) {
            Logger.severe("Failed to connect to database: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Create database tables if they don't exist
     */
    private void createTables() {
        try (Statement statement = connection.createStatement()) {
            // Create tables
            statement.executeUpdate(CREATE_CATEGORIES_TABLE);
            statement.executeUpdate(CREATE_PRODUCTS_TABLE);
            statement.executeUpdate(CREATE_PURCHASES_TABLE);
            statement.executeUpdate(CREATE_COUPONS_TABLE);
            
            Logger.info("Database tables created/verified successfully");
        } catch (SQLException e) {
            Logger.severe("Failed to create database tables: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Close the database connection
     */
    public void shutdown() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                Logger.info("Database connection closed");
            }
            
            if (executor != null) {
                executor.shutdown();
            }
        } catch (SQLException e) {
            Logger.severe("Error closing database connection: " + e.getMessage());
        }
    }
    
    /**
     * Get the database connection
     * @return The database connection
     */
    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connect();
            }
        } catch (SQLException e) {
            Logger.severe("Error checking database connection: " + e.getMessage());
        }
        
        return connection;
    }
    
    /**
     * Execute a query asynchronously
     * @param sql The SQL statement
     * @return A CompletableFuture with the ResultSet
     */
    public CompletableFuture<ResultSet> queryAsync(String sql) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                PreparedStatement statement = getConnection().prepareStatement(sql);
                return statement.executeQuery();
            } catch (SQLException e) {
                Logger.severe("Error executing query: " + e.getMessage());
                return null;
            }
        }, executor);
    }
    
    /**
     * Execute an update asynchronously
     * @param sql The SQL statement
     * @return A CompletableFuture with the number of affected rows
     */
    public CompletableFuture<Integer> updateAsync(String sql) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                PreparedStatement statement = getConnection().prepareStatement(sql);
                return statement.executeUpdate();
            } catch (SQLException e) {
                Logger.severe("Error executing update: " + e.getMessage());
                return -1;
            }
        }, executor);
    }
    
    /**
     * Prepare a statement with parameters
     * @param sql The SQL statement
     * @return The prepared statement
     */
    public PreparedStatement prepareStatement(String sql) {
        try {
            return getConnection().prepareStatement(sql);
        } catch (SQLException e) {
            Logger.severe("Error preparing statement: " + e.getMessage());
            return null;
        }
    }
} 