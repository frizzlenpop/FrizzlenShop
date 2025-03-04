package org.frizzlenpop.frizzlenStore.payment.gateways;

import org.frizzlenpop.frizzlenStore.payment.PaymentSession;

/**
 * Interface for payment gateways
 */
public interface PaymentGateway {
    
    /**
     * Initialize the payment gateway
     * @return True if successful
     */
    boolean initialize();
    
    /**
     * Create a payment session
     * @param playerName The player name
     * @param amount The amount to charge
     * @param description The payment description
     * @return The payment session
     */
    PaymentSession createPaymentSession(String playerName, double amount, String description);
    
    /**
     * Verify a payment
     * @param sessionId The session ID
     * @return True if payment is verified
     */
    boolean verifyPayment(String sessionId);
    
    /**
     * Get the gateway name
     * @return The gateway name
     */
    String getGatewayName();
    
    /**
     * Get the gateway display name
     * @return The gateway display name
     */
    String getDisplayName();
    
    /**
     * Get if the gateway is enabled
     * @return True if enabled
     */
    boolean isEnabled();
} 