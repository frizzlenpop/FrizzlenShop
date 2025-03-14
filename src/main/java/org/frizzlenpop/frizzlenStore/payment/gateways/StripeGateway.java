package org.frizzlenpop.frizzlenStore.payment.gateways;

import org.bukkit.configuration.ConfigurationSection;
import org.frizzlenpop.frizzlenStore.FrizzlenStore;
import org.frizzlenpop.frizzlenStore.payment.PaymentSession;
import org.frizzlenpop.frizzlenStore.util.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stripe payment gateway implementation
 */
public class StripeGateway implements PaymentGateway {
    private final FrizzlenStore plugin;
    private final ConfigurationSection config;
    
    private String apiKey;
    private String webhookSecret;
    private boolean testMode;
    private boolean enabled;
    
    /**
     * Create a new Stripe gateway
     * @param plugin The plugin instance
     * @param config The configuration section
     */
    public StripeGateway(FrizzlenStore plugin, ConfigurationSection config) {
        this.plugin = plugin;
        this.config = config;
    }
    
    @Override
    public boolean initialize() {
        try {
            this.apiKey = config.getString("api_key", "");
            this.webhookSecret = config.getString("webhook_secret", "");
            this.testMode = config.getBoolean("test_mode", true);
            this.enabled = config.getBoolean("enabled", false);
            
            // Validate configuration
            if (apiKey.isEmpty()) {
                Logger.warning("Stripe gateway missing api_key");
                return false;
            }
            
            // For a real implementation, we would test the API key here
            
            return true;
        } catch (Exception e) {
            Logger.severe("Failed to initialize Stripe gateway: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public PaymentSession createPaymentSession(String playerName, double amount, String description) {
        try {
            // Create a payment session
            PaymentSession session = new PaymentSession(playerName, amount, description, getGatewayName());
            
            // In a real implementation, we would create a Stripe Checkout Session here
            // For this demo, we'll just create a dummy session ID
            String checkoutSessionId = "cs_test_" + UUID.randomUUID().toString().replace("-", "");
            
            // Store the checkout session ID in metadata
            session.addMetadata("stripe_session_id", checkoutSessionId);
            
            // Set the checkout URL (in a real implementation, this would come from Stripe API)
            String checkoutUrl = "https://checkout.stripe.com/pay/" + checkoutSessionId;
            session.setPaymentUrl(checkoutUrl);
            
            return session;
        } catch (Exception e) {
            Logger.severe("Failed to create Stripe payment session: " + e.getMessage());
            return null;
        }
    }
    
    @Override
    public boolean verifyPayment(String sessionId) {
        try {
            // Placeholder - In a real implementation, this would verify with Stripe API
            // We would retrieve the session ID from our metadata and check its status
            
            // For demo purposes, just return true
            return true;
        } catch (Exception e) {
            Logger.severe("Failed to verify Stripe payment: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public String getGatewayName() {
        return "stripe";
    }
    
    @Override
    public String getDisplayName() {
        return "Credit Card";
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Create a Stripe checkout session
     * @param amount The payment amount
     * @param description The payment description
     * @param metadata Additional metadata
     * @return The session ID or null if failed
     */
    private String createCheckoutSession(double amount, String description, Map<String, String> metadata) {
        try {
            // Set up the connection
            URL url = new URL("https://api.stripe.com/v1/checkout/sessions");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            
            // Set headers
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setDoOutput(true);
            
            // Build request parameters
            StringBuilder params = new StringBuilder();
            params.append("payment_method_types[0]=card");
            params.append("&mode=payment");
            params.append("&success_url=").append(plugin.getConfig().getString("website.url", "http://localhost:3000")).append("/payment/success");
            params.append("&cancel_url=").append(plugin.getConfig().getString("website.url", "http://localhost:3000")).append("/payment/cancel");
            
            // Add line item
            long amountInCents = Math.round(amount * 100);
            params.append("&line_items[0][price_data][currency]=usd");
            params.append("&line_items[0][price_data][unit_amount]=").append(amountInCents);
            params.append("&line_items[0][price_data][product_data][name]=").append(description);
            params.append("&line_items[0][quantity]=1");
            
            // Add metadata
            if (metadata != null) {
                for (Map.Entry<String, String> entry : metadata.entrySet()) {
                    params.append("&metadata[").append(entry.getKey()).append("]=").append(entry.getValue());
                }
            }
            
            // Send request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = params.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            // Get response
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                Logger.warning("Failed to create Stripe checkout session: " + responseCode);
                return null;
            }
            
            // Read response
            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }
            
            // Parse response - In a real implementation we would use a JSON library
            // For this demo, we'll just extract the session ID manually
            String responseStr = response.toString();
            if (responseStr.contains("id")) {
                int idStart = responseStr.indexOf("id") + 5;
                int idEnd = responseStr.indexOf("\"", idStart);
                return responseStr.substring(idStart, idEnd);
            }
            
            return null;
        } catch (Exception e) {
            Logger.severe("Failed to create Stripe checkout session: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Verify a Stripe webhook signature
     * @param payload The webhook payload
     * @param signature The Stripe-Signature header
     * @return True if valid
     */
    public boolean verifyWebhookSignature(String payload, String signature) {
        // In a real implementation, we would verify the signature using the webhook secret
        // For this demo, we'll just return true
        return true;
    }
} 