package org.frizzlenpop.frizzlenStore.payment.gateways;

import org.bukkit.configuration.ConfigurationSection;
import org.frizzlenpop.frizzlenStore.FrizzlenStore;
import org.frizzlenpop.frizzlenStore.payment.PaymentSession;
import org.frizzlenpop.frizzlenStore.util.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cryptocurrency payment gateway implementation
 */
public class CryptoGateway implements PaymentGateway {
    private final FrizzlenStore plugin;
    private final ConfigurationSection config;
    
    private String apiKey;
    private String apiSecret;
    private String apiBaseUrl;
    private String webhookSecret;
    private boolean testMode;
    private boolean enabled;
    private String[] acceptedCurrencies;
    
    /**
     * Create a new Crypto gateway
     * @param plugin The plugin instance
     * @param config The configuration section
     */
    public CryptoGateway(FrizzlenStore plugin, ConfigurationSection config) {
        this.plugin = plugin;
        this.config = config;
    }
    
    @Override
    public boolean initialize() {
        try {
            this.apiKey = config.getString("api_key", "");
            this.apiSecret = config.getString("api_secret", "");
            this.apiBaseUrl = config.getString("api_url", "https://api.example.com/v1");
            this.webhookSecret = config.getString("webhook_secret", "");
            this.testMode = config.getBoolean("test_mode", true);
            this.enabled = config.getBoolean("enabled", false);
            
            // Get accepted currencies (defaults to BTC, ETH, LTC if not specified)
            if (config.contains("accepted_currencies")) {
                this.acceptedCurrencies = config.getStringList("accepted_currencies").toArray(new String[0]);
            } else {
                this.acceptedCurrencies = new String[]{"BTC", "ETH", "LTC"};
            }
            
            // Validate configuration
            if (apiKey.isEmpty() || apiSecret.isEmpty()) {
                Logger.warning("Crypto gateway missing api_key or api_secret");
                return false;
            }
            
            // For a real implementation, we would test the API key here
            
            return true;
        } catch (Exception e) {
            Logger.severe("Failed to initialize Crypto gateway: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public PaymentSession createPaymentSession(String playerName, double amount, String description) {
        try {
            // Create a payment session
            PaymentSession session = new PaymentSession(playerName, amount, description, getGatewayName());
            
            // In a real implementation, we would create a payment request with a crypto processor
            // For this demo, we'll just create a dummy session
            String paymentId = "crypto_" + UUID.randomUUID().toString().replace("-", "");
            
            // Store the payment ID in metadata
            session.addMetadata("crypto_payment_id", paymentId);
            
            // Generate a unique address for this payment (in a real implementation, this would come from the API)
            String btcAddress = "bc1q" + UUID.randomUUID().toString().substring(0, 24);
            session.addMetadata("btc_address", btcAddress);
            
            // Convert USD amount to BTC (just a dummy conversion for demo)
            double btcAmount = amount / 40000.0; // Using a fixed exchange rate for demo
            session.addMetadata("btc_amount", String.format("%.8f", btcAmount));
            
            // Set the checkout URL (in a real implementation, this would be a hosted page from the processor)
            String checkoutUrl = "https://crypto-processor.example.com/pay/" + paymentId;
            session.setPaymentUrl(checkoutUrl);
            
            return session;
        } catch (Exception e) {
            Logger.severe("Failed to create Crypto payment session: " + e.getMessage());
            return null;
        }
    }
    
    @Override
    public boolean verifyPayment(String sessionId) {
        try {
            // Placeholder - In a real implementation, this would verify with the crypto processor
            // We would retrieve the payment ID from our metadata and check its status
            
            // For demo purposes, just return true
            return true;
        } catch (Exception e) {
            Logger.severe("Failed to verify Crypto payment: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public String getGatewayName() {
        return "crypto";
    }
    
    @Override
    public String getDisplayName() {
        return "Cryptocurrency";
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Create a crypto payment
     * @param amount The payment amount in USD
     * @param description The payment description
     * @param metadata Additional metadata
     * @return The payment ID or null if failed
     */
    private String createCryptoPayment(double amount, String description, Map<String, String> metadata) {
        try {
            // Set up the connection
            URL url = new URL(apiBaseUrl + "/payments");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            
            // Set headers
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            
            // Build JSON request
            StringBuilder jsonRequestBuilder = new StringBuilder();
            jsonRequestBuilder.append("{");
            jsonRequestBuilder.append("\"price_amount\":").append(amount).append(",");
            jsonRequestBuilder.append("\"price_currency\":\"USD\",");
            jsonRequestBuilder.append("\"pay_currency\":\"BTC\",");
            jsonRequestBuilder.append("\"ipn_callback_url\":\"").append(plugin.getConfig().getString("website.url", "http://localhost:3000")).append("/api/payments/ipn/crypto\",");
            jsonRequestBuilder.append("\"order_id\":\"").append(UUID.randomUUID().toString()).append("\",");
            jsonRequestBuilder.append("\"order_description\":\"").append(description).append("\",");
            
            // Add metadata
            if (metadata != null && !metadata.isEmpty()) {
                jsonRequestBuilder.append("\"metadata\":{");
                boolean firstEntry = true;
                for (Map.Entry<String, String> entry : metadata.entrySet()) {
                    if (!firstEntry) {
                        jsonRequestBuilder.append(",");
                    }
                    jsonRequestBuilder.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
                    firstEntry = false;
                }
                jsonRequestBuilder.append("},");
            }
            
            // Close JSON object
            jsonRequestBuilder.append("\"success_url\":\"").append(plugin.getConfig().getString("website.url", "http://localhost:3000")).append("/payment/success\",");
            jsonRequestBuilder.append("\"cancel_url\":\"").append(plugin.getConfig().getString("website.url", "http://localhost:3000")).append("/payment/cancel\"");
            jsonRequestBuilder.append("}");
            
            String jsonRequest = jsonRequestBuilder.toString();
            
            // Send request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonRequest.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            // Get response
            int responseCode = connection.getResponseCode();
            if (responseCode != 200 && responseCode != 201) {
                Logger.warning("Failed to create crypto payment: " + responseCode);
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
            // For this demo, we'll just extract the payment ID manually
            String responseStr = response.toString();
            if (responseStr.contains("id")) {
                int idStart = responseStr.indexOf("id") + 5;
                int idEnd = responseStr.indexOf("\"", idStart);
                return responseStr.substring(idStart, idEnd);
            }
            
            return null;
        } catch (Exception e) {
            Logger.severe("Failed to create crypto payment: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Verify a crypto payment notification
     * @param payload The notification payload
     * @param signature The signature header
     * @return True if valid
     */
    public boolean verifyNotification(String payload, String signature) {
        // In a real implementation, we would verify the signature using the webhook secret
        // For this demo, we'll just return true
        return true;
    }
    
    /**
     * Get the current exchange rate for a cryptocurrency
     * @param cryptoCurrency The cryptocurrency code (e.g., BTC)
     * @return The exchange rate in USD or -1 if failed
     */
    public double getExchangeRate(String cryptoCurrency) {
        try {
            // Set up the connection
            URL url = new URL(apiBaseUrl + "/rates?base=USD&target=" + cryptoCurrency);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            
            // Set headers
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            
            // Get response
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                Logger.warning("Failed to get exchange rate: " + responseCode);
                return -1;
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
            // For this demo, we'll just extract the rate manually
            String responseStr = response.toString();
            if (responseStr.contains("rate")) {
                int rateStart = responseStr.indexOf("rate") + 6;
                int rateEnd = responseStr.indexOf(",", rateStart);
                if (rateEnd == -1) {
                    rateEnd = responseStr.indexOf("}", rateStart);
                }
                String rateStr = responseStr.substring(rateStart, rateEnd);
                return Double.parseDouble(rateStr);
            }
            
            return -1;
        } catch (Exception e) {
            Logger.severe("Failed to get exchange rate: " + e.getMessage());
            return -1;
        }
    }
    
    /**
     * Get the supported cryptocurrencies
     * @return Array of supported currency codes
     */
    public String[] getAcceptedCurrencies() {
        return acceptedCurrencies.clone();
    }
} 