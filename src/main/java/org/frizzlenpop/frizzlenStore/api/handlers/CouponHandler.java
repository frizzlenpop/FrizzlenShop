package org.frizzlenpop.frizzlenStore.api.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

/**
 * Handles API requests for coupons
 */
public class CouponHandler implements HttpHandler {

    private final FrizzlenStore plugin;

    /**
     * Constructor
     * @param plugin The plugin instance
     */
    public CouponHandler(FrizzlenStore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Set CORS headers
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
        
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

            // Handle different request methods
            if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                handleGetCoupons(exchange);
            } else if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                handleCreateCoupon(exchange);
            } else if (exchange.getRequestMethod().equalsIgnoreCase("PUT")) {
                handleUpdateCoupon(exchange);
            } else if (exchange.getRequestMethod().equalsIgnoreCase("DELETE")) {
                handleDeleteCoupon(exchange);
            } else {
                // Method not allowed
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Method not allowed")
                        .toString();
                sendResponse(exchange, 405, response);
            }
        } catch (Exception e) {
            Logger.severe("Error handling coupon request: " + e.getMessage());
            e.printStackTrace();
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Internal server error")
                    .toString();
            sendResponse(exchange, 500, response);
        }
    }

    /**
     * Handle GET request to fetch coupons
     * @param exchange The HTTP exchange
     * @throws IOException If an I/O error occurs
     */
    private void handleGetCoupons(HttpExchange exchange) throws IOException {
        try {
            Connection connection = plugin.getDatabaseManager().getConnection();
            String query = "SELECT * FROM coupons";
            PreparedStatement statement = connection.prepareStatement(query);
            ResultSet resultSet = statement.executeQuery();

            JSONArray coupons = new JSONArray();
            while (resultSet.next()) {
                JSONObject coupon = new JSONObject();
                coupon.put("id", resultSet.getInt("id"));
                coupon.put("code", resultSet.getString("code"));
                coupon.put("discount", resultSet.getDouble("discount"));
                coupon.put("is_percentage", resultSet.getBoolean("is_percentage"));
                coupon.put("expires_at", resultSet.getString("expires_at"));
                coupon.put("max_uses", resultSet.getInt("max_uses"));
                coupon.put("uses", resultSet.getInt("uses"));
                coupons.put(coupon);
            }

            resultSet.close();
            statement.close();

            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("coupons", coupons);
            
            sendResponse(exchange, 200, response.toString());
        } catch (SQLException e) {
            Logger.severe("Database error while getting coupons: " + e.getMessage());
            e.printStackTrace();
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Database error")
                    .toString();
            sendResponse(exchange, 500, response);
        }
    }

    /**
     * Handle POST request to create a new coupon
     * @param exchange The HTTP exchange
     * @throws IOException If an I/O error occurs
     */
    private void handleCreateCoupon(HttpExchange exchange) throws IOException {
        // Read request body
        String requestBody = new BufferedReader(new InputStreamReader(exchange.getRequestBody()))
                .lines().collect(Collectors.joining("\n"));
        JSONObject requestJson = new JSONObject(requestBody);

        try {
            // Validate required fields
            if (!requestJson.has("code") || !requestJson.has("discount")) {
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Missing required fields")
                        .toString();
                sendResponse(exchange, 400, response);
                return;
            }

            String code = requestJson.getString("code");
            double discount = requestJson.getDouble("discount");
            boolean isPercentage = requestJson.optBoolean("is_percentage", true);
            String expiresAt = requestJson.optString("expires_at", null);
            int maxUses = requestJson.optInt("max_uses", -1);

            Connection connection = plugin.getDatabaseManager().getConnection();
            String query = "INSERT INTO coupons (code, discount, is_percentage, expires_at, max_uses, uses) VALUES (?, ?, ?, ?, ?, 0)";
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setString(1, code);
            statement.setDouble(2, discount);
            statement.setBoolean(3, isPercentage);
            statement.setString(4, expiresAt);
            statement.setInt(5, maxUses);
            statement.executeUpdate();
            statement.close();

            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("message", "Coupon created successfully");
            
            sendResponse(exchange, 201, response.toString());
        } catch (SQLException e) {
            Logger.severe("Database error while creating coupon: " + e.getMessage());
            e.printStackTrace();
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Database error")
                    .toString();
            sendResponse(exchange, 500, response);
        }
    }

    /**
     * Handle PUT request to update a coupon
     * @param exchange The HTTP exchange
     * @throws IOException If an I/O error occurs
     */
    private void handleUpdateCoupon(HttpExchange exchange) throws IOException {
        // Read request body
        String requestBody = new BufferedReader(new InputStreamReader(exchange.getRequestBody()))
                .lines().collect(Collectors.joining("\n"));
        JSONObject requestJson = new JSONObject(requestBody);

        try {
            // Validate required fields
            if (!requestJson.has("id")) {
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Missing coupon ID")
                        .toString();
                sendResponse(exchange, 400, response);
                return;
            }

            int id = requestJson.getInt("id");
            
            // Build update query dynamically based on provided fields
            StringBuilder queryBuilder = new StringBuilder("UPDATE coupons SET ");
            boolean hasUpdates = false;
            
            if (requestJson.has("code")) {
                queryBuilder.append("code = ?, ");
                hasUpdates = true;
            }
            
            if (requestJson.has("discount")) {
                queryBuilder.append("discount = ?, ");
                hasUpdates = true;
            }
            
            if (requestJson.has("is_percentage")) {
                queryBuilder.append("is_percentage = ?, ");
                hasUpdates = true;
            }
            
            if (requestJson.has("expires_at")) {
                queryBuilder.append("expires_at = ?, ");
                hasUpdates = true;
            }
            
            if (requestJson.has("max_uses")) {
                queryBuilder.append("max_uses = ?, ");
                hasUpdates = true;
            }
            
            if (!hasUpdates) {
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "No fields to update")
                        .toString();
                sendResponse(exchange, 400, response);
                return;
            }
            
            // Remove trailing comma and space
            String query = queryBuilder.substring(0, queryBuilder.length() - 2) + " WHERE id = ?";
            
            Connection connection = plugin.getDatabaseManager().getConnection();
            PreparedStatement statement = connection.prepareStatement(query);
            
            int paramIndex = 1;
            
            if (requestJson.has("code")) {
                statement.setString(paramIndex++, requestJson.getString("code"));
            }
            
            if (requestJson.has("discount")) {
                statement.setDouble(paramIndex++, requestJson.getDouble("discount"));
            }
            
            if (requestJson.has("is_percentage")) {
                statement.setBoolean(paramIndex++, requestJson.getBoolean("is_percentage"));
            }
            
            if (requestJson.has("expires_at")) {
                statement.setString(paramIndex++, requestJson.getString("expires_at"));
            }
            
            if (requestJson.has("max_uses")) {
                statement.setInt(paramIndex++, requestJson.getInt("max_uses"));
            }
            
            statement.setInt(paramIndex, id);
            int rowsAffected = statement.executeUpdate();
            statement.close();
            
            if (rowsAffected == 0) {
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Coupon not found")
                        .toString();
                sendResponse(exchange, 404, response);
                return;
            }

            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("message", "Coupon updated successfully");
            
            sendResponse(exchange, 200, response.toString());
        } catch (SQLException e) {
            Logger.severe("Database error while updating coupon: " + e.getMessage());
            e.printStackTrace();
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Database error")
                    .toString();
            sendResponse(exchange, 500, response);
        }
    }

    /**
     * Handle DELETE request to delete a coupon
     * @param exchange The HTTP exchange
     * @throws IOException If an I/O error occurs
     */
    private void handleDeleteCoupon(HttpExchange exchange) throws IOException {
        // Get coupon id from path
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
            int id = Integer.parseInt(parts[3]);
            
            Connection connection = plugin.getDatabaseManager().getConnection();
            String query = "DELETE FROM coupons WHERE id = ?";
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setInt(1, id);
            int rowsAffected = statement.executeUpdate();
            statement.close();
            
            if (rowsAffected == 0) {
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Coupon not found")
                        .toString();
                sendResponse(exchange, 404, response);
                return;
            }

            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("message", "Coupon deleted successfully");
            
            sendResponse(exchange, 200, response.toString());
        } catch (NumberFormatException e) {
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Invalid coupon ID")
                    .toString();
            sendResponse(exchange, 400, response);
        } catch (SQLException e) {
            Logger.severe("Database error while deleting coupon: " + e.getMessage());
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