package org.frizzlenpop.frizzlenStore.api.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.frizzlenpop.frizzlenStore.FrizzlenStore;
import org.frizzlenpop.frizzlenStore.util.Logger;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Handler for serving static files
 */
public class StaticFileHandler implements HttpHandler {

    private final FrizzlenStore plugin;
    private final String basePath;
    private final String urlPrefix;

    /**
     * Constructor
     * @param plugin The plugin instance
     * @param basePath The base path to serve files from
     * @param urlPrefix The URL prefix to match
     */
    public StaticFileHandler(FrizzlenStore plugin, String basePath, String urlPrefix) {
        this.plugin = plugin;
        this.basePath = basePath;
        this.urlPrefix = urlPrefix;
        
        // Ensure base directory exists
        File baseDir = new File(basePath);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        
        Logger.info("StaticFileHandler initialized with base path: " + basePath);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Set CORS headers
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With, Accept, Origin");
        exchange.getResponseHeaders().add("Access-Control-Allow-Credentials", "true");
        exchange.getResponseHeaders().add("Access-Control-Max-Age", "3600");
        
        // Handle preflight requests
        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        // Only allow GET for file serving
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        // Get the requested path
        String requestPath = exchange.getRequestURI().getPath();
        
        // Decode URL for any URL encoded characters
        try {
            requestPath = java.net.URLDecoder.decode(requestPath, "UTF-8");
        } catch (Exception e) {
            Logger.warning("Failed to decode URL: " + requestPath);
            // Continue with the original path
        }
        
        // Strip the URL prefix from the path
        if (requestPath.startsWith(urlPrefix)) {
            requestPath = requestPath.substring(urlPrefix.length());
        }
        
        // Remove leading slash if present
        if (requestPath.startsWith("/")) {
            requestPath = requestPath.substring(1);
        }
        
        // Prevent directory traversal
        if (requestPath.contains("..") || requestPath.contains("\\")) {
            Logger.warning("Directory traversal attempt: " + requestPath);
            exchange.sendResponseHeaders(403, -1);
            return;
        }
        
        // Construct full file path
        Path filePath = Paths.get(basePath, requestPath);
        File file = filePath.toFile();
        
        Logger.debug("Serving file: " + filePath + " for request: " + requestPath);
        
        // Check if file exists
        if (!file.exists() || !file.isFile()) {
            Logger.warning("File not found: " + filePath);
            exchange.sendResponseHeaders(404, -1);
            return;
        }

        try {
            // Set content type based on file extension or content
            String contentType = getContentType(filePath.toString());
            exchange.getResponseHeaders().add("Content-Type", contentType);
            Logger.debug("Content-Type set to: " + contentType + " for file: " + filePath);
            
            // Cache control headers for better performance
            exchange.getResponseHeaders().add("Cache-Control", "public, max-age=86400");
            
            // Send the file
            byte[] fileBytes = Files.readAllBytes(filePath);
            exchange.sendResponseHeaders(200, fileBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(fileBytes);
            }
            
            Logger.debug("File served successfully: " + filePath);
        } catch (IOException e) {
            Logger.severe("Error serving file: " + e.getMessage());
            e.printStackTrace();
            exchange.sendResponseHeaders(500, -1);
        }
    }
    
    /**
     * Get content type based on file extension
     * @param path The file path
     * @return The content type
     */
    private String getContentType(String path) {
        // Convert to lowercase to ensure case-insensitive matching
        path = path.toLowerCase();
        
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (path.endsWith(".png")) {
            return "image/png";
        } else if (path.endsWith(".gif")) {
            return "image/gif";
        } else if (path.endsWith(".webp")) {
            return "image/webp";
        } else if (path.endsWith(".bmp")) {
            return "image/bmp";
        } else if (path.endsWith(".svg")) {
            return "image/svg+xml";
        } else if (path.endsWith(".ico")) {
            return "image/x-icon";
        } else if (path.endsWith(".tiff") || path.endsWith(".tif")) {
            return "image/tiff";
        } else if (path.endsWith(".css")) {
            return "text/css";
        } else if (path.endsWith(".js")) {
            return "application/javascript";
        } else if (path.endsWith(".html") || path.endsWith(".htm")) {
            return "text/html";
        } else if (path.endsWith(".txt")) {
            return "text/plain";
        } else if (path.endsWith(".xml")) {
            return "application/xml";
        } else if (path.endsWith(".json")) {
            return "application/json";
        } else if (path.endsWith(".pdf")) {
            return "application/pdf";
        } else if (path.endsWith(".zip")) {
            return "application/zip";
        } else if (path.endsWith(".mp3")) {
            return "audio/mpeg";
        } else if (path.endsWith(".mp4")) {
            return "video/mp4";
        } else {
            // Try to guess content type for binary files
            try {
                Path filePath = Paths.get(path);
                if (Files.exists(filePath)) {
                    byte[] bytes = Files.readAllBytes(filePath);
                    if (bytes.length > 8) {
                        // Check for common file signatures
                        if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) {
                            return "image/jpeg"; // JPEG
                        } else if (bytes[0] == (byte) 0x89 && bytes[1] == (byte) 0x50 &&
                                  bytes[2] == (byte) 0x4E && bytes[3] == (byte) 0x47) {
                            return "image/png"; // PNG
                        } else if (bytes[0] == (byte) 0x47 && bytes[1] == (byte) 0x49 &&
                                  bytes[2] == (byte) 0x46) {
                            return "image/gif"; // GIF
                        } else if (bytes[0] == (byte) 0x42 && bytes[1] == (byte) 0x4D) {
                            return "image/bmp"; // BMP
                        }
                    }
                }
            } catch (Exception e) {
                // Fall back to default if content detection fails
                Logger.debug("Failed to detect file type by content: " + e.getMessage());
            }
            
            return "application/octet-stream";
        }
    }
} 