package com.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        try {
            SpringApplication.run(Application.class, args);
        } catch (Exception startupEx) {
            System.err.println("[STARTUP ERROR] Application failed to start: " + startupEx.getMessage());
            startupEx.printStackTrace();
            
            // Send startup error to endpoint (fire and forget)
            String runtimeErrorEndpointUrl = System.getenv("RUNTIME_ERROR_ENDPOINT_URL");
            String boardId = System.getenv("BOARD_ID");
            
            if (runtimeErrorEndpointUrl != null && !runtimeErrorEndpointUrl.isEmpty()) {
                new Thread(() -> {
                    try {
                        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                            .connectTimeout(java.time.Duration.ofSeconds(5))
                            .build();
                        
                        String stackTrace = getStackTrace(startupEx);
                        String file = startupEx.getStackTrace().length > 0 ? 
                            startupEx.getStackTrace()[0].getFileName() : null;
                        Integer line = startupEx.getStackTrace().length > 0 ? 
                            startupEx.getStackTrace()[0].getLineNumber() : null;
                        
                        String jsonPayload = String.format(
                            "{\"boardId\":%s,\"timestamp\":\"%s\",\"file\":%s,\"line\":%s,\"stackTrace\":\"%s\",\"message\":\"%s\",\"exceptionType\":\"%s\",\"requestPath\":\"STARTUP\",\"requestMethod\":\"STARTUP\",\"userAgent\":\"STARTUP_ERROR\"}",
                            boardId != null ? "\"" + escapeJson(boardId) + "\"" : "null",
                            java.time.Instant.now().toString(),
                            file != null ? "\"" + escapeJson(file) + "\"" : "null",
                            line != null ? line.toString() : "null",
                            escapeJson(stackTrace),
                            escapeJson(startupEx.getMessage() != null ? startupEx.getMessage() : "Unknown error"),
                            escapeJson(startupEx.getClass().getName())
                        );
                        
                        java.net.http.HttpRequest httpRequest = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(runtimeErrorEndpointUrl))
                            .header("Content-Type", "application/json")
                            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonPayload))
                            .timeout(java.time.Duration.ofSeconds(5))
                            .build();
                        
                        httpClient.send(httpRequest, java.net.http.HttpResponse.BodyHandlers.ofString());
                    } catch (Exception e) {
                        // Ignore
                    }
                }).start();
            }
            
            System.exit(1);
        }
    }
    
    private static String getStackTrace(Exception exception) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        exception.printStackTrace(pw);
        return sw.toString();
    }
    
    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
