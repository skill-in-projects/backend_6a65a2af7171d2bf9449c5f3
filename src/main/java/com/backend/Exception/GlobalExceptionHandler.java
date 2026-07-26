package com.backend.Exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleAllExceptions(Exception ex, WebRequest request, HttpServletRequest httpRequest) {
        logger.error("[EXCEPTION HANDLER] Unhandled exception occurred: {}", ex.getMessage(), ex);

        // Extract boardId from request
        String boardId = extractBoardId(httpRequest);
        logger.warn("[EXCEPTION HANDLER] Extracted boardId: {}", boardId != null ? boardId : "NULL");

        // Send error to runtime error endpoint (fire and forget)
        String runtimeErrorEndpointUrl = System.getenv("RUNTIME_ERROR_ENDPOINT_URL");
        if (runtimeErrorEndpointUrl != null && !runtimeErrorEndpointUrl.isEmpty()) {
            logger.warn("[EXCEPTION HANDLER] Sending error to endpoint: {}", runtimeErrorEndpointUrl);
            sendErrorToEndpoint(runtimeErrorEndpointUrl, boardId, httpRequest, ex);
        } else {
            logger.warn("[EXCEPTION HANDLER] RUNTIME_ERROR_ENDPOINT_URL is not set - skipping error reporting");
        }

        // Return error response to client
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(java.util.Map.of(
                        "error", "An error occurred while processing your request",
                        "message", ex.getMessage() != null ? ex.getMessage() : "Unknown error"
                ));
    }

    private String extractBoardId(HttpServletRequest request) {
        // Try route parameter
        String boardId = request.getParameter("boardId");
        if (boardId != null && !boardId.isEmpty()) {
            return boardId;
        }

        // Try header
        boardId = request.getHeader("X-Board-Id");
        if (boardId != null && !boardId.isEmpty()) {
            return boardId;
        }

        // Try environment variable
        boardId = System.getenv("BOARD_ID");
        if (boardId != null && !boardId.isEmpty()) {
            return boardId;
        }

        // Try to extract from hostname (Railway pattern: webapi{boardId}.up.railway.app - no hyphen)
        String host = request.getServerName();
        if (host != null) {
            Pattern pattern = Pattern.compile("webapi([a-f0-9]{24})", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(host);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        // Try to extract from RUNTIME_ERROR_ENDPOINT_URL if it contains boardId pattern
        String endpointUrl = System.getenv("RUNTIME_ERROR_ENDPOINT_URL");
        if (endpointUrl != null && !endpointUrl.isEmpty()) {
            Pattern pattern = Pattern.compile("webapi([a-f0-9]{24})", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(endpointUrl);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        return null;
    }

    private void sendErrorToEndpoint(String endpointUrl, String boardId, HttpServletRequest request, Exception exception) {
        // Run in background thread to avoid blocking the response
        new Thread(() -> {
            try {
                String stackTrace = getStackTrace(exception);
                String file = exception.getStackTrace().length > 0 ? exception.getStackTrace()[0].getFileName() : null;
                Integer line = exception.getStackTrace().length > 0 ? exception.getStackTrace()[0].getLineNumber() : null;

                String requestPath = request.getRequestURI();
                String requestMethod = request.getMethod();
                String userAgent = request.getHeader("User-Agent");

                // Build JSON payload
                String jsonPayload = String.format(
                        "{\"boardId\":%s,\"timestamp\":\"%s\",\"file\":%s,\"line\":%s,\"stackTrace\":%s,\"message\":%s,\"exceptionType\":%s,\"requestPath\":%s,\"requestMethod\":%s,\"userAgent\":%s}",
                        boardId != null ? "\"" + boardId + "\"" : "null",
                        Instant.now().toString(),
                        file != null ? "\"" + escapeJson(file) + "\"" : "null",
                        line != null ? line.toString() : "null",
                        "\"" + escapeJson(stackTrace) + "\"",
                        "\"" + escapeJson(exception.getMessage() != null ? exception.getMessage() : "Unknown error") + "\"",
                        "\"" + exception.getClass().getName() + "\"",
                        "\"" + escapeJson(requestPath) + "\"",
                        "\"" + escapeJson(requestMethod) + "\"",
                        userAgent != null ? "\"" + escapeJson(userAgent) + "\"" : "null"
                );

                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(endpointUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .timeout(Duration.ofSeconds(5))
                        .build();

                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                logger.warn("[EXCEPTION HANDLER] Error endpoint response: {} - {}", response.statusCode(), response.body());
            } catch (Exception e) {
                logger.error("[EXCEPTION HANDLER] Failed to send error to endpoint: {}", e.getMessage(), e);
            }
        }).start();
    }

    private String getStackTrace(Exception exception) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        exception.printStackTrace(pw);
        return sw.toString();
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
