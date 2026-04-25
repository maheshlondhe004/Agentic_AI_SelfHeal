package ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.RetryUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * ⭐ AI Logic — calls OpenAI (GPT-4o-mini) to suggest a new CSS
 * selector when a Playwright locator breaks.
 *
 * <h3>OpenAI specifics</h3>
 * <ul>
 * <li>API: {@value OPENAI_URL}</li>
 * <li>Auth: {@code Authorization: Bearer <OPENAI_API_KEY>}</li>
 * <li>Format: OpenAI Chat Completions (messages array)</li>
 * <li>Key env var: {@code OPENAI_API_KEY} (loaded from .env)</li>
 * </ul>
 *
 * <h3>Slow-response handling</h3>
 * <ul>
 * <li>Connect timeout: {@value CONNECT_TIMEOUT_SEC}s</li>
 * <li>Request timeout: {@value REQUEST_TIMEOUT_SEC}s (OpenAI can be
 * slow)</li>
 * <li>Automatic retry with exponential back-off via {@link RetryUtils}</li>
 * </ul>
 *
 * <h3>Response contract</h3>
 * GPT-4o-mini (via OpenAI) must return ONLY valid JSON:
 * 
 * <pre>
 * {"selector":"#newSelector","confidence":"HIGH|MEDIUM|LOW","reason":"one sentence"}
 * </pre>
 */
public class HealingAgent {

    private static final Logger log = LoggerFactory.getLogger(HealingAgent.class);

    // ── OpenAI endpoint ───────────────────────────────────────────────────
    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "gpt-4o-mini-2024-07-18";

    // ── Timeout config (generous — OpenAI can be slow) ───────────────────
    private static final int CONNECT_TIMEOUT_SEC = 15;
    private static final int REQUEST_TIMEOUT_SEC = 120; // 2 minutes
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long INITIAL_RETRY_DELAY = 3_000L; // 3 s
    private static final long MAX_RETRY_DELAY = 30_000L; // 30 s cap
    private static final int MAX_TOKENS = 512;

    // ── System prompt ─────────────────────────────────────────────────────────
    private static final String SYSTEM_PROMPT = """
            You are a QA automation AI agent specialised in self-healing Playwright test locators.
            You will receive:
            - elementId:           the logical name of the UI element to find
            - description:         what the element does
            - failedLocator:       the CSS selector that stopped working
            - interactiveElements: a JSON array of visible interactive elements on the page
            - domSnapshot:         partial HTML of the page body

            Your task:
            Analyse the DOM context and return the BEST CSS selector (preferred) or XPath to
            locate the desired element. Prioritise in this order:
              1. data-testid attribute
              2. aria-label attribute
              3. unique id
              4. name attribute
              5. specific CSS class + tag combination
              6. XPath as a last resort

            Respond with ONLY valid JSON matching this exact schema — no markdown, no prose:
            {"selector":"<new selector>","confidence":"HIGH|MEDIUM|LOW","reason":"<one sentence>"}
            """;

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    // ── Constructors ──────────────────────────────────────────────────────────

    public HealingAgent() {
        this(loadApiKey(), DEFAULT_MODEL);
    }

    public HealingAgent(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SEC))
                .build();
    }

    /** Package-private: injects a mock HttpClient for unit tests. */
    HealingAgent(String apiKey, HttpClient httpClient) {
        this.apiKey = apiKey;
        this.model = DEFAULT_MODEL;
        this.httpClient = httpClient;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Asks the AI for a new selector when {@code failedLocator} no longer works.
     * Retries up to {@value MAX_RETRY_ATTEMPTS} times with exponential back-off
     * to handle slow OpenRouter responses gracefully.
     *
     * @param elementId           logical element ID (from locators.json)
     * @param elementDescription  human-readable description
     * @param failedLocator       the selector that broke
     * @param interactiveElements JSON array of visible interactive elements
     * @param domSnapshot         truncated page body HTML
     * @return {@link HealingResult} containing the new selector and confidence
     */
    public HealingResult healLocator(String elementId,
            String elementDescription,
            String failedLocator,
            String interactiveElements,
            String domSnapshot) {

        log.info("AI healing requested for '{}' (failed locator: '{}')", elementId, failedLocator);

        return RetryUtils.retryWithBackoff(
                MAX_RETRY_ATTEMPTS,
                INITIAL_RETRY_DELAY,
                MAX_RETRY_DELAY,
                () -> callOpenAI(elementId, elementDescription,
                        failedLocator, interactiveElements, domSnapshot));
    }

    // ── Private: HTTP call ────────────────────────────────────────────────────

    private HealingResult callOpenAI(String elementId,
            String elementDescription,
            String failedLocator,
            String interactiveElements,
            String domSnapshot) {
        String userMessage = """
                elementId: %s
                description: %s
                failedLocator: %s
                interactiveElements: %s
                domSnapshot:
                %s
                """.formatted(elementId, elementDescription, failedLocator,
                interactiveElements, domSnapshot);

        try {
            // OpenAI-compatible chat completions format
            Map<String, Object> requestMap = Map.of(
                    "model", model,
                    "max_tokens", MAX_TOKENS,
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", userMessage)));
            String body = mapper.writeValueAsString(requestMap);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OPENAI_URL))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SEC))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            log.info("Sending request to OpenAI (model={}, timeout={}s)…",
                    model, REQUEST_TIMEOUT_SEC);
            long start = System.currentTimeMillis();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            long elapsed = System.currentTimeMillis() - start;
            log.info("OpenAI responded in {}ms (HTTP {})", elapsed, response.statusCode());
            log.info("OpenAI response body: {}", response.body());

            if (response.statusCode() != 200) {
                throw new RuntimeException(
                        "OpenAI returned HTTP " + response.statusCode()
                                + ". Body: " + response.body());
            }

            return parseResponse(response.body());

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("OpenAI call failed: " + e.getMessage(), e);
        }
    }

    // ── Response parsing ──────────────────────────────────────────────────────

    private HealingResult parseResponse(String rawBody) throws Exception {
        // OpenAI format: { "choices": [{ "message": { "content": "..." } }] }
        JsonNode root = mapper.readTree(rawBody);
        String aiText = root.path("choices").get(0)
                .path("message").path("content").asText("");

        log.debug("AI response content: {}", aiText);

        // Strip accidental markdown fences if present
        String cleaned = aiText.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("```[a-z]*\n?", "").replace("```", "").trim();
        }

        JsonNode json = mapper.readTree(cleaned);
        String selector = json.path("selector").asText("").trim();
        String confidence = json.path("confidence").asText("LOW");
        String reason = json.path("reason").asText("");

        if (selector.isBlank()) {
            throw new RuntimeException("AI returned an empty selector — cannot heal.");
        }
        log.info("Healed selector: '{}' (confidence={}) — {}", selector, confidence, reason);
        return new HealingResult(selector, confidence, reason);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String loadApiKey() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String key = dotenv.get("OPENAI_API_KEY",
                System.getenv().getOrDefault("OPENAI_API_KEY", ""));
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "OPENAI_API_KEY is missing. Set it in .env or as an environment variable.");
        }
        return key;
    }

    // ── Result record ─────────────────────────────────────────────────────────

    /** Immutable result returned by {@link #healLocator}. */
    public record HealingResult(String selector, String confidence, String reason) {
        public boolean isHighConfidence() {
            return "HIGH".equalsIgnoreCase(confidence);
        }
    }
}
