package org.frizzlenpop.frizzlenStore.payment;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stores information about a payment session
 */
public class PaymentSession {
    private final String sessionId;
    private final String playerName;
    private final double amount;
    private final String description;
    private final String gatewayName;
    private final long createdAt;
    private final Map<String, String> metadata;
    
    private String paymentUrl;
    private PaymentStatus status;
    
    /**
     * Create a new payment session
     * @param playerName The player name
     * @param amount The payment amount
     * @param description The payment description
     * @param gatewayName The gateway name
     */
    public PaymentSession(String playerName, double amount, String description, String gatewayName) {
        this.sessionId = UUID.randomUUID().toString();
        this.playerName = playerName;
        this.amount = amount;
        this.description = description;
        this.gatewayName = gatewayName;
        this.createdAt = System.currentTimeMillis();
        this.status = PaymentStatus.PENDING;
        this.metadata = new HashMap<>();
    }
    
    /**
     * Get the session ID
     * @return The session ID
     */
    public String getSessionId() {
        return sessionId;
    }
    
    /**
     * Get the player name
     * @return The player name
     */
    public String getPlayerName() {
        return playerName;
    }
    
    /**
     * Get the payment amount
     * @return The payment amount
     */
    public double getAmount() {
        return amount;
    }
    
    /**
     * Get the payment description
     * @return The payment description
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Get the gateway name
     * @return The gateway name
     */
    public String getGatewayName() {
        return gatewayName;
    }
    
    /**
     * Get the creation timestamp
     * @return The creation timestamp
     */
    public long getCreatedAt() {
        return createdAt;
    }
    
    /**
     * Get the payment URL
     * @return The payment URL
     */
    public String getPaymentUrl() {
        return paymentUrl;
    }
    
    /**
     * Set the payment URL
     * @param paymentUrl The payment URL
     */
    public void setPaymentUrl(String paymentUrl) {
        this.paymentUrl = paymentUrl;
    }
    
    /**
     * Get the payment status
     * @return The payment status
     */
    public PaymentStatus getStatus() {
        return status;
    }
    
    /**
     * Set the payment status
     * @param status The payment status
     */
    public void setStatus(PaymentStatus status) {
        this.status = status;
    }
    
    /**
     * Add metadata to the payment session
     * @param key The metadata key
     * @param value The metadata value
     */
    public void addMetadata(String key, String value) {
        metadata.put(key, value);
    }
    
    /**
     * Get metadata value
     * @param key The metadata key
     * @return The metadata value
     */
    public String getMetadata(String key) {
        return metadata.get(key);
    }
    
    /**
     * Get all metadata
     * @return The metadata map
     */
    public Map<String, String> getAllMetadata() {
        return new HashMap<>(metadata);
    }
    
    /**
     * Check if the payment session has expired
     * @param expirationTimeMs The expiration time in milliseconds
     * @return True if expired
     */
    public boolean isExpired(long expirationTimeMs) {
        return System.currentTimeMillis() - createdAt > expirationTimeMs;
    }
    
    /**
     * Payment status enum
     */
    public enum PaymentStatus {
        PENDING,
        COMPLETED,
        FAILED,
        REFUNDED,
        CANCELLED
    }
} 