package org.frizzlenpop.frizzlenStore.config;

/**
 * Stores database configuration information
 */
public class DatabaseConfig {
    private final String type;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    
    /**
     * Create a new database configuration
     * @param type The database type (mysql, sqlite, etc.)
     * @param host The database host
     * @param port The database port
     * @param database The database name
     * @param username The database username
     * @param password The database password
     */
    public DatabaseConfig(String type, String host, int port, String database, String username, String password) {
        this.type = type;
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
    }
    
    /**
     * Get the database type
     * @return The database type
     */
    public String getType() {
        return type;
    }
    
    /**
     * Get the database host
     * @return The database host
     */
    public String getHost() {
        return host;
    }
    
    /**
     * Get the database port
     * @return The database port
     */
    public int getPort() {
        return port;
    }
    
    /**
     * Get the database name
     * @return The database name
     */
    public String getDatabase() {
        return database;
    }
    
    /**
     * Get the database username
     * @return The database username
     */
    public String getUsername() {
        return username;
    }
    
    /**
     * Get the database password
     * @return The database password
     */
    public String getPassword() {
        return password;
    }
    
    /**
     * Get the JDBC URL for this database configuration
     * @return The JDBC URL
     */
    public String getJdbcUrl() {
        if ("mysql".equalsIgnoreCase(type)) {
            return "jdbc:mysql://" + host + ":" + port + "/" + database + 
                   "?useSSL=false&serverTimezone=UTC";
        } else if ("sqlite".equalsIgnoreCase(type)) {
            return "jdbc:sqlite:" + database;
        }
        
        // Default to MySQL
        return "jdbc:mysql://" + host + ":" + port + "/" + database;
    }
} 