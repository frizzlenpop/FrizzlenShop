package org.frizzlenpop.frizzlenStore.api.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.frizzlenpop.frizzlenStore.FrizzlenStore;
import org.frizzlenpop.frizzlenStore.util.Logger;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class AuthHandler implements HttpHandler {
    private final FrizzlenStore plugin;
    
    public AuthHandler(FrizzlenStore plugin) {
        this.plugin = plugin;
        Logger.info("AuthHandler initialized");
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Logger.debug("Received auth request: " + exchange.getRequestURI().getPath());
        
        // Read CORS settings from configuration
        boolean allowCors = plugin.getConfigManager().getConfig().getBoolean("api.allow-cors", true);
        String corsOrigins = plugin.getConfigManager().getConfig().getString("api.cors-origins", "*");
        
        if (allowCors) {
            // Set CORS headers based on configuration
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", corsOrigins);
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With, Accept, Origin");
            exchange.getResponseHeaders().add("Access-Control-Allow-Credentials", "true");
            exchange.getResponseHeaders().add("Access-Control-Max-Age", "3600");
            
            Logger.debug("CORS enabled with origins: " + corsOrigins);
        } else {
            Logger.debug("CORS is disabled in config");
        }
        
        // Handle preflight requests
        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            exchange.sendResponseHeaders(200, -1);
            Logger.debug("Handled OPTIONS request for auth endpoint");
            return;
        }
        
        String path = exchange.getRequestURI().getPath();
        
        try {
            if (path.endsWith("/login") && exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                Logger.debug("Processing login request");
                handleLogin(exchange);
            } else if (path.endsWith("/verify") && exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                Logger.debug("Processing verify request");
                handleVerify(exchange);
            } else {
                Logger.debug("Invalid auth endpoint requested: " + path);
                // Not found
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Not found")
                        .toString();
                sendResponse(exchange, 404, response);
            }
        } catch (Exception e) {
            Logger.severe("Error handling auth request: " + e.getMessage());
            e.printStackTrace();
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Internal server error: " + e.getMessage())
                    .toString();
            sendResponse(exchange, 500, response);
        }
    }
    
    private void handleLogin(HttpExchange exchange) throws IOException {
        // Read request body
        String requestBody = readRequestBody(exchange);
        Logger.debug("Login request body: " + requestBody);
        
        JSONObject requestJson = new JSONObject(requestBody);
        
        String username = requestJson.getString("username");
        String password = requestJson.getString("password");
        
        Logger.debug("Login attempt for username: " + username);
        
        // Simple authentication - in a real implementation, you'd check against a database
        if ("admin".equals(username) && "admin123".equals(password)) {
            JSONObject userObj = new JSONObject()
                    .put("username", username)
                    .put("role", "admin");
            
            String token = plugin.getConfigManager().getConfig().getString("api.token");
            Logger.debug("Login successful, using token from config: " + token);
            
            JSONObject responseJson = new JSONObject()
                    .put("success", true)
                    .put("token", token)
                    .put("user", userObj);
            
            sendResponse(exchange, 200, responseJson.toString());
            Logger.info("Successful login for admin user");
        } else {
            Logger.debug("Login failed for user: " + username);
            JSONObject responseJson = new JSONObject()
                    .put("success", false)
                    .put("error", "Invalid credentials");
            
            sendResponse(exchange, 401, responseJson.toString());
        }
    }
    
    private void handleVerify(HttpExchange exchange) throws IOException {
        // Get token from Authorization header
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        Logger.debug("Verify request with auth header: " + authHeader);
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            String configToken = plugin.getConfigManager().getConfig().getString("api.token");
            
            Logger.debug("Comparing tokens - Received: " + token + ", Config: " + configToken);
            
            if (token.equals(configToken)) {
                Logger.debug("Token verification successful");
                JSONObject responseJson = new JSONObject()
                        .put("success", true)
                        .put("valid", true);
                
                sendResponse(exchange, 200, responseJson.toString());
                return;
            }
        }
        
        Logger.debug("Token verification failed");
        JSONObject responseJson = new JSONObject()
                .put("success", false)
                .put("error", "Invalid token");
        
        sendResponse(exchange, 401, responseJson.toString());
    }
    
    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name())) {
            return scanner.useDelimiter("\\A").next();
        } catch (Exception e) {
            Logger.warning("Failed to read request body: " + e.getMessage());
            return "{}";
        }
    }
    
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
        Logger.debug("Sent response with status code: " + statusCode + ", body: " + response);
    }
}