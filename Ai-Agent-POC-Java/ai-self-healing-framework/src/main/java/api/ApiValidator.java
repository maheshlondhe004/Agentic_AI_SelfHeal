package api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/**
 * Validates an HTTP API endpoint:
 * <ol>
 * <li>Makes the HTTP call and captures status code + body</li>
 * <li>Asserts status == 200</li>
 * <li>Deep-compares the actual body against developer-provided expected
 * JSON</li>
 * </ol>
 *
 * <p>
 * Uses only {@code java.net.http.HttpClient} and Jackson — no extra libraries.
 * </p>
 */
public class ApiValidator {

    private static final Logger log = LoggerFactory.getLogger(ApiValidator.class);
    private static final int TIMEOUT_SEC = 30;

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public ApiValidator() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SEC))
                .build();
        this.mapper = new ObjectMapper();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Calls {@code url} with HTTP GET and returns the raw response.
     */
    public ApiCallResult call(String url) {
        log.info("API call → GET {}", url);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(TIMEOUT_SEC))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Response: HTTP {} ({} chars)", response.statusCode(), response.body().length());
            return new ApiCallResult(response.statusCode(), response.body(), null);

        } catch (Exception e) {
            log.error("API call failed: {}", e.getMessage());
            return new ApiCallResult(-1, null, e.getMessage());
        }
    }

    /**
     * Deep-compares {@code actualJson} against {@code expectedJson} loaded from
     * the classpath resource {@code resourceName}.
     *
     * <p>
     * Only fields present in the expected JSON are validated — extra fields in
     * the actual response are noted but do NOT fail the comparison (non-breaking
     * additions are allowed).
     * </p>
     *
     * @param actualJson   live response body
     * @param resourceName classpath path to the expected JSON (e.g.
     *                     {@code "expected-api-response.json"})
     * @return {@link BodyCompareResult} with matched flag and diff details
     */
    public BodyCompareResult compareBody(String actualJson, String resourceName) {
        try {
            // Load developer-provided expected
            String expectedJson = loadResource(resourceName);
            JsonNode expected = mapper.readTree(expectedJson);
            JsonNode actual = mapper.readTree(actualJson);

            Map<String, String[]> diff = new LinkedHashMap<>();
            compareNodes("", expected, actual, diff);

            if (diff.isEmpty()) {
                log.info("✅ API body matches expected data (all {} checked field(s) match).",
                        countLeafNodes(expected));
            } else {
                log.warn("❌ API body mismatch — {} field(s) differ:", diff.size());
                diff.forEach((k, v) -> log.warn("   [{}] expected={} | actual={}", k, v[0], v[1]));
            }
            return new BodyCompareResult(diff.isEmpty(), expectedJson, actualJson, diff);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load or parse expected JSON: " + e.getMessage(), e);
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    /** Recursively compares only the fields defined in {@code expected}. */
    private void compareNodes(String path, JsonNode expected, JsonNode actual,
            Map<String, String[]> diff) {
        if (expected == null)
            return;

        if (expected.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = expected.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> e = fields.next();
                String key = path.isEmpty() ? e.getKey() : path + "." + e.getKey();
                JsonNode child = actual != null ? actual.get(e.getKey()) : null;

                if (child == null) {
                    // Field missing from actual response
                    diff.put(key, new String[] { e.getValue().toString(), "<missing>" });
                } else {
                    compareNodes(key, e.getValue(), child, diff);
                }
            }
        } else {
            // Leaf node — compare values
            String expStr = expected.toString();
            String actStr = actual != null ? actual.toString() : "<missing>";
            if (!expStr.equals(actStr)) {
                diff.put(path, new String[] { expStr, actStr });
            }
        }
    }

    private String loadResource(String name) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(name)) {
            if (is == null)
                throw new IOException("Classpath resource not found: " + name);
            return new String(is.readAllBytes());
        }
    }

    private int countLeafNodes(JsonNode node) {
        if (node.isValueNode())
            return 1;
        int count = 0;
        for (JsonNode child : node)
            count += countLeafNodes(child);
        return count;
    }

    // ── Result types ──────────────────────────────────────────────────────────

    /** Raw result of an HTTP call. */
    public record ApiCallResult(int statusCode, String body, String errorMessage) {
        public boolean isSuccess() {
            return statusCode == 200;
        }

        public boolean hasFailed() {
            return errorMessage != null;
        }
    }

    /** Result of comparing actual vs. expected JSON body. */
    public record BodyCompareResult(boolean matched,
            String expectedJson,
            String actualJson,
            Map<String, String[]> diff) {
        /** Returns a human-readable diff summary. */
        public String diffSummary() {
            if (matched)
                return "All fields match.";
            StringBuilder sb = new StringBuilder();
            diff.forEach((k, v) -> sb.append(String.format("  [%s] expected=%s actual=%s%n", k, v[0], v[1])));
            return sb.toString().trim();
        }
    }
}
