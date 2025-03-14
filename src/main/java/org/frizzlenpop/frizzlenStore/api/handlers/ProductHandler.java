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
import java.util.stream.Collectors;

/**
 * Handles API requests for store products
 */
public class ProductHandler implements HttpHandler {

    private final FrizzlenStore plugin;

    /**
     * Constructor
     * @param plugin The plugin instance
     */
    public ProductHandler(FrizzlenStore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Set CORS headers
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With, Accept, Origin");
        exchange.getResponseHeaders().add("Access-Control-Allow-Credentials", "true");
        exchange.getResponseHeaders().add("Access-Control-Max-Age", "3600");
        
        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        try {
            // Verify API token for non-public endpoints
            String path = exchange.getRequestURI().getPath();
            if (!(exchange.getRequestMethod().equalsIgnoreCase("GET") && !path.contains("/admin"))) {
                String token = exchange.getRequestHeaders().getFirst("Authorization");
                if (token == null || !token.equals("Bearer " + plugin.getConfig().getString("api.token"))) {
                    String response = new JSONObject()
                            .put("success", false)
                            .put("error", "Unauthorized")
                            .toString();
                    sendResponse(exchange, 401, response);
                    return;
                }
            }

            // Handle different request methods
            if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                if (path.endsWith("/products") || path.endsWith("/products/")) {
                    handleGetProducts(exchange);
                } else if (path.contains("/products/category/")) {
                    handleGetProductsByCategory(exchange);
                } else if (path.contains("/products/")) {
                    handleGetProduct(exchange);
                } else {
                    // Not found
                    String response = new JSONObject()
                            .put("success", false)
                            .put("error", "Not found")
                            .toString();
                    sendResponse(exchange, 404, response);
                }
            } else if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                handleCreateProduct(exchange);
            } else if (exchange.getRequestMethod().equalsIgnoreCase("PUT")) {
                handleUpdateProduct(exchange);
            } else if (exchange.getRequestMethod().equalsIgnoreCase("DELETE")) {
                handleDeleteProduct(exchange);
            } else {
                // Method not allowed
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Method not allowed")
                        .toString();
                sendResponse(exchange, 405, response);
            }
        } catch (Exception e) {
            Logger.severe("Error handling product request: " + e.getMessage());
            e.printStackTrace();
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Internal server error")
                    .toString();
            sendResponse(exchange, 500, response);
        }
    }

    /**
     * Handle GET request to fetch all products
     * @param exchange The HTTP exchange
     * @throws IOException If an I/O error occurs
     */
    private void handleGetProducts(HttpExchange exchange) throws IOException {
        try {
            Connection connection = plugin.getDatabaseManager().getConnection();
            
            // Check if display_order exists in the products table
            boolean hasDisplayOrder = false;
            try {
                ResultSet rs = connection.getMetaData().getColumns(null, null, "products", "display_order");
                hasDisplayOrder = rs.next();
                rs.close();
            } catch (SQLException e) {
                Logger.warning("Error checking for display_order column: " + e.getMessage());
            }
            
            String query;
            if (hasDisplayOrder) {
                query = "SELECT p.*, c.name as category_name FROM products p " +
                      "JOIN categories c ON p.category_id = c.id " +
                      "ORDER BY p.display_order ASC";
            } else {
                query = "SELECT p.*, c.name as category_name FROM products p " +
                      "JOIN categories c ON p.category_id = c.id " +
                      "ORDER BY p.id ASC";
            }
            
            PreparedStatement statement = connection.prepareStatement(query);
            ResultSet resultSet = statement.executeQuery();

            JSONArray products = new JSONArray();
            while (resultSet.next()) {
                JSONObject product = new JSONObject();
                try {
                    // First check if the id is a UUID or numeric
                    String idStr = resultSet.getString("id");
                    if (idStr != null && idStr.contains("-")) {
                        // This is likely a UUID
                        product.put("id", idStr);
                    } else {
                        // This is likely a numeric ID
                        product.put("id", resultSet.getInt("id"));
                    }
                    
                    product.put("name", resultSet.getString("name"));
                    product.put("description", resultSet.getString("description"));
                    product.put("price", resultSet.getDouble("price"));
                    
                    // Only include display_order if the column exists
                    if (hasDisplayOrder) {
                        try {
                            product.put("display_order", resultSet.getInt("display_order"));
                        } catch (SQLException e) {
                            // Ignore if the column doesn't exist
                        }
                    }
                    
                    if (resultSet.getObject("sale_price") != null) {
                        product.put("sale_price", resultSet.getDouble("sale_price"));
                    }
                    
                    // Get category_id safely
                    try {
                        String catIdStr = resultSet.getString("category_id");
                        if (catIdStr != null && catIdStr.contains("-")) {
                            // This is likely a UUID
                            product.put("category_id", catIdStr);
                        } else {
                            // This is likely a numeric ID
                            product.put("category_id", resultSet.getInt("category_id"));
                        }
                    } catch (Exception e) {
                        // Use a default value if conversion fails
                        product.put("category_id", 0);
                    }
                    
                    product.put("category", resultSet.getString("category_name"));
                    product.put("image", resultSet.getString("image_url"));
                    product.put("commands", resultSet.getString("commands").split("\n"));
                    product.put("created_at", resultSet.getTimestamp("created_at").toString());
                    product.put("active", resultSet.getBoolean("active"));
                    products.put(product);
                } catch (SQLException e) {
                    // Ignore if there's an error getting a product
                }
            }

            resultSet.close();
            statement.close();

            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("products", products);
            
            sendResponse(exchange, 200, response.toString());
        } catch (SQLException e) {
            Logger.severe("Database error while getting products: " + e.getMessage());
            e.printStackTrace();
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Database error")
                    .toString();
            sendResponse(exchange, 500, response);
        }
    }

    /**
     * Handle GET request to fetch products by category
     * @param exchange The HTTP exchange
     * @throws IOException If an I/O error occurs
     */
    private void handleGetProductsByCategory(HttpExchange exchange) throws IOException {
        // Get category id from path
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
            // Try to parse the category ID as an integer
            String categoryIdStr = parts[4];
            int categoryId;
            
            try {
                categoryId = Integer.parseInt(categoryIdStr);
            } catch (NumberFormatException e) {
                // If it's not a valid integer, return an empty result set
                Logger.warning("Invalid category ID format: " + categoryIdStr + ". Expected integer.");
                JSONObject response = new JSONObject();
                response.put("success", true);
                response.put("products", new JSONArray());
                sendResponse(exchange, 200, response.toString());
                return;
            }
            
            Connection connection = plugin.getDatabaseManager().getConnection();
            
            // Check if display_order exists in the products table
            boolean hasDisplayOrder = false;
            try {
                ResultSet rs = connection.getMetaData().getColumns(null, null, "products", "display_order");
                hasDisplayOrder = rs.next();
                rs.close();
            } catch (SQLException e) {
                Logger.warning("Error checking for display_order column: " + e.getMessage());
            }
            
            String query;
            if (hasDisplayOrder) {
                query = "SELECT p.*, c.name as category_name FROM products p " +
                      "JOIN categories c ON p.category_id = c.id " +
                      "WHERE p.category_id = ? AND p.active = true " +
                      "ORDER BY p.display_order ASC";
            } else {
                query = "SELECT p.*, c.name as category_name FROM products p " +
                      "JOIN categories c ON p.category_id = c.id " +
                      "WHERE p.category_id = ? AND p.active = true " +
                      "ORDER BY p.id ASC";
            }
            
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setInt(1, categoryId);
            ResultSet resultSet = statement.executeQuery();

            JSONArray products = new JSONArray();
            while (resultSet.next()) {
                JSONObject product = new JSONObject();
                try {
                    // First check if the id is a UUID or numeric
                    String idStr = resultSet.getString("id");
                    if (idStr != null && idStr.contains("-")) {
                        // This is likely a UUID
                        product.put("id", idStr);
                    } else {
                        // This is likely a numeric ID
                        product.put("id", resultSet.getInt("id"));
                    }
                    
                    product.put("name", resultSet.getString("name"));
                    product.put("description", resultSet.getString("description"));
                    product.put("price", resultSet.getDouble("price"));
                    
                    // Only include display_order if the column exists
                    if (hasDisplayOrder) {
                        try {
                            product.put("display_order", resultSet.getInt("display_order"));
                        } catch (SQLException e) {
                            // Ignore if the column doesn't exist
                        }
                    }
                    
                    if (resultSet.getObject("sale_price") != null) {
                        product.put("sale_price", resultSet.getDouble("sale_price"));
                    }
                    
                    // Get category_id safely
                    try {
                        String catIdStr = resultSet.getString("category_id");
                        if (catIdStr != null && catIdStr.contains("-")) {
                            // This is likely a UUID
                            product.put("category_id", catIdStr);
                        } else {
                            // This is likely a numeric ID
                            product.put("category_id", resultSet.getInt("category_id"));
                        }
                    } catch (Exception e) {
                        // Use a default value if conversion fails
                        product.put("category_id", 0);
                    }
                    
                    product.put("category", resultSet.getString("category_name"));
                    product.put("image", resultSet.getString("image_url"));
                    product.put("commands", resultSet.getString("commands").split("\n"));
                    product.put("created_at", resultSet.getTimestamp("created_at").toString());
                    product.put("active", resultSet.getBoolean("active"));
                    products.put(product);
                } catch (SQLException e) {
                    // Ignore if there's an error getting a product
                }
            }

            resultSet.close();
            statement.close();

            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("products", products);
            
            sendResponse(exchange, 200, response.toString());
        } catch (SQLException e) {
            Logger.severe("Database error while getting products by category: " + e.getMessage());
            e.printStackTrace();
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Database error")
                    .toString();
            sendResponse(exchange, 500, response);
        }
    }

    /**
     * Handle GET request to fetch a single product
     * @param exchange The HTTP exchange
     * @throws IOException If an I/O error occurs
     */
    private void handleGetProduct(HttpExchange exchange) throws IOException {
        // Get product id from path
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
            int productId = Integer.parseInt(parts[3]);
            
            Connection connection = plugin.getDatabaseManager().getConnection();
            String query = "SELECT p.*, c.name as category_name FROM products p " +
                          "JOIN categories c ON p.category_id = c.id " +
                          "WHERE p.id = ?";
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setInt(1, productId);
            ResultSet resultSet = statement.executeQuery();

            if (!resultSet.next()) {
                resultSet.close();
                statement.close();
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Product not found")
                        .toString();
                sendResponse(exchange, 404, response);
                return;
            }
            
            JSONObject product = new JSONObject();
            product.put("id", resultSet.getInt("id"));
            product.put("name", resultSet.getString("name"));
            product.put("description", resultSet.getString("description"));
            product.put("price", resultSet.getDouble("price"));
            product.put("sale_price", resultSet.getDouble("sale_price"));
            product.put("is_on_sale", resultSet.getBoolean("is_on_sale"));
            product.put("category_id", resultSet.getInt("category_id"));
            product.put("category_name", resultSet.getString("category_name"));
            product.put("image_url", resultSet.getString("image_url"));
            product.put("display_order", resultSet.getInt("display_order"));
            product.put("enabled", resultSet.getBoolean("active"));
            product.put("commands", new JSONArray(resultSet.getString("commands")));
            
            resultSet.close();
            statement.close();

            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("product", product);
            
            sendResponse(exchange, 200, response.toString());
        } catch (NumberFormatException e) {
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Invalid product ID")
                    .toString();
            sendResponse(exchange, 400, response);
        } catch (SQLException e) {
            Logger.severe("Database error while getting product: " + e.getMessage());
            e.printStackTrace();
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Database error")
                    .toString();
            sendResponse(exchange, 500, response);
        }
    }

    /**
     * Handle POST request to create a new product
     * @param exchange The HTTP exchange
     * @throws IOException If an I/O error occurs
     */
    private void handleCreateProduct(HttpExchange exchange) throws IOException {
        // Read request body
        String requestBody = new BufferedReader(new InputStreamReader(exchange.getRequestBody()))
                .lines().collect(Collectors.joining("\n"));
        JSONObject requestJson = new JSONObject(requestBody);

        try {
            // Validate required fields
            if (!requestJson.has("name") || !requestJson.has("price") || !requestJson.has("category_id") || !requestJson.has("commands")) {
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Missing required fields")
                        .toString();
                sendResponse(exchange, 400, response);
                return;
            }

            String name = requestJson.getString("name");
            String description = requestJson.optString("description", "");
            double price = requestJson.getDouble("price");
            double salePrice = requestJson.optDouble("sale_price", 0.0);
            boolean isOnSale = requestJson.optBoolean("is_on_sale", false);
            int categoryId = requestJson.getInt("category_id");
            String imageUrl = requestJson.optString("image_url", "");
            int displayOrder = requestJson.optInt("display_order", 0);
            boolean enabled = requestJson.optBoolean("active", true);
            JSONArray commands = requestJson.getJSONArray("commands");

            // Verify category exists
            Connection connection = plugin.getDatabaseManager().getConnection();
            String categoryQuery = "SELECT * FROM categories WHERE id = ?";
            PreparedStatement categoryStatement = connection.prepareStatement(categoryQuery);
            categoryStatement.setInt(1, categoryId);
            ResultSet categoryResultSet = categoryStatement.executeQuery();
            
            if (!categoryResultSet.next()) {
                categoryResultSet.close();
                categoryStatement.close();
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Category not found")
                        .toString();
                sendResponse(exchange, 404, response);
                return;
            }
            
            categoryResultSet.close();
            categoryStatement.close();

            // Create product record
            String insertQuery = "INSERT INTO products (name, description, price, sale_price, is_on_sale, " +
                                "category_id, image_url, display_order, active, commands) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement insertStatement = connection.prepareStatement(insertQuery, PreparedStatement.RETURN_GENERATED_KEYS);
            insertStatement.setString(1, name);
            insertStatement.setString(2, description);
            insertStatement.setDouble(3, price);
            insertStatement.setDouble(4, salePrice);
            insertStatement.setBoolean(5, isOnSale);
            insertStatement.setInt(6, categoryId);
            insertStatement.setString(7, imageUrl);
            insertStatement.setInt(8, displayOrder);
            insertStatement.setBoolean(9, enabled);
            insertStatement.setString(10, commands.toString());
            insertStatement.executeUpdate();
            
            ResultSet generatedKeys = insertStatement.getGeneratedKeys();
            int productId = -1;
            if (generatedKeys.next()) {
                productId = generatedKeys.getInt(1);
            }
            generatedKeys.close();
            insertStatement.close();

            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("message", "Product created successfully");
            response.put("product_id", productId);
            
            sendResponse(exchange, 201, response.toString());
        } catch (SQLException e) {
            Logger.severe("Database error while creating product: " + e.getMessage());
            e.printStackTrace();
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Database error")
                    .toString();
            sendResponse(exchange, 500, response);
        }
    }

    /**
     * Handle PUT request to update a product
     * @param exchange The HTTP exchange
     * @throws IOException If an I/O error occurs
     */
    private void handleUpdateProduct(HttpExchange exchange) throws IOException {
        // Read request body
        String requestBody = new BufferedReader(new InputStreamReader(exchange.getRequestBody()))
                .lines().collect(Collectors.joining("\n"));
        JSONObject requestJson = new JSONObject(requestBody);

        try {
            // Validate required fields
            if (!requestJson.has("id")) {
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Missing product ID")
                        .toString();
                sendResponse(exchange, 400, response);
                return;
            }

            int id = requestJson.getInt("id");
            
            // Build update query dynamically based on provided fields
            StringBuilder queryBuilder = new StringBuilder("UPDATE products SET ");
            boolean hasUpdates = false;
            
            if (requestJson.has("name")) {
                queryBuilder.append("name = ?, ");
                hasUpdates = true;
            }
            
            if (requestJson.has("description")) {
                queryBuilder.append("description = ?, ");
                hasUpdates = true;
            }
            
            if (requestJson.has("price")) {
                queryBuilder.append("price = ?, ");
                hasUpdates = true;
            }
            
            if (requestJson.has("sale_price")) {
                queryBuilder.append("sale_price = ?, ");
                hasUpdates = true;
            }
            
            if (requestJson.has("is_on_sale")) {
                queryBuilder.append("is_on_sale = ?, ");
                hasUpdates = true;
            }
            
            if (requestJson.has("category_id")) {
                queryBuilder.append("category_id = ?, ");
                hasUpdates = true;
            }
            
            if (requestJson.has("image_url")) {
                queryBuilder.append("image_url = ?, ");
                hasUpdates = true;
            }
            
            if (requestJson.has("display_order")) {
                queryBuilder.append("display_order = ?, ");
                hasUpdates = true;
            }
            
            if (requestJson.has("active")) {
                queryBuilder.append("active = ?, ");
                hasUpdates = true;
            }
            
            if (requestJson.has("commands")) {
                queryBuilder.append("commands = ?, ");
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
            
            if (requestJson.has("name")) {
                statement.setString(paramIndex++, requestJson.getString("name"));
            }
            
            if (requestJson.has("description")) {
                statement.setString(paramIndex++, requestJson.getString("description"));
            }
            
            if (requestJson.has("price")) {
                statement.setDouble(paramIndex++, requestJson.getDouble("price"));
            }
            
            if (requestJson.has("sale_price")) {
                statement.setDouble(paramIndex++, requestJson.getDouble("sale_price"));
            }
            
            if (requestJson.has("is_on_sale")) {
                statement.setBoolean(paramIndex++, requestJson.getBoolean("is_on_sale"));
            }
            
            if (requestJson.has("category_id")) {
                int categoryId = requestJson.getInt("category_id");
                
                // Verify category exists
                String categoryQuery = "SELECT * FROM categories WHERE id = ?";
                PreparedStatement categoryStatement = connection.prepareStatement(categoryQuery);
                categoryStatement.setInt(1, categoryId);
                ResultSet categoryResultSet = categoryStatement.executeQuery();
                
                if (!categoryResultSet.next()) {
                    categoryResultSet.close();
                    categoryStatement.close();
                    statement.close();
                    String response = new JSONObject()
                            .put("success", false)
                            .put("error", "Category not found")
                            .toString();
                    sendResponse(exchange, 404, response);
                    return;
                }
                
                categoryResultSet.close();
                categoryStatement.close();
                
                statement.setInt(paramIndex++, categoryId);
            }
            
            if (requestJson.has("image_url")) {
                statement.setString(paramIndex++, requestJson.getString("image_url"));
            }
            
            if (requestJson.has("display_order")) {
                statement.setInt(paramIndex++, requestJson.getInt("display_order"));
            }
            
            if (requestJson.has("active")) {
                statement.setBoolean(paramIndex++, requestJson.getBoolean("active"));
            }
            
            if (requestJson.has("commands")) {
                statement.setString(paramIndex++, requestJson.getJSONArray("commands").toString());
            }
            
            statement.setInt(paramIndex, id);
            int rowsAffected = statement.executeUpdate();
            statement.close();
            
            if (rowsAffected == 0) {
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Product not found")
                        .toString();
                sendResponse(exchange, 404, response);
                return;
            }

            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("message", "Product updated successfully");
            
            sendResponse(exchange, 200, response.toString());
        } catch (SQLException e) {
            Logger.severe("Database error while updating product: " + e.getMessage());
            e.printStackTrace();
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Database error")
                    .toString();
            sendResponse(exchange, 500, response);
        }
    }

    /**
     * Handle DELETE request to delete a product
     * @param exchange The HTTP exchange
     * @throws IOException If an I/O error occurs
     */
    private void handleDeleteProduct(HttpExchange exchange) throws IOException {
        // Get product id from path
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
            
            // Check if product has purchases
            String purchaseQuery = "SELECT COUNT(*) FROM purchases WHERE product_id = ?";
            PreparedStatement purchaseStatement = connection.prepareStatement(purchaseQuery);
            purchaseStatement.setInt(1, id);
            ResultSet purchaseResultSet = purchaseStatement.executeQuery();
            
            if (purchaseResultSet.next() && purchaseResultSet.getInt(1) > 0) {
                purchaseResultSet.close();
                purchaseStatement.close();
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Cannot delete product with existing purchases")
                        .toString();
                sendResponse(exchange, 400, response);
                return;
            }
            
            purchaseResultSet.close();
            purchaseStatement.close();
            
            // Delete product
            String query = "DELETE FROM products WHERE id = ?";
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setInt(1, id);
            int rowsAffected = statement.executeUpdate();
            statement.close();
            
            if (rowsAffected == 0) {
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Product not found")
                        .toString();
                sendResponse(exchange, 404, response);
                return;
            }

            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("message", "Product deleted successfully");
            
            sendResponse(exchange, 200, response.toString());
        } catch (NumberFormatException e) {
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Invalid product ID")
                    .toString();
            sendResponse(exchange, 400, response);
        } catch (SQLException e) {
            Logger.severe("Database error while deleting product: " + e.getMessage());
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