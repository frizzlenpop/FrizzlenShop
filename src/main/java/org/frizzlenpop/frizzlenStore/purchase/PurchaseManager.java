package org.frizzlenpop.frizzlenStore.purchase;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.frizzlenpop.frizzlenStore.FrizzlenStore;
import org.frizzlenpop.frizzlenStore.util.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages product purchases and delivery
 */
public class PurchaseManager {
    private final FrizzlenStore plugin;
    
    // Cache of pending purchases (player name -> list of purchases)
    private final Map<String, List<Purchase>> pendingPurchases;
    
    // SQL queries
    private static final String GET_PENDING_PURCHASES = 
            "SELECT p.id, p.transaction_id, p.player_name, p.player_uuid, " +
            "p.product_id, p.price_paid, p.payment_method, pr.commands, p.payment_status " +
            "FROM purchases p " +
            "JOIN products pr ON p.product_id = pr.id " +
            "WHERE p.player_name = ? AND p.delivered = 0";
    
    private static final String GET_PENDING_PURCHASES_COUNT = 
            "SELECT COUNT(*) FROM purchases WHERE delivered = 0";
    
    private static final String MARK_PURCHASE_DELIVERED = 
            "UPDATE purchases SET delivered = 1, delivery_time = CURRENT_TIMESTAMP " +
            "WHERE id = ?";
    
    /**
     * Create a new purchase manager
     * @param plugin The plugin instance
     */
    public PurchaseManager(FrizzlenStore plugin) {
        this.plugin = plugin;
        this.pendingPurchases = new ConcurrentHashMap<>();
        
        // Load pending purchases from database
        loadPendingPurchases();
    }
    
    /**
     * Load pending purchases from database
     */
    private void loadPendingPurchases() {
        try {
            PreparedStatement statement = plugin.getDatabaseManager().prepareStatement(
                    "SELECT DISTINCT player_name FROM purchases WHERE delivered = 0");
            
            ResultSet resultSet = statement.executeQuery();
            
            while (resultSet.next()) {
                String playerName = resultSet.getString("player_name");
                List<Purchase> purchases = getPendingPurchasesForPlayer(playerName);
                
                if (!purchases.isEmpty()) {
                    pendingPurchases.put(playerName.toLowerCase(), purchases);
                }
            }
            
            resultSet.close();
            statement.close();
            
            Logger.info("Loaded pending purchases for " + pendingPurchases.size() + " players");
        } catch (SQLException e) {
            Logger.severe("Failed to load pending purchases: " + e.getMessage());
        }
    }
    
    /**
     * Get pending purchases for a player
     * @param playerName The player name
     * @return List of pending purchases
     */
    private List<Purchase> getPendingPurchasesForPlayer(String playerName) {
        List<Purchase> purchases = new ArrayList<>();
        
        try {
            PreparedStatement statement = plugin.getDatabaseManager().prepareStatement(GET_PENDING_PURCHASES);
            statement.setString(1, playerName);
            
            ResultSet resultSet = statement.executeQuery();
            
            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                String transactionId = resultSet.getString("transaction_id");
                String name = resultSet.getString("player_name");
                String uuidStr = resultSet.getString("player_uuid");
                UUID uuid = uuidStr != null ? UUID.fromString(uuidStr) : null;
                int productId = resultSet.getInt("product_id");
                double pricePaid = resultSet.getDouble("price_paid");
                String paymentMethod = resultSet.getString("payment_method");
                String commands = resultSet.getString("commands");
                String paymentStatus = resultSet.getString("payment_status");
                
                Purchase purchase = new Purchase(id, transactionId, name, uuid, productId, 
                                                 pricePaid, paymentMethod, paymentStatus, commands);
                purchases.add(purchase);
            }
            
            resultSet.close();
            statement.close();
        } catch (SQLException e) {
            Logger.severe("Failed to get pending purchases for player " + playerName + ": " + e.getMessage());
        }
        
        return purchases;
    }
    
    /**
     * Get the number of pending purchases
     * @return The number of pending purchases
     */
    public int getPendingPurchasesCount() {
        try {
            PreparedStatement statement = plugin.getDatabaseManager().prepareStatement(GET_PENDING_PURCHASES_COUNT);
            ResultSet resultSet = statement.executeQuery();
            
            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
            
            resultSet.close();
            statement.close();
        } catch (SQLException e) {
            Logger.severe("Failed to get pending purchases count: " + e.getMessage());
        }
        
        return 0;
    }
    
    /**
     * Deliver pending purchases to a player
     * @param player The player to deliver to
     */
    public void deliverPendingPurchases(Player player) {
        String playerName = player.getName().toLowerCase();
        
        // Check if player has pending purchases
        if (!pendingPurchases.containsKey(playerName)) {
            return;
        }
        
        List<Purchase> purchases = pendingPurchases.get(playerName);
        List<Purchase> deliveredPurchases = new ArrayList<>();
        
        for (Purchase purchase : purchases) {
            // Attempt to deliver the purchase
            if (deliverPurchase(player, purchase)) {
                deliveredPurchases.add(purchase);
            }
        }
        
        // Remove delivered purchases from cache
        purchases.removeAll(deliveredPurchases);
        
        // If no more pending purchases, remove from map
        if (purchases.isEmpty()) {
            pendingPurchases.remove(playerName);
        }
    }
    
    /**
     * Deliver a purchase to a player
     * @param player The player to deliver to
     * @param purchase The purchase to deliver
     * @return True if delivery was successful
     */
    public boolean deliverPurchase(Player player, Purchase purchase) {
        try {
            // Mark as delivered in the database first
            PreparedStatement statement = plugin.getDatabaseManager().prepareStatement(MARK_PURCHASE_DELIVERED);
            statement.setInt(1, purchase.getId());
            statement.executeUpdate();
            statement.close();
            
            // Execute commands
            String[] commands = purchase.getCommands().split("\\n");
            
            for (String command : commands) {
                // Replace placeholders
                command = command.replace("%player%", player.getName());
                
                // Execute command
                Logger.debug("Executing command: " + command);
                boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                
                if (!success) {
                    Logger.warning("Failed to execute command: " + command);
                }
            }
            
            // Send confirmation message to player
            player.sendMessage("§6[FrizzlenStore] §aYour purchase has been delivered! Enjoy!");
            
            return true;
        } catch (SQLException e) {
            Logger.severe("Failed to deliver purchase " + purchase.getTransactionId() + 
                          " to player " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Deliver a purchase by purchase ID
     * @param purchaseId The purchase ID to deliver
     * @return True if delivery was successful
     */
    public boolean deliverPurchase(int purchaseId) {
        try {
            // Get the purchase from the database
            PreparedStatement getStatement = plugin.getDatabaseManager().prepareStatement(
                    "SELECT p.id, p.transaction_id, p.player_name, p.player_uuid, " +
                    "p.product_id, p.price_paid, p.payment_method, pr.commands, p.payment_status " +
                    "FROM purchases p " +
                    "JOIN products pr ON p.product_id = pr.id " +
                    "WHERE p.id = ?");
            getStatement.setInt(1, purchaseId);
            
            ResultSet resultSet = getStatement.executeQuery();
            
            if (resultSet.next()) {
                String transactionId = resultSet.getString("transaction_id");
                String playerName = resultSet.getString("player_name");
                String uuidStr = resultSet.getString("player_uuid");
                UUID playerUuid = uuidStr != null ? UUID.fromString(uuidStr) : null;
                int productId = resultSet.getInt("product_id");
                double pricePaid = resultSet.getDouble("price_paid");
                String paymentMethod = resultSet.getString("payment_method");
                String commands = resultSet.getString("commands");
                String paymentStatus = resultSet.getString("payment_status");
                
                resultSet.close();
                getStatement.close();
                
                // Create purchase object
                Purchase purchase = new Purchase(purchaseId, transactionId, playerName, playerUuid, 
                                               productId, pricePaid, paymentMethod, paymentStatus, commands);
                
                // If player is online, deliver immediately
                Player player = playerUuid != null ? Bukkit.getPlayer(playerUuid) : Bukkit.getPlayerExact(playerName);
                
                if (player != null && player.isOnline()) {
                    return deliverPurchase(player, purchase);
                } else {
                    // Mark as still pending in the database
                    PreparedStatement markStatement = plugin.getDatabaseManager().prepareStatement(
                            "UPDATE purchases SET delivery_attempted = delivery_attempted + 1 " +
                            "WHERE id = ?");
                    markStatement.setInt(1, purchaseId);
                    markStatement.executeUpdate();
                    markStatement.close();
                    
                    // Add to pending purchases cache
                    if (!pendingPurchases.containsKey(playerName.toLowerCase())) {
                        pendingPurchases.put(playerName.toLowerCase(), new ArrayList<>());
                    }
                    
                    pendingPurchases.get(playerName.toLowerCase()).add(purchase);
                    
                    // Schedule delivery for when player logs in
                    return false;
                }
            } else {
                resultSet.close();
                getStatement.close();
                Logger.warning("Could not find purchase with ID " + purchaseId);
                return false;
            }
        } catch (SQLException e) {
            Logger.severe("Failed to deliver purchase " + purchaseId + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Create a new purchase record
     * @param playerName The player name
     * @param productId The product ID
     * @param pricePaid The price paid
     * @param paymentMethod The payment method
     * @param paymentStatus The payment status
     * @param transactionId The transaction ID
     * @return True if successful
     */
    public boolean createPurchase(String playerName, int productId, double pricePaid, 
                                 String paymentMethod, String paymentStatus, String transactionId) {
        try {
            // Get player UUID if online
            Player player = Bukkit.getPlayerExact(playerName);
            String uuidStr = player != null ? player.getUniqueId().toString() : null;
            String ipAddress = player != null ? player.getAddress().getAddress().getHostAddress() : null;
            
            // Create SQL statement
            String sql = "INSERT INTO purchases (transaction_id, player_name, player_uuid, " +
                         "product_id, price_paid, payment_method, payment_status, ip_address) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            
            PreparedStatement statement = plugin.getDatabaseManager().prepareStatement(sql);
            statement.setString(1, transactionId);
            statement.setString(2, playerName);
            statement.setString(3, uuidStr);
            statement.setInt(4, productId);
            statement.setDouble(5, pricePaid);
            statement.setString(6, paymentMethod);
            statement.setString(7, paymentStatus);
            statement.setString(8, ipAddress);
            
            int result = statement.executeUpdate();
            statement.close();
            
            if (result > 0) {
                // Get product commands
                String getProductSql = "SELECT commands FROM products WHERE id = ?";
                PreparedStatement productStatement = plugin.getDatabaseManager().prepareStatement(getProductSql);
                productStatement.setInt(1, productId);
                
                ResultSet resultSet = productStatement.executeQuery();
                String commands = null;
                
                if (resultSet.next()) {
                    commands = resultSet.getString("commands");
                }
                
                resultSet.close();
                productStatement.close();
                
                // If player is online, deliver immediately
                if (player != null && commands != null) {
                    // Get the purchase ID
                    PreparedStatement idStatement = plugin.getDatabaseManager().prepareStatement(
                            "SELECT id FROM purchases WHERE transaction_id = ?");
                    idStatement.setString(1, transactionId);
                    
                    ResultSet idResult = idStatement.executeQuery();
                    if (idResult.next()) {
                        int purchaseId = idResult.getInt("id");
                        
                        // Create purchase object and deliver
                        Purchase purchase = new Purchase(purchaseId, transactionId, playerName, 
                                                        player.getUniqueId(), productId, pricePaid, 
                                                        paymentMethod, paymentStatus, commands);
                        
                        deliverPurchase(player, purchase);
                    }
                    
                    idResult.close();
                    idStatement.close();
                } else if (commands != null) {
                    // Add to pending purchases
                    // Get the purchase ID
                    PreparedStatement idStatement = plugin.getDatabaseManager().prepareStatement(
                            "SELECT id FROM purchases WHERE transaction_id = ?");
                    idStatement.setString(1, transactionId);
                    
                    ResultSet idResult = idStatement.executeQuery();
                    if (idResult.next()) {
                        int purchaseId = idResult.getInt("id");
                        
                        // Create purchase object
                        Purchase purchase = new Purchase(purchaseId, transactionId, playerName, 
                                                        null, productId, pricePaid, 
                                                        paymentMethod, paymentStatus, commands);
                        
                        // Add to pending purchases
                        if (!pendingPurchases.containsKey(playerName.toLowerCase())) {
                            pendingPurchases.put(playerName.toLowerCase(), new ArrayList<>());
                        }
                        
                        pendingPurchases.get(playerName.toLowerCase()).add(purchase);
                    }
                    
                    idResult.close();
                    idStatement.close();
                }
                
                return true;
            }
            
            return false;
        } catch (SQLException e) {
            Logger.severe("Failed to create purchase: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Create a new purchase record from a payment
     * @param playerUuid The player UUID
     * @param productId The product ID
     * @param paymentId The payment ID
     * @return The purchase ID, or -1 if failed
     */
    public int createPurchase(String playerUuid, int productId, int paymentId) {
        try {
            // Get payment details
            PreparedStatement paymentStatement = plugin.getDatabaseManager().prepareStatement(
                    "SELECT amount, gateway FROM payments WHERE id = ?");
            paymentStatement.setInt(1, paymentId);
            ResultSet paymentResult = paymentStatement.executeQuery();
            
            if (!paymentResult.next()) {
                paymentResult.close();
                paymentStatement.close();
                Logger.warning("Payment not found: " + paymentId);
                return -1;
            }
            
            double amount = paymentResult.getDouble("amount");
            String gateway = paymentResult.getString("gateway");
            paymentResult.close();
            paymentStatement.close();
            
            // Get player name from UUID
            String playerName = null;
            PreparedStatement playerStatement = plugin.getDatabaseManager().prepareStatement(
                    "SELECT name FROM players WHERE uuid = ?");
            playerStatement.setString(1, playerUuid);
            ResultSet playerResult = playerStatement.executeQuery();
            
            if (playerResult.next()) {
                playerName = playerResult.getString("name");
            }
            
            playerResult.close();
            playerStatement.close();
            
            if (playerName == null) {
                // Try to get from Bukkit API
                Player player = Bukkit.getPlayer(UUID.fromString(playerUuid));
                if (player != null) {
                    playerName = player.getName();
                } else {
                    Logger.warning("Could not find player name for UUID: " + playerUuid);
                    playerName = "Unknown"; // Fallback
                }
            }
            
            // Get product details
            PreparedStatement productStatement = plugin.getDatabaseManager().prepareStatement(
                    "SELECT name, commands FROM products WHERE id = ?");
            productStatement.setInt(1, productId);
            ResultSet productResult = productStatement.executeQuery();
            
            if (!productResult.next()) {
                productResult.close();
                productStatement.close();
                Logger.warning("Product not found: " + productId);
                return -1;
            }
            
            String productName = productResult.getString("name");
            String commands = productResult.getString("commands");
            productResult.close();
            productStatement.close();
            
            // Create transaction ID
            String transactionId = "PAY-" + paymentId;
            
            // Call the other createPurchase method
            boolean success = createPurchase(playerName, productId, amount, gateway, "completed", transactionId);
            
            if (success) {
                // Get the purchase ID
                PreparedStatement idStatement = plugin.getDatabaseManager().prepareStatement(
                        "SELECT id FROM purchases WHERE transaction_id = ?");
                idStatement.setString(1, transactionId);
                
                ResultSet idResult = idStatement.executeQuery();
                if (idResult.next()) {
                    int purchaseId = idResult.getInt("id");
                    idResult.close();
                    idStatement.close();
                    return purchaseId;
                }
                
                idResult.close();
                idStatement.close();
            }
            
            return -1;
        } catch (SQLException e) {
            Logger.severe("Failed to create purchase from payment: " + e.getMessage());
            return -1;
        }
    }
} 