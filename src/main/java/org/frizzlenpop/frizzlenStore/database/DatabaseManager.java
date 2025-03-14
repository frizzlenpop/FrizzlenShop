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
import java.util.Properties;

/**
 * Manages database connections and operations
 */
public class DatabaseManager {
    private final FrizzlenStore plugin;
    private DatabaseConfig dbConfig;
    private Connection connection;
    private ExecutorService executor;
    
    // SQL statements for table creation
    private static final String CREATE_PRODUCTS_TABLE_MYSQL = 
            "CREATE TABLE IF NOT EXISTS products (" +
            "id INT AUTO_INCREMENT PRIMARY KEY, " +
            "name VARCHAR(128) NOT NULL, " +
            "description TEXT, " +
            "price DECIMAL(10, 2) NOT NULL, " +
            "sale_price DECIMAL(10, 2), " +
            "category_id INT, " +
            "commands TEXT NOT NULL, " +
            "image_url VARCHAR(255), " +
            "display_order INT DEFAULT 0, " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
            "active BOOLEAN DEFAULT TRUE" +
            ")";
    
    private static final String CREATE_PRODUCTS_TABLE_SQLITE = 
            "CREATE TABLE IF NOT EXISTS products (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "name VARCHAR(128) NOT NULL, " +
            "description TEXT, " +
            "price DECIMAL(10, 2) NOT NULL, " +
            "sale_price DECIMAL(10, 2), " +
            "category_id INTEGER, " +
            "commands TEXT NOT NULL, " +
            "image_url VARCHAR(255), " +
            "display_order INTEGER DEFAULT 0, " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
            "active BOOLEAN DEFAULT 1" +
            ")";
    
    private static final String CREATE_CATEGORIES_TABLE_MYSQL = 
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
    
    private static final String CREATE_CATEGORIES_TABLE_SQLITE = 
            "CREATE TABLE IF NOT EXISTS categories (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "name VARCHAR(64) NOT NULL, " +
            "description TEXT, " +
            "display_order INTEGER DEFAULT 0, " +
            "image_url VARCHAR(255), " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
            "active BOOLEAN DEFAULT 1" +
            ")";
    
    private static final String CREATE_PURCHASES_TABLE_MYSQL = 
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
            "delivery_time TIMESTAMP NULL, " +
            "delivery_attempted INT DEFAULT 0" +
            ")";
    
    private static final String CREATE_PURCHASES_TABLE_SQLITE = 
            "CREATE TABLE IF NOT EXISTS purchases (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "transaction_id VARCHAR(64) NOT NULL UNIQUE, " +
            "player_name VARCHAR(32) NOT NULL, " +
            "player_uuid VARCHAR(36), " +
            "product_id INTEGER NOT NULL, " +
            "price_paid DECIMAL(10, 2) NOT NULL, " +
            "payment_method VARCHAR(32) NOT NULL, " +
            "payment_status VARCHAR(16) NOT NULL, " +
            "purchase_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
            "ip_address VARCHAR(45), " +
            "delivered BOOLEAN DEFAULT 0, " +
            "delivery_time TIMESTAMP NULL, " +
            "delivery_attempted INTEGER DEFAULT 0" +
            ")";
    
    private static final String CREATE_COUPONS_TABLE_MYSQL = 
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
    
    private static final String CREATE_COUPONS_TABLE_SQLITE = 
            "CREATE TABLE IF NOT EXISTS coupons (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "code VARCHAR(32) NOT NULL UNIQUE, " +
            "discount_type VARCHAR(10) NOT NULL CHECK(discount_type IN ('percentage', 'fixed')), " +
            "discount_value DECIMAL(10, 2) NOT NULL, " +
            "start_date TIMESTAMP NULL, " +
            "end_date TIMESTAMP NULL, " +
            "uses_limit INTEGER DEFAULT 0, " +
            "used_count INTEGER DEFAULT 0, " +
            "minimum_purchase DECIMAL(10, 2) DEFAULT 0, " +
            "product_ids TEXT, " +
            "category_ids TEXT, " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
            "active BOOLEAN DEFAULT 1" +
            ")";
    
    private static final String CREATE_PLAYERS_TABLE_MYSQL = 
            "CREATE TABLE IF NOT EXISTS players (" +
            "uuid VARCHAR(36) PRIMARY KEY, " +
            "name VARCHAR(32) NOT NULL, " +
            "last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
            "total_spent DECIMAL(10, 2) DEFAULT 0, " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")";
    
    private static final String CREATE_PLAYERS_TABLE_SQLITE = 
            "CREATE TABLE IF NOT EXISTS players (" +
            "uuid VARCHAR(36) PRIMARY KEY, " +
            "name VARCHAR(32) NOT NULL, " +
            "last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
            "total_spent DECIMAL(10, 2) DEFAULT 0, " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
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
            // Always use 127.0.0.1 instead of localhost for more reliable connections
            String effectiveHost = "localhost".equalsIgnoreCase(dbConfig.getHost()) ? "127.0.0.1" : dbConfig.getHost();
            
            // Select appropriate JDBC driver based on database type
            if ("mariadb".equalsIgnoreCase(dbConfig.getType())) {
                try {
                    Class.forName("org.mariadb.jdbc.Driver");
                    Logger.info("Loaded MariaDB JDBC driver");
                } catch (ClassNotFoundException e) {
                    Logger.warning("MariaDB driver not found, falling back to MySQL driver: " + e.getMessage());
                    try {
                        Class.forName("com.mysql.cj.jdbc.Driver");
                        Logger.info("Loaded MySQL JDBC driver as fallback");
                    } catch (ClassNotFoundException e2) {
                        Logger.severe("Neither MariaDB nor MySQL driver found: " + e2.getMessage());
                        throw e2; // Rethrow to be caught by the outer catch
                    }
                }
            } else {
                // Default to MySQL driver for non-MariaDB types
                try {
                    Class.forName("com.mysql.cj.jdbc.Driver");
                    Logger.info("Loaded MySQL JDBC driver");
                } catch (ClassNotFoundException e) {
                    Logger.severe("MySQL driver not found: " + e.getMessage());
                    throw e; // Rethrow to be caught by the outer catch
                }
            }
            
            // Build connection properties
            Properties props = new Properties();
            props.setProperty("user", dbConfig.getUsername());
            props.setProperty("password", dbConfig.getPassword());
            props.setProperty("connectTimeout", "10000");
            props.setProperty("socketTimeout", "30000");
            props.setProperty("useSSL", "false");
            props.setProperty("allowPublicKeyRetrieval", "true");
            
            // Get JDBC URL based on database type
            String jdbcUrl = dbConfig.getJdbcUrl();
            
            // Don't force MySQL URL for MariaDB anymore
            // Replace any "localhost" with "127.0.0.1" in the URL
            if (jdbcUrl.contains("localhost")) {
                jdbcUrl = jdbcUrl.replace("localhost", "127.0.0.1");
            }
            
            Logger.info("Connecting to database with URL: " + jdbcUrl);
            
            // Establish connection
            connection = DriverManager.getConnection(jdbcUrl, props);
            
            // For MariaDB/MySQL, set some session variables
            if ("mysql".equalsIgnoreCase(dbConfig.getType()) || "mariadb".equalsIgnoreCase(dbConfig.getType())) {
                try (Statement stmt = connection.createStatement()) {
                    // Set session variables for better compatibility
                    stmt.execute("SET SESSION wait_timeout=28800");
                    stmt.execute("SET SESSION interactive_timeout=28800");
                    stmt.execute("SET NAMES utf8mb4");
                } catch (SQLException e) {
                    Logger.warning("Failed to set session variables: " + e.getMessage());
                }
            }
            
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
            // Create tables based on database type
            boolean isMysqlType = "mysql".equalsIgnoreCase(dbConfig.getType()) || "mariadb".equalsIgnoreCase(dbConfig.getType());
            
            // Execute the appropriate SQL based on database type
            if (isMysqlType) {
                statement.executeUpdate(CREATE_PRODUCTS_TABLE_MYSQL);
                statement.executeUpdate(CREATE_CATEGORIES_TABLE_MYSQL);
                statement.executeUpdate(CREATE_PURCHASES_TABLE_MYSQL);
                statement.executeUpdate(CREATE_COUPONS_TABLE_MYSQL);
                statement.executeUpdate(CREATE_PLAYERS_TABLE_MYSQL);
            } else {
                statement.executeUpdate(CREATE_PRODUCTS_TABLE_SQLITE);
                statement.executeUpdate(CREATE_CATEGORIES_TABLE_SQLITE);
                statement.executeUpdate(CREATE_PURCHASES_TABLE_SQLITE);
                statement.executeUpdate(CREATE_COUPONS_TABLE_SQLITE);
                statement.executeUpdate(CREATE_PLAYERS_TABLE_SQLITE);
            }
            
            // Alter tables to add missing columns if they don't exist
            try {
                // Check if display_order column exists in products table
                ResultSet rs = connection.getMetaData().getColumns(null, null, "products", "display_order");
                if (!rs.next()) {
                    Logger.info("Adding display_order column to products table");
                    if (isMysqlType) {
                        statement.executeUpdate("ALTER TABLE products ADD COLUMN display_order INT DEFAULT 0");
                    } else {
                        statement.executeUpdate("ALTER TABLE products ADD COLUMN display_order INTEGER DEFAULT 0");
                    }
                }
                rs.close();
            } catch (SQLException e) {
                Logger.warning("Error checking/adding display_order column: " + e.getMessage());
            }
            
            Logger.info("Database tables created/verified successfully");
        } catch (SQLException e) {
            Logger.severe("Error creating database tables: " + e.getMessage());
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