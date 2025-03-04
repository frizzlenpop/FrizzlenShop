package org.frizzlenpop.frizzlenStore.purchase;

import java.util.UUID;

/**
 * Represents a store purchase
 */
public class Purchase {
    private final int id;
    private final String transactionId;
    private final String playerName;
    private final UUID playerUuid;
    private final int productId;
    private final double pricePaid;
    private final String paymentMethod;
    private final String paymentStatus;
    private final String commands;
    
    /**
     * Create a new purchase
     * @param id The purchase ID
     * @param transactionId The transaction ID
     * @param playerName The player name
     * @param playerUuid The player UUID
     * @param productId The product ID
     * @param pricePaid The price paid
     * @param paymentMethod The payment method
     * @param paymentStatus The payment status
     * @param commands The commands to execute
     */
    public Purchase(int id, String transactionId, String playerName, UUID playerUuid, int productId, 
                   double pricePaid, String paymentMethod, String paymentStatus, String commands) {
        this.id = id;
        this.transactionId = transactionId;
        this.playerName = playerName;
        this.playerUuid = playerUuid;
        this.productId = productId;
        this.pricePaid = pricePaid;
        this.paymentMethod = paymentMethod;
        this.paymentStatus = paymentStatus;
        this.commands = commands;
    }
    
    /**
     * Get the purchase ID
     * @return The purchase ID
     */
    public int getId() {
        return id;
    }
    
    /**
     * Get the transaction ID
     * @return The transaction ID
     */
    public String getTransactionId() {
        return transactionId;
    }
    
    /**
     * Get the player name
     * @return The player name
     */
    public String getPlayerName() {
        return playerName;
    }
    
    /**
     * Get the player UUID
     * @return The player UUID
     */
    public UUID getPlayerUuid() {
        return playerUuid;
    }
    
    /**
     * Get the product ID
     * @return The product ID
     */
    public int getProductId() {
        return productId;
    }
    
    /**
     * Get the price paid
     * @return The price paid
     */
    public double getPricePaid() {
        return pricePaid;
    }
    
    /**
     * Get the payment method
     * @return The payment method
     */
    public String getPaymentMethod() {
        return paymentMethod;
    }
    
    /**
     * Get the payment status
     * @return The payment status
     */
    public String getPaymentStatus() {
        return paymentStatus;
    }
    
    /**
     * Get the commands to execute
     * @return The commands
     */
    public String getCommands() {
        return commands;
    }
} 