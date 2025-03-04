package org.frizzlenpop.frizzlenStore.payment;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.frizzlenpop.frizzlenStore.FrizzlenStore;
import org.frizzlenpop.frizzlenStore.payment.gateways.CryptoGateway;
import org.frizzlenpop.frizzlenStore.payment.gateways.PayPalGateway;
import org.frizzlenpop.frizzlenStore.payment.gateways.PaymentGateway;
import org.frizzlenpop.frizzlenStore.payment.gateways.StripeGateway;
import org.frizzlenpop.frizzlenStore.util.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages payment gateways and processing
 */
public class PaymentManager {
    private final FrizzlenStore plugin;
    private final Map<String, PaymentGateway> gateways;
    
    /**
     * Create a new payment manager
     * @param plugin The plugin instance
     */
    public PaymentManager(FrizzlenStore plugin) {
        this.plugin = plugin;
        this.gateways = new HashMap<>();
        loadPaymentGateways();
    }
    
    /**
     * Load payment gateways from config
     */
    private void loadPaymentGateways() {
        FileConfiguration config = plugin.getConfigManager().getCustomConfig("payment-gateways.yml");
        
        // Load PayPal if enabled
        if (config.getBoolean("paypal.enabled", false)) {
            ConfigurationSection paypalConfig = config.getConfigurationSection("paypal");
            if (paypalConfig != null) {
                PaymentGateway paypal = new PayPalGateway(plugin, paypalConfig);
                if (paypal.initialize()) {
                    gateways.put("paypal", paypal);
                    Logger.info("PayPal gateway initialized");
                } else {
                    Logger.warning("Failed to initialize PayPal gateway");
                }
            }
        }
        
        // Load Stripe if enabled
        if (config.getBoolean("stripe.enabled", false)) {
            ConfigurationSection stripeConfig = config.getConfigurationSection("stripe");
            if (stripeConfig != null) {
                PaymentGateway stripe = new StripeGateway(plugin, stripeConfig);
                if (stripe.initialize()) {
                    gateways.put("stripe", stripe);
                    Logger.info("Stripe gateway initialized");
                } else {
                    Logger.warning("Failed to initialize Stripe gateway");
                }
            }
        }
        
        // Load Crypto if enabled
        if (config.getBoolean("crypto.enabled", false)) {
            ConfigurationSection cryptoConfig = config.getConfigurationSection("crypto");
            if (cryptoConfig != null) {
                PaymentGateway crypto = new CryptoGateway(plugin, cryptoConfig);
                if (crypto.initialize()) {
                    gateways.put("crypto", crypto);
                    Logger.info("Crypto gateway initialized");
                } else {
                    Logger.warning("Failed to initialize Crypto gateway");
                }
            }
        }
        
        Logger.info("Initialized " + gateways.size() + " payment gateways");
    }
    
    /**
     * Get a payment gateway by name
     * @param gatewayName The gateway name
     * @return The payment gateway or null if not found
     */
    public PaymentGateway getGateway(String gatewayName) {
        return gateways.getOrDefault(gatewayName.toLowerCase(), null);
    }
    
    /**
     * Get all enabled payment gateways
     * @return Map of gateway names to gateways
     */
    public Map<String, PaymentGateway> getGateways() {
        return gateways;
    }
    
    /**
     * Create a new payment session
     * @param playerName The player name
     * @param amount The payment amount
     * @param description The payment description
     * @param gatewayName The gateway name
     * @return The payment session or null if failed
     */
    public PaymentSession createPaymentSession(String playerName, double amount, String description, String gatewayName) {
        PaymentGateway gateway = getGateway(gatewayName);
        
        if (gateway == null) {
            Logger.warning("Attempted to create payment session with unknown gateway: " + gatewayName);
            return null;
        }
        
        return gateway.createPaymentSession(playerName, amount, description);
    }
    
    /**
     * Verify a payment
     * @param sessionId The payment session ID
     * @param gatewayName The gateway name
     * @return True if payment is valid
     */
    public boolean verifyPayment(String sessionId, String gatewayName) {
        PaymentGateway gateway = getGateway(gatewayName);
        
        if (gateway == null) {
            Logger.warning("Attempted to verify payment with unknown gateway: " + gatewayName);
            return false;
        }
        
        return gateway.verifyPayment(sessionId);
    }
} 