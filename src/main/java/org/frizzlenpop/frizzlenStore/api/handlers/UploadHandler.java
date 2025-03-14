package org.frizzlenpop.frizzlenStore.api.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.frizzlenpop.frizzlenStore.FrizzlenStore;
import org.frizzlenpop.frizzlenStore.util.Logger;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.UUID;

/**
 * Handles API requests for file uploads
 */
public class UploadHandler implements HttpHandler {

    private final FrizzlenStore plugin;
    private final String uploadDir;

    /**
     * Constructor
     * @param plugin The plugin instance
     */
    public UploadHandler(FrizzlenStore plugin) {
        this.plugin = plugin;
        this.uploadDir = plugin.getDataFolder().getAbsolutePath() + File.separator + "uploads";
        
        // Ensure upload directory exists
        File dir = new File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        Logger.info("UploadHandler initialized with upload directory: " + uploadDir);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Set CORS headers
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With, Accept, Origin");
        exchange.getResponseHeaders().add("Access-Control-Allow-Credentials", "true");
        exchange.getResponseHeaders().add("Access-Control-Max-Age", "3600");
        
        // Handle preflight requests
        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        try {
            // Verify API token if needed
            String token = exchange.getRequestHeaders().getFirst("Authorization");
            if (token == null || !token.startsWith("Bearer ")) {
                // For this example, we're not enforcing token authentication for uploads
                // Uncomment the below code if you want to enforce authentication
                /*
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Unauthorized")
                        .toString();
                sendResponse(exchange, 401, response);
                return;
                */
            }

            // Only allow POST for file uploads
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Method not allowed")
                        .toString();
                sendResponse(exchange, 405, response);
                return;
            }

            // Handle file upload
            handleFileUpload(exchange);
        } catch (Exception e) {
            Logger.severe("Error handling upload request: " + e.getMessage());
            e.printStackTrace();
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Internal server error: " + e.getMessage())
                    .toString();
            sendResponse(exchange, 500, response);
        }
    }

    /**
     * Handle file upload
     * @param exchange The HTTP exchange
     * @throws IOException If an I/O error occurs
     */
    private void handleFileUpload(HttpExchange exchange) throws IOException {
        // Get content type header
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.startsWith("multipart/form-data")) {
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Content-Type must be multipart/form-data")
                    .toString();
            sendResponse(exchange, 400, response);
            return;
        }

        // Parse boundary from content type
        String boundary = null;
        for (String part : contentType.split(";")) {
            part = part.trim();
            if (part.startsWith("boundary=")) {
                boundary = part.substring("boundary=".length());
                if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
                    boundary = boundary.substring(1, boundary.length() - 1);
                }
                break;
            }
        }

        if (boundary == null) {
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "No boundary found in multipart/form-data")
                    .toString();
            sendResponse(exchange, 400, response);
            return;
        }

        // Create temporary file for upload
        String tempFileName = System.currentTimeMillis() + "_" + UUID.randomUUID().toString();
        Path tempFilePath = Paths.get(uploadDir, tempFileName);
        
        try {
            // Read the request body into a byte array
            byte[] body = exchange.getRequestBody().readAllBytes();
            String bodyStr = new String(body, StandardCharsets.UTF_8);
            
            // Find the start of the file data
            String startMarker = "Content-Disposition: form-data; name=\"image\"";
            int startPos = bodyStr.indexOf(startMarker);
            
            if (startPos == -1) {
                // Try alternate form field names if "image" is not found
                String[] possibleFieldNames = {"image", "file", "photo", "picture", "upload"};
                for (String fieldName : possibleFieldNames) {
                    startMarker = "Content-Disposition: form-data; name=\"" + fieldName + "\"";
                    startPos = bodyStr.indexOf(startMarker);
                    if (startPos != -1) break;
                }
                
                if (startPos == -1) {
                    String response = new JSONObject()
                            .put("success", false)
                            .put("error", "No file field found")
                            .toString();
                    sendResponse(exchange, 400, response);
                    return;
                }
            }
            
            // Try to extract the original filename for reference
            String filenameMarker = "filename=\"";
            int filenamePos = bodyStr.indexOf(filenameMarker, startPos);
            String originalFilename = null;
            String inferredExtension = null;
            
            if (filenamePos != -1) {
                filenamePos += filenameMarker.length();
                int filenameEndPos = bodyStr.indexOf("\"", filenamePos);
                if (filenameEndPos != -1) {
                    originalFilename = bodyStr.substring(filenamePos, filenameEndPos);
                    // Get extension from original filename
                    int dotPos = originalFilename.lastIndexOf(".");
                    if (dotPos != -1) {
                        inferredExtension = originalFilename.substring(dotPos);
                    }
                }
            }
            
            // Check for Content-Type in multipart data to help determine file type
            String fileContentType = null;
            String contentTypeMarker = "Content-Type: ";
            int contentTypePos = bodyStr.indexOf(contentTypeMarker, startPos);
            if (contentTypePos != -1) {
                contentTypePos += contentTypeMarker.length();
                int contentTypeEndPos = bodyStr.indexOf("\r\n", contentTypePos);
                if (contentTypeEndPos != -1) {
                    fileContentType = bodyStr.substring(contentTypePos, contentTypeEndPos).trim();
                }
            }
            
            // Skip to the end of the headers
            int dataStart = bodyStr.indexOf("\r\n\r\n", startPos);
            if (dataStart == -1) {
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Invalid multipart format")
                        .toString();
                sendResponse(exchange, 400, response);
                return;
            }
            dataStart += 4;
            
            // Find the end of the file data
            String endMarker = "--" + boundary + "--";
            int endPos = bodyStr.indexOf(endMarker, dataStart);
            
            if (endPos == -1) {
                endMarker = "--" + boundary;
                endPos = bodyStr.indexOf(endMarker, dataStart);
            }
            
            if (endPos == -1) {
                String response = new JSONObject()
                        .put("success", false)
                        .put("error", "Could not find end of file data")
                        .toString();
                sendResponse(exchange, 400, response);
                return;
            }
            
            // Extract the file data, making sure to account for the trailing \r\n
            int fileEndPos = endPos;
            if (bodyStr.substring(endPos - 2, endPos).equals("\r\n")) {
                fileEndPos -= 2;
            }
            
            byte[] fileData = Arrays.copyOfRange(body, dataStart, fileEndPos);
            
            // Determine file extension by content analysis
            String fileExt = determineFileExtension(fileData);
            
            // If we couldn't determine from bytes, try using the Content-Type
            if (fileExt == null && fileContentType != null) {
                fileExt = getExtensionFromContentType(fileContentType);
            }
            
            // If still no extension, use the one from the original filename
            if (fileExt == null && inferredExtension != null) {
                fileExt = inferredExtension;
            }
            
            // Default to .bin if we still don't have an extension
            if (fileExt == null) {
                fileExt = ".bin";
            }
            
            // Always ensure file extension starts with a dot
            if (!fileExt.startsWith(".")) {
                fileExt = "." + fileExt;
            }
            
            // Create a filename with the extension
            String fileName = tempFileName + fileExt;
            Path finalPath = Paths.get(uploadDir, fileName);
            
            // Save the file
            Files.write(finalPath, fileData);
            
            Logger.info("File uploaded: " + finalPath + " (original name: " + 
                        (originalFilename != null ? originalFilename : "unknown") + 
                        ", detected type: " + (fileContentType != null ? fileContentType : "unknown") + ")");
            
            // Generate URL for the uploaded file
            String serverUrl = plugin.getConfigManager().getConfig().getString("api.server-url", "http://localhost:8081");
            String fileUrl = serverUrl + "/uploads/" + fileName;
            
            // Respond with the file URL
            String response = new JSONObject()
                    .put("success", true)
                    .put("url", fileUrl)
                    .put("originalName", originalFilename)
                    .put("type", fileContentType)
                    .toString();
            
            sendResponse(exchange, 200, response);
        } catch (Exception e) {
            // Delete the temporary file if it was created
            Files.deleteIfExists(tempFilePath);
            
            Logger.severe("Error processing file upload: " + e.getMessage());
            e.printStackTrace();
            
            String response = new JSONObject()
                    .put("success", false)
                    .put("error", "Failed to process upload: " + e.getMessage())
                    .toString();
            sendResponse(exchange, 500, response);
        }
    }
    
    /**
     * Get file extension from HTTP Content-Type
     * @param contentType The HTTP Content-Type value
     * @return The file extension with dot, or null if unknown
     */
    private String getExtensionFromContentType(String contentType) {
        contentType = contentType.toLowerCase();
        
        if (contentType.contains("image/jpeg") || contentType.contains("image/jpg")) {
            return ".jpg";
        } else if (contentType.contains("image/png")) {
            return ".png";
        } else if (contentType.contains("image/gif")) {
            return ".gif";
        } else if (contentType.contains("image/webp")) {
            return ".webp";
        } else if (contentType.contains("image/svg+xml")) {
            return ".svg";
        } else if (contentType.contains("image/bmp")) {
            return ".bmp";
        } else if (contentType.contains("image/tiff")) {
            return ".tiff";
        } else if (contentType.contains("image/x-icon") || contentType.contains("image/vnd.microsoft.icon")) {
            return ".ico";
        }
        
        return null;
    }
    
    /**
     * Determine the file extension based on file content
     * @param fileData The file data
     * @return The file extension with dot, or null if unknown
     */
    private String determineFileExtension(byte[] fileData) {
        if (fileData.length < 8) {
            return null;
        }
        
        // Check for JPEG: first 2 bytes are FF D8
        if (fileData[0] == (byte) 0xFF && fileData[1] == (byte) 0xD8) {
            return ".jpg";
        }
        
        // Check for PNG: bytes 0-7 is 89 50 4E 47 0D 0A 1A 0A
        if (fileData[0] == (byte) 0x89 && fileData[1] == (byte) 0x50 && 
            fileData[2] == (byte) 0x4E && fileData[3] == (byte) 0x47 &&
            fileData[4] == (byte) 0x0D && fileData[5] == (byte) 0x0A &&
            fileData[6] == (byte) 0x1A && fileData[7] == (byte) 0x0A) {
            return ".png";
        }
        
        // Check for GIF: bytes 0-5 is 47 49 46 38 39/37 61 (GIF89a or GIF87a)
        if (fileData[0] == (byte) 0x47 && fileData[1] == (byte) 0x49 && 
            fileData[2] == (byte) 0x46 && fileData[3] == (byte) 0x38 &&
            (fileData[4] == (byte) 0x39 || fileData[4] == (byte) 0x37) && 
            fileData[5] == (byte) 0x61) {
            return ".gif";
        }
        
        // Check for BMP: bytes 0-1 is 42 4D (BM)
        if (fileData[0] == (byte) 0x42 && fileData[1] == (byte) 0x4D) {
            return ".bmp";
        }
        
        // Check for WebP: bytes 8-11 is 57 45 42 50 (WEBP)
        if (fileData.length >= 12 &&
            fileData[8] == (byte) 0x57 && fileData[9] == (byte) 0x45 && 
            fileData[10] == (byte) 0x42 && fileData[11] == (byte) 0x50) {
            return ".webp";
        }
        
        // Unknown format
        return null;
    }
    
    /**
     * Send HTTP response
     * @param exchange The HTTP exchange
     * @param statusCode The HTTP status code
     * @param response The response body
     * @throws IOException If an I/O error occurs
     */
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.length());
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes(StandardCharsets.UTF_8));
        os.close();
    }
} 