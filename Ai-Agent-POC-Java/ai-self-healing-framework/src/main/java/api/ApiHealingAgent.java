package api;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Calls OpenAI LLM to analyze an API response diff and suggest healing
 * actions.
 *
 * <p>
 * When the live API response no longer matches the stored expected body, this
 * agent:
 * </p>
 * <ol>
 * <li>Sends the endpoint URL, expected JSON, actual JSON, and diff to the
 * LLM</li>
 * <li>Receives an {@link ApiHealingResult} containing the updated expected JSON
 * and a list of Cucumber feature file step replacements</li>
 * </ol>
 *
 * <p>
 * Uses {@code gpt-4o-mini-2024-07-18} via OpenAI with 120 s timeout
 * and 3-attempt exponential back-off.
 * </p>
 */
public class ApiHealingAgent {

    private static final Logger log = LoggerFactory.getLogger(ApiHealingAgent.class);

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-4o-mini-2024-07-18";
    private static final int CONNECT_TIMEOUT = 15;
    private static final int REQUEST_TIMEOUT = 120;
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_DELAY = 3_000L;
    private static final long MAX_DELAY = 30_000L;

    private static final String SYSTEM_PROMPT = """
            You are an API test self-healing agent.
            The live API response body no longer matches the stored expected test data.
            Your task:
            1. Analyze what changed between the expected and actual response.
            2. Assume the change is a VALID API evolution (not a bug) unless clearly broken.
            3. Return the updated expected JSON that should be stored going forward.
            4. List any Cucumber feature file step assertions that need updating
               (e.g. if a field value or field name changed).
            5. List any Java step definition code that needs updating
               (e.g. if field names in assertions need to change).

            Respond ONLY with valid JSON — no markdown fences, no extra text:
            {
              "updatedExpectedJson": { ...complete new expected object... },
              "featureFileUpdates": [
                { "oldStep": "exact old step line", "newStep": "updated step line" }
              ],
              "stepDefinitionUpdates": [
                { "filePath": "relative/path/to/StepDefs.java", "oldCode": "exact old code block", "newCode": "updated code block" }
              ],
              "reasoning": "one sentence"
            }
            If no feature file or step definition changes are needed, return empty arrays.
            """;

    private final String apiKey;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public ApiHealingAgent() {
        this.apiKey = loadApiKey();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT))
                .build();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Analyses the mismatch and returns healing instructions.
     *
     * @param endpointUrl  API endpoint that was tested
     * @param actualJson   live response body
     * @param expectedJson stored expected body
     * @param diffSummary  human-readable diff produced by {@link ApiValidator}
     */
    public ApiHealingResult analyze(String endpointUrl,
            String actualJson,
            String expectedJson,
            String diffSummary) {
        log.info("Sending mismatch to OpenAI for healing analysis (endpoint={})", endpointUrl);
        return RetryUtils.retryWithBackoff(MAX_RETRIES, INITIAL_DELAY, MAX_DELAY,
                () -> callOpenAI(endpointUrl, actualJson, expectedJson, diffSummary));
    }

    // ── Private: HTTP call ────────────────────────────────────────────────────

    private ApiHealingResult callOpenAI(String url, String actual, String expected, String diff) {
        String userMsg = """
                Endpoint: %s

                Expected (stored):
                %s

                Actual (live):
                %s

                Mismatched fields:
                %s
                """.formatted(url, expected, actual, diff);

        try {
            String body = mapper.writeValueAsString(Map.of(
                    "model", MODEL,
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", userMsg))));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OPENAI_URL))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            long start = System.currentTimeMillis();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("OpenAI responded in {}ms (HTTP {})",
                    System.currentTimeMillis() - start, response.statusCode());

            if (response.statusCode() != 200) {
                throw new RuntimeException("OpenRouter HTTP " + response.statusCode()
                        + ": " + response.body());
            }
            log.info("LLM Raw Response: {}", response.body());
            return parseResponse(response.body());

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("OpenAI call failed: " + e.getMessage(), e);
        }
    }

    private ApiHealingResult parseResponse(String rawBody) throws Exception {
        String content = mapper.readTree(rawBody)
                .path("choices").get(0)
                .path("message").path("content").asText("");

        // Strip markdown fences if present
        String cleaned = content.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("```[a-z]*\n?", "").replace("```", "").trim();
        }

        JsonNode root = mapper.readTree(cleaned);

        String updatedJson = mapper.writeValueAsString(root.path("updatedExpectedJson"));
        String reasoning = root.path("reasoning").asText("");

        List<ApiHealingResult.FeatureFileUpdate> featureUpdates = new ArrayList<>();
        JsonNode featureArr = root.path("featureFileUpdates");
        if (featureArr.isArray()) {
            for (JsonNode node : featureArr) {
                featureUpdates.add(new ApiHealingResult.FeatureFileUpdate(
                        node.path("oldStep").asText(""),
                        node.path("newStep").asText("")));
            }
        }

        List<ApiHealingResult.StepDefinitionUpdate> stepUpdates = new ArrayList<>();
        JsonNode stepArr = root.path("stepDefinitionUpdates");
        if (stepArr.isArray()) {
            for (JsonNode node : stepArr) {
                stepUpdates.add(new ApiHealingResult.StepDefinitionUpdate(
                        node.path("filePath").asText(""),
                        node.path("oldCode").asText(""),
                        node.path("newCode").asText("")));
            }
        }

        log.info("Healing reasoning: {}", reasoning);
        return new ApiHealingResult(updatedJson, featureUpdates, stepUpdates, reasoning);
    }

    // ── Result type ───────────────────────────────────────────────────────────

    /** Healing instructions returned by the LLM. */
    public record ApiHealingResult(
            String updatedExpectedJson,
            List<FeatureFileUpdate> featureFileUpdates,
            List<StepDefinitionUpdate> stepDefinitionUpdates,
            String reasoning) {

        public record FeatureFileUpdate(String oldStep, String newStep) {
        }

        public record StepDefinitionUpdate(String filePath, String oldCode, String newCode) {
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String loadApiKey() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String key = dotenv.get("OPENAI_API_KEY",
                System.getenv().getOrDefault("OPENAI_API_KEY", ""));
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "OPENAI_API_KEY missing. Set it in .env or as an environment variable.");
        }
        return key;
    }
}
