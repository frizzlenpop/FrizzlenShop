package org.frizzlenpop.frizzlenStore.api.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.Bukkit;
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
 * Handles API requests for purchases
 */
public class PurchaseHandler implements HttpHandler {

    private final FrizzlenStore plugin;

    /**
     * Constructor
     * @param plugin The plugin instance
     */
    public PurchaseHandler(FrizzlenStore plugin) {
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

            // Handle different request methods and paths
            String path = exchange.getRequestURI().getPath();
            
            if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                if (path.endsWith("/purchases") || path.endsWith("/purchases/")) {
                    handleGetPurchases(exchange);
                } else if (path.contains("/purchases/player/")) {
                    handleGetPlayerPurchases(exchange);
                } else if (path.contains("/purchases/pending")) {
                    handleGetPendingPurchases(exchange);
                } else if (path.contains("/purchases/")) {
                    handleGetPurchase(exchange);
                } else {
                    // Not found
                    String response = new JSONObject()
                            .put("success", false)
                            .put("error", "Not found")
                            .toString();
                    sendResponse(exchange, 404, response);
                }
            } else if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                if (path.contains("/purchases/deliver/")) {
                    handleDeliverPurchase(exchange);
                } else {
                    handleCreatePurchase(exchange);
                }
            } else if (exchange.getRequestMethod().equalsIgnoreCase("PUT")) {
                handleUpdatePurchase(exchange);
            } else if (exchange.getRequestMethod().equalsIgnoreCase("DELETE")) {
                handleDeletePurchase(exchange);
            } else {
                // Method not allowed
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Method not allowed")
                        .toString();
                sendResponse(exchange, 405, response);
            }
        } catch (Exception e) {
            Logger.severe("Error handling purchase request: " + e.getMessage());
            e.printStackTrace();
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Internal server error")
                    .toString();
            sendResponse(exchange, 500, response);
        }
    }

    /**
     * Handle GET request to fetch all purchases
     * @param exchange The HTTP exchange
     * @throws IOException If an I/O error occurs
     */
    private void handleGetPurchases(HttpExchange exchange) throws IOException {
        try {
            Connection connection = plugin.getDatabaseManager().getConnection();
            String query = "SELECT pu.*, pr.name as product_name, pl.name as player_name " +
                           "FROM purchases pu " +
                           "JOIN products pr ON pu.product_id = pr.id " +
                           "LEFT JOIN players pl ON pu.player_uuid = pl.uuid " +
                           "ORDER BY pu.created_at DESC LIMIT 100";
            PreparedStatement statement = connection.prepareStatement(query);
            ResultSet resultSet = statement.executeQuery();

            JSONArray purchases = new JSONArray();
            while (resultSet.next()) {
                JSONObject purchase = new JSONObject();
                purchase.put("id", resultSet.getInt("id"));
                purchase.put("player_uuid", resultSet.getString("player_uuid"));
                purchase.put("player_name", resultSet.getString("player_name"));
                purchase.put("product_id", resultSet.getInt("product_id"));
                purchase.put("product_name", resultSet.getString("product_name"));
                purchase.put("price", resultSet.getDouble("price"));
                purchase.put("payment_id", resultSet.getInt("payment_id"));
                purchase.put("status", resultSet.getString("status"));
                purchase.put("created_at", resultSet.getString("created_at"));
                purchase.put("delivered_at", resultSet.getString("delivered_at"));
                purchases.put(purchase);
            }

            resultSet.close();
            statement.close();

            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("purchases", purchases);
            
            sendResponse(exchange, 200, response.toString());
        } catch (SQLException e) {
            Logger.severe("Database error while getting purchases: " + e.getMessage());
            e.printStackTrace();
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Database error")
                    .toString();
            sendResponse(exchange, 500, response);
        }
    }

    /**
     * Handle GET request to fetch a single purchase
     * @param exchange The HTTP exchange
     * @throws IOException If an I/O error occurs
     */
    private void handleGetPurchase(HttpExchange exchange) throws IOException {
        // Get purchase id from path
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
            int purchaseId = Integer.parseInt(parts[3]);
            
            Connection connection = plugin.getDatabaseManager().getConnection();
            String query = "SELECT pu.*, pr.name as product_name, pr.commands, pl.name as player_name " +
                           "FROM purchases pu " +
                           "JOIN products pr ON pu.product_id = pr.id " +
                           "LEFT JOIN players pl ON pu.player_uuid = pl.uuid " +
                           "WHERE pu.id = ?";
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setInt(1, purchaseId);
            ResultSet resultSet = statement.executeQuery();

            if (!resultSet.next()) {
                resultSet.close();
                statement.close();
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Purchase not found")
                        .toString();
                sendResponse(exchange, 404, response);
                return;
            }
            
            JSONObject purchase = new JSONObject();
            purchase.put("id", resultSet.getInt("id"));
            purchase.put("player_uuid", resultSet.getString("player_uuid"));
            purchase.put("player_name", resultSet.getString("player_name"));
            purchase.put("product_id", resultSet.getInt("product_id"));
            purchase.put("product_name", resultSet.getString("product_name"));
            purchase.put("price", resultSet.getDouble("price"));
            purchase.put("payment_id", resultSet.getInt("payment_id"));
            purchase.put("status", resultSet.getString("status"));
            purchase.put("created_at", resultSet.getString("created_at"));
            purchase.put("delivered_at", resultSet.getString("delivered_at"));
            purchase.put("commands", new JSONArray(resultSet.getString("commands")));
            
            resultSet.close();
            statement.close();

            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("purchase", purchase);
            
            sendResponse(exchange, 200, response.toString());
        } catch (NumberFormatException e) {
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Invalid purchase ID")
                    .toString();
            sendResponse(exchange, 400, response);
        } catch (SQLException e) {
            Logger.severe("Database error while getting purchase: " + e.getMessage());
            e.printStackTrace();
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Database error")
                    .toString();
            sendResponse(exchange, 500, response);
        }
    }

    /**
     * Handle GET request to fetch purchases for a player
     * @param exchange The HTTP exchange
     * @throws IOException If an I/O error occurs
     */
    private void handleGetPlayerPurchases(HttpExchange exchange) throws IOException {
        // Get player UUID from path
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        if (parts.length < 5) {
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Invalid path")
                    .toString();
            sendResponse(exchange, 400, response);
            return;
        }
        
        try {
            String playerUuid = parts[4];
            
            Connection connection = plugin.getDatabaseManager().getConnection();
            String query = "SELECT pu.*, pr.name as product_name, pl.name as player_name " +
                           "FROM purchases pu " +
                           "JOIN products pr ON pu.product_id = pr.id " +
                           "LEFT JOIN players pl ON pu.player_uuid = pl.uuid " +
                           "WHERE pu.player_uuid = ? " +
                           "ORDER BY pu.created_at DESC";
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setString(1, playerUuid);
            ResultSet resultSet = statement.executeQuery();

            JSONArray purchases = new JSONArray();
            while (resultSet.next()) {
                JSONObject purchase = new JSONObject();
                purchase.put("id", resultSet.getInt("id"));
                purchase.put("player_uuid", resultSet.getString("player_uuid"));
                purchase.put("player_name", resultSet.getString("player_name"));
                purchase.put("product_id", resultSet.getInt("product_id"));
                purchase.put("product_name", resultSet.getString("product_name"));
                purchase.put("price", resultSet.getDouble("price"));
                purchase.put("payment_id", resultSet.getInt("payment_id"));
                purchase.put("status", resultSet.getString("status"));
                purchase.put("created_at", resultSet.getString("created_at"));
                purchase.put("delivered_at", resultSet.getString("delivered_at"));
                purchases.put(purchase);
            }

            resultSet.close();
            statement.close();

            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("player_uuid", playerUuid);
            response.put("purchases", purchases);
            
            sendResponse(exchange, 200, response.toString());
        } catch (SQLException e) {
            Logger.severe("Database error while getting player purchases: " + e.getMessage());
            e.printStackTrace();
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Database error")
                    .toString();
            sendResponse(exchange, 500, response);
        }
    }

    /**
     * Handle GET request to fetch pending purchases
     * @param exchange The HTTP exchange
     * @throws IOException If an I/O error occurs
     */
    private void handleGetPendingPurchases(HttpExchange exchange) throws IOException {
        try {
            Connection connection = plugin.getDatabaseManager().getConnection();
            String query = "SELECT pu.*, pr.name as product_name, pl.name as player_name " +
                           "FROM purchases pu " +
                           "JOIN products pr ON pu.product_id = pr.id " +
                           "LEFT JOIN players pl ON pu.player_uuid = pl.uuid " +
                           "WHERE pu.status = 'pending' " +
                           "ORDER BY pu.created_at ASC";
            PreparedStatement statement = connection.prepareStatement(query);
            ResultSet resultSet = statement.executeQuery();

            JSONArray purchases = new JSONArray();
            while (resultSet.next()) {
                JSONObject purchase = new JSONObject();
                purchase.put("id", resultSet.getInt("id"));
                purchase.put("player_uuid", resultSet.getString("player_uuid"));
                purchase.put("player_name", resultSet.getString("player_name"));
                purchase.put("product_id", resultSet.getInt("product_id"));
                purchase.put("product_name", resultSet.getString("product_name"));
                purchase.put("price", resultSet.getDouble("price"));
                purchase.put("payment_id", resultSet.getInt("payment_id"));
                purchase.put("status", resultSet.getString("status"));
                purchase.put("created_at", resultSet.getString("created_at"));
                purchases.put(purchase);
            }

            resultSet.close();
            statement.close();

            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("purchases", purchases);
            
            sendResponse(exchange, 200, response.toString());
        } catch (SQLException e) {
            Logger.severe("Database error while getting pending purchases: " + e.getMessage());
            e.printStackTrace();
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Database error")
                    .toString();
            sendResponse(exchange, 500, response);
        }
    }

    /**
     * Handle POST request to create a new purchase
     * @param exchange The HTTP exchange
     * @throws IOException If an I/O error occurs
     */
    private void handleCreatePurchase(HttpExchange exchange) throws IOException {
        // Read request body
        String requestBody = new BufferedReader(new InputStreamReader(exchange.getRequestBody()))
                .lines().collect(Collectors.joining("\n"));
        JSONObject requestJson = new JSONObject(requestBody);

        try {
            // Validate required fields
            if (!requestJson.has("player_uuid") || !requestJson.has("product_id")) {
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Missing required fields")
                        .toString();
                sendResponse(exchange, 400, response);
                return;
            }

            String playerUuid = requestJson.getString("player_uuid");
            int productId = requestJson.getInt("product_id");
            int paymentId = requestJson.optInt("payment_id", -1);
            
            // Verify product exists and get details
            Connection connection = plugin.getDatabaseManager().getConnection();
            String productQuery = "SELECT * FROM products WHERE id = ?";
            PreparedStatement productStatement = connection.prepareStatement(productQuery);
            productStatement.setInt(1, productId);
            ResultSet productResultSet = productStatement.executeQuery();
            
            if (!productResultSet.next()) {
                productResultSet.close();
                productStatement.close();
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Product not found")
                        .toString();
                sendResponse(exchange, 404, response);
                return;
            }
            
            double price = requestJson.optDouble("price", productResultSet.getDouble("price"));
            if (productResultSet.getBoolean("is_on_sale")) {
                price = productResultSet.getDouble("sale_price");
            }
            
            String productName = productResultSet.getString("name");
            String commands = productResultSet.getString("commands");
            productResultSet.close();
            productStatement.close();
            
            // Create the purchase through the purchase manager
            boolean purchaseSuccess = plugin.getPurchaseManager().createPurchase(
                playerUuid, 
                productId, 
                price, 
                productName, 
                "API", 
                paymentId > 0 ? String.valueOf(paymentId) : null
            );
            
            if (!purchaseSuccess) {
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Failed to create purchase")
                        .toString();
                sendResponse(exchange, 500, response);
                return;
            }

            // Get the purchase ID after creating it successfully
            int purchaseId = -1;
            PreparedStatement idStatement = null;
            ResultSet idResultSet = null;
            try {
                String sql = "SELECT id FROM purchases WHERE player_uuid = ? AND product_id = ? ORDER BY id DESC LIMIT 1";
                idStatement = connection.prepareStatement(sql);
                idStatement.setString(1, playerUuid);
                idStatement.setInt(2, productId);
                idResultSet = idStatement.executeQuery();
                if (idResultSet.next()) {
                    purchaseId = idResultSet.getInt("id");
                }
            } catch (SQLException e) {
                Logger.warning("Could not retrieve purchase ID: " + e.getMessage());
            } finally {
                try {
                    if (idResultSet != null) idResultSet.close();
                    if (idStatement != null) idStatement.close();
                } catch (SQLException e) {
                    Logger.severe("Error closing resources: " + e.getMessage());
                }
            }
            
            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("message", "Purchase created successfully");
            response.put("purchase_id", purchaseId);
            
            sendResponse(exchange, 201, response.toString());
        } catch (SQLException e) {
            Logger.severe("Database error while creating purchase: " + e.getMessage());
            e.printStackTrace();
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Database error")
                    .toString();
            sendResponse(exchange, 500, response);
        }
    }

    /**
     * Handle POST request to deliver a purchase
     * @param exchange The HTTP exchange
     * @throws IOException If an I/O error occurs
     */
    private void handleDeliverPurchase(HttpExchange exchange) throws IOException {
        // Get purchase id from path
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        if (parts.length < 5) {
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Invalid path")
                    .toString();
            sendResponse(exchange, 400, response);
            return;
        }
        
        try {
            int purchaseId = Integer.parseInt(parts[4]);
            
            // Get purchase details
            Connection connection = plugin.getDatabaseManager().getConnection();
            String query = "SELECT pu.*, pr.commands FROM purchases pu " +
                          "JOIN products pr ON pu.product_id = pr.id " +
                          "WHERE pu.id = ?";
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setInt(1, purchaseId);
            ResultSet resultSet = statement.executeQuery();
            
            if (!resultSet.next()) {
                resultSet.close();
                statement.close();
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Purchase not found")
                        .toString();
                sendResponse(exchange, 404, response);
                return;
            }
            
            String playerUuid = resultSet.getString("player_uuid");
            String status = resultSet.getString("status");
            String commands = resultSet.getString("commands");
            
            resultSet.close();
            statement.close();
            
            if (status.equals("delivered")) {
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Purchase already delivered")
                        .toString();
                sendResponse(exchange, 400, response);
                return;
            }
            
            // Try to deliver the purchase
            boolean delivered = plugin.getPurchaseManager().deliverPurchase(purchaseId);
            
            if (!delivered) {
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Failed to deliver purchase. Player may be offline.")
                        .toString();
                sendResponse(exchange, 500, response);
                return;
            }

            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("message", "Purchase delivered successfully");
            
            sendResponse(exchange, 200, response.toString());
        } catch (NumberFormatException e) {
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Invalid purchase ID")
                    .toString();
            sendResponse(exchange, 400, response);
        } catch (SQLException e) {
            Logger.severe("Database error while delivering purchase: " + e.getMessage());
            e.printStackTrace();
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Database error")
                    .toString();
            sendResponse(exchange, 500, response);
        }
    }

    /**
     * Handle PUT request to update a purchase
     * @param exchange The HTTP exchange
     * @throws IOException If an I/O error occurs
     */
    private void handleUpdatePurchase(HttpExchange exchange) throws IOException {
        // Read request body
        String requestBody = new BufferedReader(new InputStreamReader(exchange.getRequestBody()))
                .lines().collect(Collectors.joining("\n"));
        JSONObject requestJson = new JSONObject(requestBody);

        try {
            // Validate required fields
            if (!requestJson.has("id") || !requestJson.has("status")) {
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Missing required fields")
                        .toString();
                sendResponse(exchange, 400, response);
                return;
            }

            int id = requestJson.getInt("id");
            String status = requestJson.getString("status");
            
            // Verify status is valid
            if (!status.equals("pending") && !status.equals("delivered") && !status.equals("refunded") && !status.equals("cancelled")) {
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Invalid status")
                        .toString();
                sendResponse(exchange, 400, response);
                return;
            }
            
            // Update purchase status
            Connection connection = plugin.getDatabaseManager().getConnection();
            String updateQuery = "UPDATE purchases SET status = ? WHERE id = ?";
            PreparedStatement updateStatement = connection.prepareStatement(updateQuery);
            updateStatement.setString(1, status);
            updateStatement.setInt(2, id);
            int rowsAffected = updateStatement.executeUpdate();
            updateStatement.close();
            
            if (rowsAffected == 0) {
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Purchase not found")
                        .toString();
                sendResponse(exchange, 404, response);
                return;
            }

            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("message", "Purchase updated successfully");
            
            sendResponse(exchange, 200, response.toString());
        } catch (SQLException e) {
            Logger.severe("Database error while updating purchase: " + e.getMessage());
            e.printStackTrace();
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Database error")
                    .toString();
            sendResponse(exchange, 500, response);
        }
    }

    /**
     * Handle DELETE request to delete a purchase
     * @param exchange The HTTP exchange
     * @throws IOException If an I/O error occurs
     */
    private void handleDeletePurchase(HttpExchange exchange) throws IOException {
        // Get purchase id from path
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
            String query = "DELETE FROM purchases WHERE id = ?";
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setInt(1, id);
            int rowsAffected = statement.executeUpdate();
            statement.close();
            
            if (rowsAffected == 0) {
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Purchase not found")
                        .toString();
                sendResponse(exchange, 404, response);
                return;
            }

            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("message", "Purchase deleted successfully");
            
            sendResponse(exchange, 200, response.toString());
        } catch (NumberFormatException e) {
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Invalid purchase ID")
                    .toString();
            sendResponse(exchange, 400, response);
        } catch (SQLException e) {
            Logger.severe("Database error while deleting purchase: " + e.getMessage());
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