package com.backend.Controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/google")
public class GoogleApiController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String getKey() {
        String k = System.getenv("GOOGLE_API_KEY");
        return k != null ? k : "";
    }

    private static HttpClient httpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        String key = getKey();
        boolean configured = key != null && !key.trim().isEmpty();
        return ResponseEntity.ok(Map.of(
            "configured", configured,
            "message", configured ? "Google API key is set. You can use Gemini, Maps, and Speech-to-Text." : "Google API key is not set. Add GOOGLE_API_KEY in Railway environment variables."
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() { return gemini(); }

    @GetMapping("/gemini")
    public ResponseEntity<Map<String, String>> gemini() {
        String key = getKey();
        if (key == null || key.trim().isEmpty())
            return ResponseEntity.ok(Map.of("status", "not_configured", "message", "GOOGLE_API_KEY is not set.", "service", "Gemini"));
        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{\"contents\":[{\"parts\":[{\"text\":\"Reply with exactly: OK\"}]}]}")).build();
            HttpResponse<String> resp = httpClient().send(req, HttpResponse.BodyHandlers.ofString());
            String body = resp.body();
            if (resp.statusCode() != 200) return ResponseEntity.ok(Map.of("status", "error", "message", body.length() > 200 ? body.substring(0, 200) + "..." : body, "service", "Gemini"));
            JsonNode root = MAPPER.readTree(body);
            String message = "OK";
            if (root.has("candidates") && root.get("candidates").isArray() && root.get("candidates").size() > 0) {
                JsonNode cand = root.get("candidates").get(0);
                if (cand.has("content") && cand.get("content").has("parts") && cand.get("content").get("parts").size() > 0)
                    message = cand.get("content").get("parts").get(0).path("text").asText("OK").trim();
            }
            return ResponseEntity.ok(Map.of("status", "ok", "message", message, "service", "Gemini"));
        } catch (Exception e) { return ResponseEntity.ok(Map.of("status", "error", "message", e.getMessage() != null ? e.getMessage() : "", "service", "Gemini")); }
    }

    @GetMapping("/geocoding")
    public ResponseEntity<Map<String, String>> geocoding() {
        String key = getKey();
        if (key == null || key.trim().isEmpty()) return ResponseEntity.ok(Map.of("status", "not_configured", "message", "GOOGLE_API_KEY is not set.", "service", "Geocoding"));
        try {
            String url = "https://maps.googleapis.com/maps/api/geocode/json?address=Times+Square+New+York&key=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> resp = httpClient().send(req, HttpResponse.BodyHandlers.ofString());
            String body = resp.body();
            if (resp.statusCode() != 200) return ResponseEntity.ok(Map.of("status", "error", "message", body.length() > 200 ? body.substring(0, 200) + "..." : body, "service", "Geocoding"));
            JsonNode json = MAPPER.readTree(body);
            String status = json.path("status").asText("");
            if ("OK".equals(status)) return ResponseEntity.ok(Map.of("status", "ok", "message", "Geocoding API responded successfully.", "service", "Geocoding"));
            return ResponseEntity.ok(Map.of("status", "error", "message", status, "service", "Geocoding"));
        } catch (Exception e) { return ResponseEntity.ok(Map.of("status", "error", "message", e.getMessage() != null ? e.getMessage() : "", "service", "Geocoding")); }
    }

    @GetMapping("/maps")
    public ResponseEntity<Map<String, String>> maps() {
        String key = getKey();
        if (key == null || key.trim().isEmpty()) return ResponseEntity.ok(Map.of("status", "not_configured", "message", "GOOGLE_API_KEY is not set.", "service", "Maps"));
        try {
            String url = "https://maps.googleapis.com/maps/api/js?key=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> resp = httpClient().send(req, HttpResponse.BodyHandlers.ofString());
            String body = resp.body();
            if (resp.statusCode() != 200) return ResponseEntity.ok(Map.of("status", "error", "message", body.length() > 200 ? body.substring(0, 200) + "..." : body, "service", "Maps"));
            if (body.contains("ApiNotActivatedMapError")) return ResponseEntity.ok(Map.of("status", "error", "message", "Maps JavaScript API is not enabled for this key.", "service", "Maps"));
            if (body.contains("RefererNotAllowedMapError")) return ResponseEntity.ok(Map.of("status", "error", "message", "Referer not allowed for this key.", "service", "Maps"));
            if (body.contains("InvalidKeyMapError")) return ResponseEntity.ok(Map.of("status", "error", "message", "Invalid API key.", "service", "Maps"));
            return ResponseEntity.ok(Map.of("status", "ok", "message", "Maps JavaScript API key valid.", "service", "Maps"));
        } catch (Exception e) { return ResponseEntity.ok(Map.of("status", "error", "message", e.getMessage() != null ? e.getMessage() : "", "service", "Maps")); }
    }

    @GetMapping("/directions")
    public ResponseEntity<Map<String, String>> directions() {
        String key = getKey();
        if (key == null || key.trim().isEmpty()) return ResponseEntity.ok(Map.of("status", "not_configured", "message", "GOOGLE_API_KEY is not set.", "service", "Directions"));
        try {
            String origin = URLEncoder.encode("Times Square, New York, NY", StandardCharsets.UTF_8);
            String dest = URLEncoder.encode("Brooklyn Bridge, New York, NY", StandardCharsets.UTF_8);
            String url = "https://maps.googleapis.com/maps/api/directions/json?origin=" + origin + "&destination=" + dest + "&key=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> resp = httpClient().send(req, HttpResponse.BodyHandlers.ofString());
            String body = resp.body();
            if (resp.statusCode() != 200) return ResponseEntity.ok(Map.of("status", "error", "message", body.length() > 200 ? body.substring(0, 200) + "..." : body, "service", "Directions"));
            JsonNode json = MAPPER.readTree(body);
            String status = json.path("status").asText("");
            if ("OK".equals(status)) return ResponseEntity.ok(Map.of("status", "ok", "message", "Directions API responded successfully. Use it from the backend to return routes to the frontend.", "service", "Directions"));
            return ResponseEntity.ok(Map.of("status", "error", "message", status, "service", "Directions"));
        } catch (Exception e) { return ResponseEntity.ok(Map.of("status", "error", "message", e.getMessage() != null ? e.getMessage() : "", "service", "Directions")); }
    }

    @GetMapping("/places")
    public ResponseEntity<Map<String, String>> places() {
        String key = getKey();
        if (key == null || key.trim().isEmpty()) return ResponseEntity.ok(Map.of("status", "not_configured", "message", "GOOGLE_API_KEY is not set.", "service", "Places"));
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create("https://places.googleapis.com/v1/places:searchText")).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json").header("X-Goog-Api-Key", key).header("X-Goog-FieldMask", "places.id")
                .POST(HttpRequest.BodyPublishers.ofString("{\"textQuery\":\"coffee\"}")).build();
            HttpResponse<String> resp = httpClient().send(req, HttpResponse.BodyHandlers.ofString());
            String body = resp.body();
            if (resp.statusCode() == 200) return ResponseEntity.ok(Map.of("status", "ok", "message", "Places API (New) responded successfully.", "service", "Places"));
            return ResponseEntity.ok(Map.of("status", "error", "message", body.length() > 200 ? body.substring(0, 200) + "..." : body, "service", "Places"));
        } catch (Exception e) { return ResponseEntity.ok(Map.of("status", "error", "message", e.getMessage() != null ? e.getMessage() : "", "service", "Places")); }
    }

    @GetMapping("/speech-to-text")
    public ResponseEntity<Map<String, String>> speechToText() {
        String key = getKey();
        if (key == null || key.trim().isEmpty()) return ResponseEntity.ok(Map.of("status", "not_configured", "message", "GOOGLE_API_KEY is not set.", "service", "SpeechToText"));
        try {
            byte[] silence = new byte[3200];
            String base64Audio = java.util.Base64.getEncoder().encodeToString(silence);
            String payload = "{\"config\":{\"encoding\":\"LINEAR16\",\"sampleRateHertz\":16000,\"languageCode\":\"en-US\"},\"audio\":{\"content\":\"" + base64Audio + "\"}}";
            String url = "https://speech.googleapis.com/v1/speech:recognize?key=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(payload)).build();
            HttpResponse<String> resp = httpClient().send(req, HttpResponse.BodyHandlers.ofString());
            String body = resp.body();
            if (resp.statusCode() == 200) return ResponseEntity.ok(Map.of("status", "ok", "message", "Speech-to-Text API accepted the request.", "service", "SpeechToText"));
            if (resp.statusCode() == 400 && body != null && body.contains("No speech")) return ResponseEntity.ok(Map.of("status", "ok", "message", "Speech-to-Text API responded (no speech in test audio).", "service", "SpeechToText"));
            return ResponseEntity.ok(Map.of("status", "error", "message", body != null && body.length() > 200 ? body.substring(0, 200) + "..." : (body != null ? body : ""), "service", "SpeechToText"));
        } catch (Exception e) { return ResponseEntity.ok(Map.of("status", "error", "message", e.getMessage() != null ? e.getMessage() : "", "service", "SpeechToText")); }
    }
}
