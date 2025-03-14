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
import org.frizzlenpop.frizzlenStore.api.handlers.AuthHandler;
import org.frizzlenpop.frizzlenStore.api.handlers.UploadHandler;
import org.frizzlenpop.frizzlenStore.api.handlers.StaticFileHandler;
import org.frizzlenpop.frizzlenStore.util.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.io.File;

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
        handlers.put("/api/auth", new AuthHandler(plugin));
        handlers.put("/api/upload", new UploadHandler(plugin));
        
        // Add a simple test endpoint that requires no authentication
        handlers.put("/api/test", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                Logger.info("Test endpoint accessed from: " + exchange.getRemoteAddress());
                String response = "{\"status\":\"ok\",\"message\":\"API server is running\"}";
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        });
    }
    
    /**
     * Start the API server
     */
    public void startApiServer() {
        try {
            Logger.info("Starting API server...");
            int port = plugin.getConfigManager().getApiPort();
            Logger.info("Using port: " + port);
            
            // Bind to 0.0.0.0 (all interfaces) instead of the default
            Logger.info("Attempting to bind to 0.0.0.0:" + port);
            try {
                server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
                Logger.info("HTTP server created successfully");
            } catch (IOException e) {
                // If binding to 0.0.0.0 fails, try binding to localhost
                Logger.warning("Failed to bind to 0.0.0.0:" + port + ", trying localhost... Error: " + e.getMessage());
                server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
                Logger.info("HTTP server created successfully on localhost:" + port);
            }
            
            // Register API endpoint handlers
            Logger.info("Registering API endpoint handlers...");
            for (Map.Entry<String, HttpHandler> entry : handlers.entrySet()) {
                server.createContext(entry.getKey(), entry.getValue());
                Logger.info("Registered handler for: " + entry.getKey());
            }
            
            // Add static file handler for uploads
            String uploadsDir = plugin.getDataFolder().getAbsolutePath() + File.separator + "uploads";
            server.createContext("/uploads", new StaticFileHandler(plugin, uploadsDir, "/uploads"));
            Logger.info("Registered static file handler for uploads directory: " + uploadsDir);
            
            // Add a CORS handler for preflight requests
            server.createContext("/", new CorsHandler());
            Logger.info("Registered CORS handler");
            
            // Set executor
            Logger.info("Setting up thread pool...");
            server.setExecutor(Executors.newFixedThreadPool(10));
            
            // Start the server
            Logger.info("Starting HTTP server...");
            server.start();
            
            // Verify port is actually bound
            InetSocketAddress address = server.getAddress();
            Logger.info("API server started successfully on " + address.getHostString() + ":" + address.getPort());
            
            // Log a verification message to help troubleshoot
            Logger.info("To verify if the API is accessible, try: curl http://localhost:" + port + "/api/status");
        } catch (IOException e) {
            Logger.severe("Failed to start API server: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            Logger.severe("Unexpected error starting API server: " + e.getMessage());
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
    private class CorsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
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