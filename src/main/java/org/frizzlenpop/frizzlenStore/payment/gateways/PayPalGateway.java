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
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * PayPal payment gateway implementation
 */
public class PayPalGateway implements PaymentGateway {
    private final FrizzlenStore plugin;
    private final ConfigurationSection config;
    
    private String clientId;
    private String clientSecret;
    private String baseUrl;
    private boolean sandbox;
    private boolean enabled;
    
    // Cache for access tokens
    private String accessToken;
    private long accessTokenExpiry;
    
    /**
     * Create a new PayPal gateway
     * @param plugin The plugin instance
     * @param config The configuration section
     */
    public PayPalGateway(FrizzlenStore plugin, ConfigurationSection config) {
        this.plugin = plugin;
        this.config = config;
    }
    
    @Override
    public boolean initialize() {
        try {
            this.clientId = config.getString("client_id", "");
            this.clientSecret = config.getString("client_secret", "");
            this.sandbox = config.getBoolean("sandbox", true);
            this.enabled = config.getBoolean("enabled", false);
            
            // Set base URL based on sandbox mode
            this.baseUrl = sandbox ? 
                    "https://api.sandbox.paypal.com" : 
                    "https://api.paypal.com";
            
            // Validate configuration
            if (clientId.isEmpty() || clientSecret.isEmpty()) {
                Logger.warning("PayPal gateway missing client_id or client_secret");
                return false;
            }
            
            // Test authentication
            if (!getAccessToken()) {
                Logger.warning("PayPal gateway authentication failed");
                return false;
            }
            
            return true;
        } catch (Exception e) {
            Logger.severe("Failed to initialize PayPal gateway: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public PaymentSession createPaymentSession(String playerName, double amount, String description) {
        try {
            // Ensure we have a valid access token
            if (!getAccessToken()) {
                return null;
            }
            
            // Create a payment session
            PaymentSession session = new PaymentSession(playerName, amount, description, getGatewayName());
            
            // Create PayPal order
            String orderId = createOrder(amount, description);
            if (orderId == null) {
                return null;
            }
            
            // Store the order ID in metadata
            session.addMetadata("paypal_order_id", orderId);
            
            // Set the checkout URL
            String checkoutUrl = sandbox ?
                    "https://www.sandbox.paypal.com/checkoutnow?token=" + orderId :
                    "https://www.paypal.com/checkoutnow?token=" + orderId;
            session.setPaymentUrl(checkoutUrl);
            
            return session;
        } catch (Exception e) {
            Logger.severe("Failed to create PayPal payment session: " + e.getMessage());
            return null;
        }
    }
    
    @Override
    public boolean verifyPayment(String sessionId) {
        try {
            // Placeholder - In a real implementation, this would verify with PayPal API
            // We'd need to get the orderId from the session metadata and call PayPal's API
            // to verify the payment status
            
            // For demo purposes, just return true
            return true;
        } catch (Exception e) {
            Logger.severe("Failed to verify PayPal payment: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public String getGatewayName() {
        return "paypal";
    }
    
    @Override
    public String getDisplayName() {
        return "PayPal";
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Get an access token from PayPal
     * @return True if successful
     */
    private boolean getAccessToken() {
        try {
            // Check if we already have a valid token
            if (accessToken != null && System.currentTimeMillis() < accessTokenExpiry) {
                return true;
            }
            
            // Set up the connection
            URL url = new URL(baseUrl + "/v1/oauth2/token");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            
            // Set authorization header
            String auth = clientId + ":" + clientSecret;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            connection.setRequestProperty("Authorization", "Basic " + encodedAuth);
            
            // Set other headers
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setDoOutput(true);
            
            // Set request body
            String body = "grant_type=client_credentials";
            try (OutputStream os = connection.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            
            // Get response
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                Logger.warning("Failed to get PayPal access token: " + responseCode);
                return false;
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
            // but for this demo we'll just extract the token manually
            String responseStr = response.toString();
            if (responseStr.contains("access_token")) {
                // Extract token and expiry
                int tokenStart = responseStr.indexOf("access_token") + 15;
                int tokenEnd = responseStr.indexOf("\"", tokenStart);
                accessToken = responseStr.substring(tokenStart, tokenEnd);
                
                int expiryStart = responseStr.indexOf("expires_in") + 12;
                int expiryEnd = responseStr.indexOf(",", expiryStart);
                if (expiryEnd == -1) {
                    expiryEnd = responseStr.indexOf("}", expiryStart);
                }
                String expiryStr = responseStr.substring(expiryStart, expiryEnd);
                
                // Calculate expiry time (in milliseconds) - subtract 60 seconds for safety
                long expirySeconds = Long.parseLong(expiryStr) - 60;
                accessTokenExpiry = System.currentTimeMillis() + (expirySeconds * 1000);
                
                return true;
            }
            
            return false;
        } catch (Exception e) {
            Logger.severe("Failed to get PayPal access token: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Create a PayPal order
     * @param amount The payment amount
     * @param description The payment description
     * @return The order ID or null if failed
     */
    private String createOrder(double amount, String description) {
        try {
            // Set up the connection
            URL url = new URL(baseUrl + "/v2/checkout/orders");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            
            // Set headers
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            
            // Format amount to 2 decimal places
            String formattedAmount = String.format("%.2f", amount);
            
            // Build JSON request
            String requestJson = String.format(
                    "{" +
                    "\"intent\": \"CAPTURE\"," +
                    "\"purchase_units\": [{" +
                    "\"amount\": {" +
                    "\"currency_code\": \"USD\"," +
                    "\"value\": \"%s\"" +
                    "}," +
                    "\"description\": \"%s\"" +
                    "}]," +
                    "\"application_context\": {" +
                    "\"return_url\": \"%s\"," +
                    "\"cancel_url\": \"%s\"" +
                    "}" +
                    "}",
                    formattedAmount,
                    description,
                    plugin.getConfigManager().getApiUrl() + "/api/payment/paypal/return",
                    plugin.getConfigManager().getApiUrl() + "/api/payment/paypal/cancel"
            );
            
            // Send request
            try (OutputStream os = connection.getOutputStream()) {
                os.write(requestJson.getBytes(StandardCharsets.UTF_8));
            }
            
            // Get response
            int responseCode = connection.getResponseCode();
            if (responseCode != 201) {
                Logger.warning("Failed to create PayPal order: " + responseCode);
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
            
            // Parse response to get order ID - again, in a real implementation we'd use a JSON library
            String responseStr = response.toString();
            if (responseStr.contains("id")) {
                int idStart = responseStr.indexOf("id") + 5;
                int idEnd = responseStr.indexOf("\"", idStart);
                return responseStr.substring(idStart, idEnd);
            }
            
            return null;
        } catch (IOException e) {
            Logger.severe("Failed to create PayPal order: " + e.getMessage());
            return null;
        }
    }
}