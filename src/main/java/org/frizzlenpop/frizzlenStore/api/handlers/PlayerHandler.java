package org.frizzlenpop.frizzlenStore.api.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.frizzlenpop.frizzlenStore.FrizzlenStore;
import org.frizzlenpop.frizzlenStore.util.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles API requests for players
 */
public class PlayerHandler implements HttpHandler {

    private final FrizzlenStore plugin;

    /**
     * Constructor
     * @param plugin The plugin instance
     */
    public PlayerHandler(FrizzlenStore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Set CORS headers
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With, Accept, Origin");
        exchange.getResponseHeaders().add("Access-Control-Allow-Credentials", "true");
        exchange.getResponseHeaders().add("Access-Control-Max-Age", "3600");
        
        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        try {
            // Verify API token
            String token = exchange.getRequestHeaders().getFirst("Authorization");
            if (token == null || !token.equals("Bearer " + plugin.getConfig().getString("api.token"))) {
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Unauthorized")
                        .toString();
                sendResponse(exchange, 401, response);
                return;
            }

            // Handle different request methods and paths
            String path = exchange.getRequestURI().getPath();
            
            if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                if (path.endsWith("/players") || path.endsWith("/players/")) {
                    handleGetPlayers(exchange);
                } else if (path.contains("/players/search")) {
                    handleSearchPlayers(exchange);
                } else if (path.contains("/players/")) {
                    handleGetPlayer(exchange);
                } else {
                    // Not found
                    String response = new JSONObject()
                            .put("success", false)
                            .put("error", "Not found")
                            .toString();
                    sendResponse(exchange, 404, response);
                }
            } else if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                if (path.contains("/players/sync")) {
                    handleSyncPlayers(exchange);
                } else {
                    // Method not allowed
                    String response = new JSONObject()
                            .put("success", false)
                            .put("error", "Method not allowed")
                            .toString();
                    sendResponse(exchange, 405, response);
                }
            } else {
                // Method not allowed
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Method not allowed")
                        .toString();
                sendResponse(exchange, 405, response);
            }
        } catch (Exception e) {
            Logger.severe("Error handling player request: " + e.getMessage());
            e.printStackTrace();
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Internal server error")
                    .toString();
            sendResponse(exchange, 500, response);
        }
    }

    /**
     * Handle GET request to fetch all players
     * @param exchange The HTTP exchange
     * @throws IOException If an I/O error occurs
     */
    private void handleGetPlayers(HttpExchange exchange) throws IOException {
        try {
            Connection connection = plugin.getDatabaseManager().getConnection();
            String query = "SELECT * FROM players ORDER BY last_seen DESC LIMIT 100";
            PreparedStatement statement = connection.prepareStatement(query);
            ResultSet resultSet = statement.executeQuery();

            JSONArray players = new JSONArray();
            while (resultSet.next()) {
                JSONObject player = new JSONObject();
                player.put("uuid", resultSet.getString("uuid"));
                player.put("name", resultSet.getString("name"));
                player.put("first_join", resultSet.getString("first_join"));
                player.put("last_seen", resultSet.getString("last_seen"));
                player.put("banned", resultSet.getBoolean("banned"));
                players.put(player);
            }

            resultSet.close();
            statement.close();

            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("players", players);
            
            sendResponse(exchange, 200, response.toString());
        } catch (SQLException e) {
            Logger.severe("Database error while getting players: " + e.getMessage());
            e.printStackTrace();
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Database error")
                    .toString();
            sendResponse(exchange, 500, response);
        }
    }

    /**
     * Handle GET request to fetch a single player
     * @param exchange The HTTP exchange
     * @throws IOException If an I/O error occurs
     */
    private void handleGetPlayer(HttpExchange exchange) throws IOException {
        // Get player UUID from path
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        if (parts.length < 4) {
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Invalid path")
                    .toString();
            sendResponse(exchange, 400, response);
            return;
        }
        
        try {
            String playerUuid = parts[3];
            
            Connection connection = plugin.getDatabaseManager().getConnection();
            String query = "SELECT * FROM players WHERE uuid = ?";
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setString(1, playerUuid);
            ResultSet resultSet = statement.executeQuery();

            if (!resultSet.next()) {
                resultSet.close();
                statement.close();
                
                // If not in database, try to look up from server
                OfflinePlayer offlinePlayer = null;
                try {
                    UUID uuid = UUID.fromString(playerUuid);
                    offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                } catch (IllegalArgumentException e) {
                    // Not a valid UUID
                }
                
                if (offlinePlayer != null && offlinePlayer.hasPlayedBefore()) {
                    JSONObject player = new JSONObject();
                    player.put("uuid", offlinePlayer.getUniqueId().toString());
                    player.put("name", offlinePlayer.getName());
                    player.put("first_join", offlinePlayer.getFirstPlayed());
                    player.put("last_seen", offlinePlayer.getLastPlayed());
                    player.put("banned", offlinePlayer.isBanned());
                    player.put("online", offlinePlayer.isOnline());
                    
                    JSONObject response = new JSONObject();
                    response.put("success", true);
                    response.put("player", player);
                    
                    sendResponse(exchange, 200, response.toString());
                    return;
                }
                
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Player not found")
                        .toString();
                sendResponse(exchange, 404, response);
                return;
            }
            
            JSONObject player = new JSONObject();
            player.put("uuid", resultSet.getString("uuid"));
            player.put("name", resultSet.getString("name"));
            player.put("first_join", resultSet.getString("first_join"));
            player.put("last_seen", resultSet.getString("last_seen"));
            player.put("banned", resultSet.getBoolean("banned"));
            
            // Check if player is currently online
            player.put("online", false);
            try {
                UUID uuid = UUID.fromString(playerUuid);
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                player.put("online", offlinePlayer.isOnline());
            } catch (IllegalArgumentException e) {
                // Not a valid UUID
            }
            
            resultSet.close();
            statement.close();

            // Get purchase stats
            String statsQuery = "SELECT COUNT(*) as purchase_count, SUM(price) as total_spent " +
                              "FROM purchases WHERE player_uuid = ?";
            PreparedStatement statsStatement = connection.prepareStatement(statsQuery);
            statsStatement.setString(1, playerUuid);
            ResultSet statsResultSet = statsStatement.executeQuery();
            
            if (statsResultSet.next()) {
                player.put("purchase_count", statsResultSet.getInt("purchase_count"));
                player.put("total_spent", statsResultSet.getDouble("total_spent"));
            } else {
                player.put("purchase_count", 0);
                player.put("total_spent", 0.0);
            }
            
            statsResultSet.close();
            statsStatement.close();

            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("player", player);
            
            sendResponse(exchange, 200, response.toString());
        } catch (SQLException e) {
            Logger.severe("Database error while getting player: " + e.getMessage());
            e.printStackTrace();
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Database error")
                    .toString();
            sendResponse(exchange, 500, response);
        }
    }

    /**
     * Handle GET request to search players
     * @param exchange The HTTP exchange
     * @throws IOException If an I/O error occurs
     */
    private void handleSearchPlayers(HttpExchange exchange) throws IOException {
        // Get query parameter
        String query = exchange.getRequestURI().getQuery();
        if (query == null || !query.startsWith("q=")) {
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Missing search query")
                    .toString();
            sendResponse(exchange, 400, response);
            return;
        }
        
        String searchQuery = query.substring(2);
        if (searchQuery.isEmpty()) {
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Empty search query")
                    .toString();
            sendResponse(exchange, 400, response);
            return;
        }
        
        try {
            Connection connection = plugin.getDatabaseManager().getConnection();
            String sql = "SELECT * FROM players WHERE name LIKE ? ORDER BY last_seen DESC LIMIT 20";
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, "%" + searchQuery + "%");
            ResultSet resultSet = statement.executeQuery();

            JSONArray players = new JSONArray();
            while (resultSet.next()) {
                JSONObject player = new JSONObject();
                player.put("uuid", resultSet.getString("uuid"));
                player.put("name", resultSet.getString("name"));
                player.put("first_join", resultSet.getString("first_join"));
                player.put("last_seen", resultSet.getString("last_seen"));
                player.put("banned", resultSet.getBoolean("banned"));
                players.put(player);
            }

            resultSet.close();
            statement.close();

            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("query", searchQuery);
            response.put("players", players);
            
            sendResponse(exchange, 200, response.toString());
        } catch (SQLException e) {
            Logger.severe("Database error while searching players: " + e.getMessage());
            e.printStackTrace();
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Database error")
                    .toString();
            sendResponse(exchange, 500, response);
        }
    }

    /**
     * Handle POST request to sync players from server to database
     * @param exchange The HTTP exchange
     * @throws IOException If an I/O error occurs
     */
    private void handleSyncPlayers(HttpExchange exchange) throws IOException {
        try {
            int syncCount = 0;
            Connection connection = plugin.getDatabaseManager().getConnection();
            
            for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
                if (offlinePlayer.hasPlayedBefore()) {
                    String playerUuid = offlinePlayer.getUniqueId().toString();
                    String playerName = offlinePlayer.getName();
                    long firstJoin = offlinePlayer.getFirstPlayed();
                    long lastSeen = offlinePlayer.getLastPlayed();
                    boolean banned = offlinePlayer.isBanned();
                    
                    // Check if player exists in database
                    String checkQuery = "SELECT * FROM players WHERE uuid = ?";
                    PreparedStatement checkStatement = connection.prepareStatement(checkQuery);
                    checkStatement.setString(1, playerUuid);
                    ResultSet checkResultSet = checkStatement.executeQuery();
                    
                    if (checkResultSet.next()) {
                        // Update existing player
                        String updateQuery = "UPDATE players SET name = ?, first_join = ?, last_seen = ?, banned = ? WHERE uuid = ?";
                        PreparedStatement updateStatement = connection.prepareStatement(updateQuery);
                        updateStatement.setString(1, playerName);
                        updateStatement.setLong(2, firstJoin);
                        updateStatement.setLong(3, lastSeen);
                        updateStatement.setBoolean(4, banned);
                        updateStatement.setString(5, playerUuid);
                        updateStatement.executeUpdate();
                        updateStatement.close();
                    } else {
                        // Insert new player
                        String insertQuery = "INSERT INTO players (uuid, name, first_join, last_seen, banned) VALUES (?, ?, ?, ?, ?)";
                        PreparedStatement insertStatement = connection.prepareStatement(insertQuery);
                        insertStatement.setString(1, playerUuid);
                        insertStatement.setString(2, playerName);
                        insertStatement.setLong(3, firstJoin);
                        insertStatement.setLong(4, lastSeen);
                        insertStatement.setBoolean(5, banned);
                        insertStatement.executeUpdate();
                        insertStatement.close();
                    }
                    
                    checkResultSet.close();
                    checkStatement.close();
                    syncCount++;
                }
            }

            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("message", "Players synchronized successfully");
            response.put("sync_count", syncCount);
            
            sendResponse(exchange, 200, response.toString());
        } catch (SQLException e) {
            Logger.severe("Database error while syncing players: " + e.getMessage());
            e.printStackTrace();
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Database error")
                    .toString();
            sendResponse(exchange, 500, response);
        }
    }

    /**
     * Send HTTP response
     * @param exchange The HTTP exchange
     * @param statusCode The HTTP status code
     * @param response The response body
     * @throws IOException If an I/O error occurs
     */
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] responseBytes = response.getBytes();
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
} 