package api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Lightweight HTTP client supporting GET, POST, PUT, and DELETE.
 *
 * <p>
 * This replaces the old {@code ApiValidator} with a cleaner,
 * method-per-verb design used by Cucumber step definitions.
 * </p>
 */
public class ApiClient {

    private static final Logger log = LoggerFactory.getLogger(ApiClient.class);
    private static final int TIMEOUT_SEC = 30;
    private static final String JSON_TYPE = "application/json";

    private final HttpClient http;
    private final ObjectMapper mapper;

    public ApiClient() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SEC))
                .build();
        this.mapper = new ObjectMapper();
    }

    // ── Verb methods ──────────────────────────────────────────────────────────

    /** HTTP GET — no request body. */
    public ApiCallResult get(String url) {
        log.info("→ GET {}", url);
        return execute(HttpRequest.newBuilder(uri(url))
                .timeout(Duration.ofSeconds(TIMEOUT_SEC))
                .header("Accept", JSON_TYPE)
                .GET()
                .build());
    }

    /** HTTP POST — sends {@code body} as JSON. */
    public ApiCallResult post(String url, String body) {
        log.info("→ POST {} | body={}", url, body);
        return execute(HttpRequest.newBuilder(uri(url))
                .timeout(Duration.ofSeconds(TIMEOUT_SEC))
                .header("Content-Type", JSON_TYPE)
                .header("Accept", JSON_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
    }

    /** HTTP PUT — sends {@code body} as JSON. */
    public ApiCallResult put(String url, String body) {
        log.info("→ PUT {} | body={}", url, body);
        return execute(HttpRequest.newBuilder(uri(url))
                .timeout(Duration.ofSeconds(TIMEOUT_SEC))
                .header("Content-Type", JSON_TYPE)
                .header("Accept", JSON_TYPE)
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build());
    }

    /** HTTP DELETE — no request body. */
    public ApiCallResult delete(String url) {
        log.info("→ DELETE {}", url);
        return execute(HttpRequest.newBuilder(uri(url))
                .timeout(Duration.ofSeconds(TIMEOUT_SEC))
                .header("Accept", JSON_TYPE)
                .DELETE()
                .build());
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    /**
     * Extracts the value of a top-level field from a JSON string.
     * Supports nested keys using dot-notation (e.g. {@code "address.city"}).
     *
     * @return field value as String, or {@code null} if not found
     */
    public String extractField(String json, String dotKey) {
        try {
            JsonNode node = mapper.readTree(json);
            for (String part : dotKey.split("\\.")) {
                if (node.isArray() && part.matches("\\d+")) {
                    node = node.get(Integer.parseInt(part));
                } else {
                    node = node.path(part);
                }
            }
            return node == null || node.isMissingNode() ? null : node.asText();
        } catch (Exception e) {
            log.warn("Could not parse field '{}' from JSON: {}", dotKey, e.getMessage());
            return null;
        }
    }

    public JsonNode parseJson(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            log.warn("Could not parse JSON body: {}", e.getMessage());
            return null;
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private ApiCallResult execute(HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("← HTTP {} ({} chars)", response.statusCode(), response.body().length());
            log.info("Response Body:\n{}", prettyPrint(response.body()));

            // Parse body for the recorder (stored as object, not raw string)
            Object parsedBody;
            try {
                parsedBody = mapper.readValue(response.body(), Object.class);
            } catch (Exception e) {
                parsedBody = response.body();
            }
            ApiResponseRecorder.getInstance().record(
                    request.method(),
                    request.uri().toString(),
                    response.statusCode(),
                    parsedBody);

            return new ApiCallResult(response.statusCode(), response.body(), null);
        } catch (Exception e) {
            log.error("Request failed: {}", e.getMessage());
            return new ApiCallResult(-1, null, e.getMessage());
        }
    }

    private static URI uri(String url) {
        return URI.create(url);
    }

    // ── Result type ───────────────────────────────────────────────────────────

    /** Immutable result of an HTTP call. */
    public record ApiCallResult(int status, String body, String error) {
        public boolean isOk() {
            return status >= 200 && status < 300;
        }

        public boolean hasFailed() {
            return error != null;
        }

        public boolean isEmpty() {
            return body == null || body.isBlank() || body.equals("{}") || body.equals("[]");
        }
    }

    // ── Pretty-print helper ───────────────────────────────────────────────────

    private String prettyPrint(String json) {
        try {
            Object parsed = mapper.readValue(json, Object.class);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
        } catch (Exception e) {
            return json; // not valid JSON — return raw
        }
    }
}
