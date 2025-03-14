package org.frizzlenpop.frizzlenStore.api.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.frizzlenpop.frizzlenStore.FrizzlenStore;
import org.frizzlenpop.frizzlenStore.util.Logger;
import org.bukkit.configuration.file.FileConfiguration;
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
import java.util.stream.Collectors;

/**
 * Handles API requests for store categories
 */
public class CategoryHandler implements HttpHandler {

    private final FrizzlenStore plugin;

    /**
     * Constructor
     * @param plugin The plugin instance
     */
    public CategoryHandler(FrizzlenStore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Set CORS headers
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With, Accept, Origin");
        exchange.getResponseHeaders().add("Access-Control-Allow-Credentials", "true");
        exchange.getResponseHeaders().add("Access-Control-Max-Age", "3600");
        
        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        try {
            // Handle different request methods
            if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                handleGetCategories(exchange);
            } else if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                // Verify API token for POST requests
                String token = exchange.getRequestHeaders().getFirst("Authorization");
                if (token == null || !token.equals("Bearer " + plugin.getConfig().getString("api.token"))) {
                    String response = new JSONObject()
                            .put("success", false)
                            .put("error", "Unauthorized")
                            .toString();
                    sendResponse(exchange, 401, response);
                    return;
                }
                
                handleCreateCategory(exchange);
            } else if (exchange.getRequestMethod().equalsIgnoreCase("PUT")) {
                // Verify API token for PUT requests
                String token = exchange.getRequestHeaders().getFirst("Authorization");
                if (token == null || !token.equals("Bearer " + plugin.getConfig().getString("api.token"))) {
                    String response = new JSONObject()
                            .put("success", false)
                            .put("error", "Unauthorized")
                            .toString();
                    sendResponse(exchange, 401, response);
                    return;
                }
                
                // Extract category ID from path
                String path = exchange.getRequestURI().getPath();
                String[] pathParts = path.split("/");
                if (pathParts.length > 3) {
                    String categoryId = pathParts[3];
                    handleUpdateCategory(exchange, categoryId);
                } else {
                    String response = new JSONObject()
                            .put("success", false)
                            .put("error", "Missing category ID in path")
                            .toString();
                    sendResponse(exchange, 400, response);
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
            Logger.severe("Error handling category request: " + e.getMessage());
            e.printStackTrace();
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Internal server error")
                    .toString();
            sendResponse(exchange, 500, response);
        }
    }

    /**
     * Handle GET request to fetch categories
     * @param exchange The HTTP exchange
     * @throws IOException If an I/O error occurs
     */
    private void handleGetCategories(HttpExchange exchange) throws IOException {
        try {
            Connection connection = plugin.getDatabaseManager().getConnection();
            String query = "SELECT * FROM categories ORDER BY display_order ASC";
            PreparedStatement statement = connection.prepareStatement(query);
            ResultSet resultSet = statement.executeQuery();

            JSONArray categories = new JSONArray();
            while (resultSet.next()) {
                JSONObject category = new JSONObject();
                try {
                    // First check if the id is a UUID or numeric
                    String idStr = resultSet.getString("id");
                    if (idStr != null && idStr.contains("-")) {
                        // This is likely a UUID
                        category.put("id", idStr);
                    } else {
                        // This is likely a numeric ID
                        category.put("id", resultSet.getInt("id"));
                    }
                    
                    category.put("name", resultSet.getString("name"));
                    category.put("description", resultSet.getString("description"));
                    
                    try {
                        category.put("display_order", resultSet.getInt("display_order"));
                    } catch (SQLException e) {
                        // If display_order doesn't exist, use id as fallback
                        // Only try to use id as display_order if it's numeric
                        if (!idStr.contains("-")) {
                            category.put("display_order", resultSet.getInt("id"));
                        } else {
                            category.put("display_order", 0);
                        }
                    }
                    
                    // Safely try to get image_url which may not exist
                    try {
                        String imageUrl = resultSet.getString("image_url");
                        if (imageUrl != null) {
                            category.put("image_url", imageUrl);
                        } else {
                            category.put("image_url", "");
                        }
                    } catch (SQLException e) {
                        // If image_url doesn't exist, use a default empty string
                        category.put("image_url", "");
                    }
                    
                    categories.put(category);
                } catch (SQLException e) {
                    // Skip this category if there's an error parsing any field
                    Logger.warning("Error parsing category data: " + e.getMessage());
                }
            }

            resultSet.close();
            statement.close();

            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("categories", categories);
            
            sendResponse(exchange, 200, response.toString());
        } catch (SQLException e) {
            Logger.severe("Database error while getting categories: " + e.getMessage());
            e.printStackTrace();
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Database error")
                    .toString();
            sendResponse(exchange, 500, response);
        }
    }

    /**
     * Handle POST request to create a category
     * @param exchange The HTTP exchange
     * @throws IOException If an I/O error occurs
     */
    private void handleCreateCategory(HttpExchange exchange) throws IOException {
        // Read request body
        String requestBody = new BufferedReader(new InputStreamReader(exchange.getRequestBody()))
                .lines().collect(Collectors.joining("\n"));
        JSONObject requestJson = new JSONObject(requestBody);

        try {
            // Validate required fields
            if (!requestJson.has("name")) {
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Missing required field: name")
                        .toString();
                sendResponse(exchange, 400, response);
                return;
            }

            String name = requestJson.getString("name");
            String description = requestJson.optString("description", "");
            String imageUrl = requestJson.optString("image_url", "");
            int displayOrder = requestJson.optInt("displayOrder", 0);

            // Create category record
            Connection connection = plugin.getDatabaseManager().getConnection();
            String insertQuery = "INSERT INTO categories (name, description, image_url, display_order) VALUES (?, ?, ?, ?)";
            PreparedStatement insertStatement = connection.prepareStatement(insertQuery, PreparedStatement.RETURN_GENERATED_KEYS);
            insertStatement.setString(1, name);
            insertStatement.setString(2, description);
            insertStatement.setString(3, imageUrl);
            insertStatement.setInt(4, displayOrder);
            
            int rowsAffected = insertStatement.executeUpdate();
            if (rowsAffected == 0) {
                insertStatement.close();
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Failed to create category")
                        .toString();
                sendResponse(exchange, 500, response);
                return;
            }
            
            // Get the generated ID
            ResultSet generatedKeys = insertStatement.getGeneratedKeys();
            int categoryId = -1;
            if (generatedKeys.next()) {
                categoryId = generatedKeys.getInt(1);
            }
            generatedKeys.close();
            insertStatement.close();
            
            // Return the created category
            JSONObject categoryJson = new JSONObject();
            categoryJson.put("id", Integer.toString(categoryId));
            categoryJson.put("name", name);
            categoryJson.put("description", description);
            categoryJson.put("image_url", imageUrl);
            categoryJson.put("display_order", displayOrder);
            
            String response = new JSONObject()
                    .put("success", true)
                    .put("category", categoryJson)
                    .toString();
            
            sendResponse(exchange, 201, response);
        } catch (SQLException e) {
            Logger.severe("Database error creating category: " + e.getMessage());
            e.printStackTrace();
            
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Database error: " + e.getMessage())
                    .toString();
            
            sendResponse(exchange, 500, response);
        } catch (Exception e) {
            Logger.severe("Error creating category: " + e.getMessage());
            e.printStackTrace();
            
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Internal server error: " + e.getMessage())
                    .toString();
            
            sendResponse(exchange, 500, response);
        }
    }

    /**
     * Handle PUT request to update a category
     * @param exchange The HTTP exchange
     * @param categoryId The ID of the category to update
     * @throws IOException If an I/O error occurs
     */
    private void handleUpdateCategory(HttpExchange exchange, String categoryId) throws IOException {
        // Read request body
        String requestBody = new BufferedReader(new InputStreamReader(exchange.getRequestBody()))
                .lines().collect(Collectors.joining("\n"));
        JSONObject requestJson = new JSONObject(requestBody);

        try {
            // Check if category exists
            Connection connection = plugin.getDatabaseManager().getConnection();
            String checkQuery = "SELECT * FROM categories WHERE id = ?";
            PreparedStatement checkStatement = connection.prepareStatement(checkQuery);
            checkStatement.setInt(1, Integer.parseInt(categoryId));
            ResultSet resultSet = checkStatement.executeQuery();
            
            if (!resultSet.next()) {
                resultSet.close();
                checkStatement.close();
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Category not found")
                        .toString();
                sendResponse(exchange, 404, response);
                return;
            }
            
            resultSet.close();
            checkStatement.close();
            
            // Get update fields
            String name = requestJson.optString("name", null);
            String description = requestJson.optString("description", null);
            String imageUrl = requestJson.optString("image_url", null);
            Integer displayOrder = requestJson.has("displayOrder") ? requestJson.getInt("displayOrder") : null;
            
            // Build update query
            StringBuilder updateQuery = new StringBuilder("UPDATE categories SET ");
            boolean needsComma = false;
            
            if (name != null) {
                updateQuery.append("name = ?");
                needsComma = true;
            }
            
            if (description != null) {
                if (needsComma) updateQuery.append(", ");
                updateQuery.append("description = ?");
                needsComma = true;
            }
            
            if (imageUrl != null) {
                if (needsComma) updateQuery.append(", ");
                updateQuery.append("image_url = ?");
                needsComma = true;
            }
            
            if (displayOrder != null) {
                if (needsComma) updateQuery.append(", ");
                updateQuery.append("display_order = ?");
            }
            
            updateQuery.append(" WHERE id = ?");
            
            // Execute update
            PreparedStatement updateStatement = connection.prepareStatement(updateQuery.toString());
            int paramIndex = 1;
            
            if (name != null) {
                updateStatement.setString(paramIndex++, name);
            }
            
            if (description != null) {
                updateStatement.setString(paramIndex++, description);
            }
            
            if (imageUrl != null) {
                updateStatement.setString(paramIndex++, imageUrl);
            }
            
            if (displayOrder != null) {
                updateStatement.setInt(paramIndex++, displayOrder);
            }
            
            updateStatement.setInt(paramIndex, Integer.parseInt(categoryId));
            
            int rowsAffected = updateStatement.executeUpdate();
            updateStatement.close();
            
            if (rowsAffected == 0) {
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "No changes made")
                        .toString();
                sendResponse(exchange, 400, response);
                return;
            }
            
            // Get updated category
            String getQuery = "SELECT * FROM categories WHERE id = ?";
            PreparedStatement getStatement = connection.prepareStatement(getQuery);
            getStatement.setInt(1, Integer.parseInt(categoryId));
            ResultSet updatedResult = getStatement.executeQuery();
            
            if (updatedResult.next()) {
                JSONObject categoryJson = new JSONObject();
                categoryJson.put("id", Integer.toString(updatedResult.getInt("id")));
                categoryJson.put("name", updatedResult.getString("name"));
                categoryJson.put("description", updatedResult.getString("description"));
                categoryJson.put("image_url", updatedResult.getString("image_url"));
                categoryJson.put("display_order", updatedResult.getInt("display_order"));
                
                String response = new JSONObject()
                        .put("success", true)
                        .put("category", categoryJson)
                        .toString();
                
                updatedResult.close();
                getStatement.close();
                
                sendResponse(exchange, 200, response);
            } else {
                updatedResult.close();
                getStatement.close();
                
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Failed to retrieve updated category")
                        .toString();
                sendResponse(exchange, 500, response);
            }
        } catch (NumberFormatException e) {
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Invalid category ID format")
                    .toString();
            sendResponse(exchange, 400, response);
        } catch (SQLException e) {
            Logger.severe("Database error updating category: " + e.getMessage());
            e.printStackTrace();
            
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Database error: " + e.getMessage())
                    .toString();
            
            sendResponse(exchange, 500, response);
        } catch (Exception e) {
            Logger.severe("Error updating category: " + e.getMessage());
            e.printStackTrace();
            
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Internal server error: " + e.getMessage())
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