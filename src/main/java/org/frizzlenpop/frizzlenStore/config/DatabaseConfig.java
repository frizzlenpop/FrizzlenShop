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
        // Convert localhost to IP address for more reliable connectivity
        String effectiveHost = host;
        if ("localhost".equalsIgnoreCase(effectiveHost)) {
            effectiveHost = "127.0.0.1";
        }
        
        if ("mysql".equalsIgnoreCase(type)) {
            // Build a standard MySQL JDBC URL
            StringBuilder url = new StringBuilder("jdbc:mysql://");
            
            // Specify host, port and database
            url.append(effectiveHost)
               .append(":")
               .append(port)
               .append("/")
               .append(database);
            
            // Add standard connection parameters
            url.append("?useSSL=false")
               .append("&allowPublicKeyRetrieval=true")
               .append("&connectTimeout=5000")
               .append("&socketTimeout=30000")
               .append("&autoReconnect=true");
            
            // Add additional parameters for better stability
            url.append("&tcpKeepAlive=true")
               .append("&useUnicode=true")
               .append("&characterEncoding=UTF-8");
            
            return url.toString();
        } else if ("mariadb".equalsIgnoreCase(type)) {
            // Build a standard MariaDB JDBC URL
            StringBuilder url = new StringBuilder("jdbc:mariadb://");
            
            // Specify host, port and database
            url.append(effectiveHost)
               .append(":")
               .append(port)
               .append("/")
               .append(database);
            
            // Add standard connection parameters
            url.append("?useSSL=false")
               .append("&allowPublicKeyRetrieval=true")
               .append("&connectTimeout=5000")
               .append("&socketTimeout=30000")
               .append("&autoReconnect=true");
            
            // Add additional parameters for better stability
            url.append("&tcpKeepAlive=true")
               .append("&useUnicode=true")
               .append("&characterEncoding=UTF-8");
            
            return url.toString();
        } else if ("sqlite".equalsIgnoreCase(type)) {
            // For SQLite, use a file in the plugin's data folder
            return "jdbc:sqlite:plugins/FrizzlenStore/database.db";
        }
        
        // Default to MySQL with basic parameters
        return "jdbc:mysql://" + effectiveHost + ":" + port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true";
    }
} 