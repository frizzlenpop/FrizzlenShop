package org.frizzlenpop.frizzlenStore.api.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.Bukkit;
import org.frizzlenpop.frizzlenStore.FrizzlenStore;
import org.frizzlenpop.frizzlenStore.util.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Handles the /api/status endpoint
 * Returns server status and plugin information
 */
public class StatusHandler implements HttpHandler {
    private final FrizzlenStore plugin;
    
    /**
     * Create a new status handler
     * @param plugin The plugin instance
     */
    public StatusHandler(FrizzlenStore plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Logger.info("Status endpoint accessed from: " + exchange.getRemoteAddress());
        
        // Set CORS headers
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With, Accept, Origin");
        exchange.getResponseHeaders().add("Access-Control-Allow-Credentials", "true");
        exchange.getResponseHeaders().add("Access-Control-Max-Age", "3600");
        
        // Handle preflight requests
        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        
        // Only allow GET requests
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            return;
        }
        
        try {
            // Build status response
            String response = buildStatusResponse();
            
            // Send response
            sendResponse(exchange, 200, response);
        } catch (Exception e) {
            Logger.severe("Error handling status request: " + e.getMessage());
            sendResponse(exchange, 500, "{\"error\": \"Internal server error\"}");
        }
    }
    
    /**
     * Build the status response JSON
     * @return JSON string with status information
     */
    private String buildStatusResponse() {
        String serverVersion = Bukkit.getVersion();
        String pluginVersion = plugin.getDescription().getVersion();
        String apiVersion = plugin.getDescription().getAPIVersion();
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();
        boolean databaseConnected = plugin.getDatabaseManager().getConnection() != null;
        String apiUrl = plugin.getConfigManager().getApiUrl();
        int apiPort = plugin.getConfigManager().getApiPort();
        
        return String.format(
                "{" +
                "\"status\": \"online\"," +
                "\"timestamp\": %d," +
                "\"server\": {" +
                "\"version\": \"%s\"," +
                "\"players\": {" +
                "\"online\": %d," +
                "\"max\": %d" +
                "}" +
                "}," +
                "\"plugin\": {" +
                "\"name\": \"FrizzlenStore\"," +
                "\"version\": \"%s\"," +
                "\"apiVersion\": \"%s\"" +
                "}," +
                "\"api\": {" +
                "\"url\": \"%s\"," +
                "\"port\": %d" +
                "}," +
                "\"database\": {" +
                "\"connected\": %b" +
                "}" +
                "}",
                System.currentTimeMillis(), serverVersion, onlinePlayers, maxPlayers, 
                pluginVersion, apiVersion, apiUrl, apiPort, databaseConnected
        );
    }
    
    /**
     * Send an HTTP response
     * @param exchange The HTTP exchange
     * @param statusCode The HTTP status code
     * @param response The response body
     * @throws IOException If an I/O error occurs
     */
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
} 