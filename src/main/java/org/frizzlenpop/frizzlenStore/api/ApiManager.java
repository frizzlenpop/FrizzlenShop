package org.frizzlenpop.frizzlenStore.api;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.bukkit.Bukkit;
import org.frizzlenpop.frizzlenStore.FrizzlenStore;
import org.frizzlenpop.frizzlenStore.api.handlers.CategoryHandler;
import org.frizzlenpop.frizzlenStore.api.handlers.CouponHandler;
import org.frizzlenpop.frizzlenStore.api.handlers.PaymentHandler;
import org.frizzlenpop.frizzlenStore.api.handlers.ProductHandler;
import org.frizzlenpop.frizzlenStore.api.handlers.PurchaseHandler;
import org.frizzlenpop.frizzlenStore.api.handlers.PlayerHandler;
import org.frizzlenpop.frizzlenStore.api.handlers.StatusHandler;
import org.frizzlenpop.frizzlenStore.util.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Manages the HTTP API server for web communication
 */
public class ApiManager {
    private final FrizzlenStore plugin;
    private HttpServer server;
    private final Map<String, HttpHandler> handlers;
    
    /**
     * Create a new API manager
     * @param plugin The plugin instance
     */
    public ApiManager(FrizzlenStore plugin) {
        this.plugin = plugin;
        this.handlers = new HashMap<>();
        registerHandlers();
    }
    
    /**
     * Register API endpoint handlers
     */
    private void registerHandlers() {
        // Add handlers for different API endpoints
        handlers.put("/api/status", new StatusHandler(plugin));
        handlers.put("/api/products", new ProductHandler(plugin));
        handlers.put("/api/categories", new CategoryHandler(plugin));
        handlers.put("/api/purchases", new PurchaseHandler(plugin));
        handlers.put("/api/coupons", new CouponHandler(plugin));
        handlers.put("/api/players", new PlayerHandler(plugin));
        handlers.put("/api/payment", new PaymentHandler(plugin));
    }
    
    /**
     * Start the API server
     */
    public void startApiServer() {
        try {
            int port = plugin.getConfigManager().getApiPort();
            server = HttpServer.create(new InetSocketAddress(port), 0);
            
            // Register API endpoint handlers
            for (Map.Entry<String, HttpHandler> entry : handlers.entrySet()) {
                server.createContext(entry.getKey(), entry.getValue());
            }
            
            // Add a CORS handler for preflight requests
            server.createContext("/", new CorsHandler());
            
            // Set executor
            server.setExecutor(Executors.newFixedThreadPool(10));
            server.start();
            
            Logger.info("API server started on port " + port);
        } catch (IOException e) {
            Logger.severe("Failed to start API server: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Stop the API server
     */
    public void stopApiServer() {
        if (server != null) {
            server.stop(0);
            Logger.info("API server stopped");
        }
    }
    
    /**
     * Handle CORS for API requests
     */
    private static class CorsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Set CORS headers
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
            
            // Handle preflight requests
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            // Send 404 for unknown endpoints
            String response = "{\"error\": \"Not Found\"}";
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(404, response.length());
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }
    
    /**
     * Execute a task on the main server thread
     * @param runnable The task to execute
     */
    public void runOnMainThread(Runnable runnable) {
        Bukkit.getScheduler().runTask(plugin, runnable);
    }
} 