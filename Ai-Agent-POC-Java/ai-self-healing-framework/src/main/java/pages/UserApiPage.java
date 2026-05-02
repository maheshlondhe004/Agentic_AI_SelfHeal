package pages;

import api.ApiClient;
import api.ApiSelfHealer;
import api.healing.DynamicValidationEngine;
import api.healing.PostHealingOrchestrator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import config.ApiEndpoints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * Page Object Model for the User API (GET /login/:id).
 *
 * <h3>Responsibilities (all business logic lives here)</h3>
 * <ul>
 *   <li><strong>URL construction</strong> — builds the full endpoint URL from the configured base + userId</li>
 *   <li><strong>Request execution</strong> — delegates the raw HTTP GET to {@link ApiClient} and
 *       exposes the result via {@link #getLastResponse()} so the calling step definition can
 *       write it into the shared {@code ApiTestContext.lastResponse}.</li>
 *   <li><strong>Status validation</strong> — asserts HTTP 200</li>
 *   <li><strong>Field validation</strong> — validates "username" (mapped to {@code data.name}),
 *       "email" ({@code data.email}), and "id" ({@code data.userId}) against the live response</li>
 *   <li><strong>Schema comparison</strong> — deep-compares the actual response against the
 *       stored baseline in {@code user-api-expected-response.json}</li>
 *   <li><strong>Self-healing</strong> — when a mismatch is found, delegates to
 *       {@link ApiSelfHealer} to update the expected JSON, feature file, and step definitions</li>
 *   <li><strong>Dynamic file updates</strong> — after healing, persists the new baseline so
 *       subsequent runs use the healed state</li>
 * </ul>
 *
 * <p><strong>Cross-layer isolation:</strong> This class lives in {@code main/java} and must NOT
 * import test-scoped classes (e.g. {@code ApiTestContext}). The step definitions bridge the two
 * layers by reading {@link #getLastResponse()} and writing it into the shared test context.</p>
 */
public class UserApiPage {

    private static final Logger log = LoggerFactory.getLogger(UserApiPage.class);

    private static final String BASELINE_RESOURCE   = "user-api-expected-response.json";
    private static final String BASELINE_WRITE_PATH = "src/test/resources/user-api-expected-response.json";

    private final ApiClient    client;
    private final ObjectMapper mapper;
    private final DynamicValidationEngine dynamicValidationEngine;
    private final PostHealingOrchestrator postHealingOrchestrator;

    // Lazy-initialized — only created when self-healing is actually triggered.
    // This avoids an eager OPENAI_API_KEY lookup at object construction time,
    // which would fail in environments where the key is not yet configured.
    private ApiSelfHealer healer;

    // ── Per-scenario state ────────────────────────────────────────────────────
    private String               configuredBaseUrl;
    private String               currentUserId;
    private ApiClient.ApiCallResult lastResponse;
    private String               lastEndpointUrl;

    // Self-healing mismatch tracking
    private String               failedField;
    private String               expectedFieldValue;
    private String               actualFieldValue;
    private boolean              healingWasTriggered = false;
    private DynamicValidationEngine.ValidationResult lastContractValidation;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * Creates a UserApiPage that shares the given {@link ApiClient}.
     * Pass in {@code ctx.client} from the calling step definition so that
     * the response recorder is the same instance used across the scenario.
     *
     * @param client the HTTP client to use for all GET calls
     */
    public UserApiPage(ApiClient client) {
        this.client = client;
        this.mapper = new ObjectMapper();
        this.dynamicValidationEngine = new DynamicValidationEngine();
        this.postHealingOrchestrator = new PostHealingOrchestrator();
    }

    // =========================================================================
    // 1. URL LOADING / ENDPOINT CONFIGURATION
    // =========================================================================

    /**
     * Reads the base URL from {@link ApiEndpoints#BASE_URL} (overridable via
     * {@code -Dapi.base.url=...} at runtime).
     *
     * <p>Called by the <em>Background</em> step so that every scenario in the
     * feature starts with a properly configured endpoint.</p>
     *
     * @return the configured base URL (also stored internally)
     */
    public String configureEndpoint() {
        this.configuredBaseUrl = ApiEndpoints.BASE_URL;
        log.info("✅ User API endpoint configured: {}", configuredBaseUrl);
        return configuredBaseUrl;
    }

    // =========================================================================
    // 2. REQUEST EXECUTION
    // =========================================================================

    /**
     * Builds the full URL {@code <base>/login/<userId>} and fires an HTTP GET.
     *
     * <p>The raw {@link ApiClient.ApiCallResult} is stored internally and
     * accessible via {@link #getLastResponse()} and {@link #getLastEndpointUrl()}.
     * The calling step definition is responsible for writing it into the shared
     * {@code ApiTestContext.lastResponse} so that all Cucumber shared steps can
     * read the same response object.</p>
     *
     * @param userId the path parameter (e.g. "1", "2", "3")
     */
    public void executeGetUserById(String userId) {
        this.currentUserId = userId;
        this.lastEndpointUrl = configuredBaseUrl + ApiEndpoints.loginById(userId);

        log.info("→ Executing GET {} for userId={}", lastEndpointUrl, userId);
        this.lastResponse = client.get(lastEndpointUrl);

        if (lastResponse.hasFailed()) {
            log.error("HTTP call failed: {}", lastResponse.error());
        } else {
            log.info("← HTTP {} received for userId={}", lastResponse.status(), userId);
        }
    }

    // =========================================================================
    // 3. RESPONSE VALIDATION — STATUS CODE
    // =========================================================================

    /**
     * Validates the HTTP status code equals the expected value.
     *
     * @param expectedStatus expected HTTP status (e.g. 200)
     * @throws AssertionError if the status does not match
     */
    public void validateStatusCode(int expectedStatus) {
        requireResponse();
        int actual = lastResponse.status();
        if (actual != expectedStatus) {
            log.error("Status mismatch — expected={} actual={} body={}",
                    expectedStatus, actual, lastResponse.body());
            throw new AssertionError(
                    String.format("Expected HTTP %d but got HTTP %d. Body: %s",
                            expectedStatus, actual, lastResponse.body()));
        }
        log.info("✅ Status code validated: HTTP {}", actual);
    }

    // =========================================================================
    // 4. RESPONSE VALIDATION — FIELD CONTENT
    // =========================================================================

    /**
     * Validates that a named field is present and non-empty in the response.
     *
     * <p>The {@code logicalFieldName} is the name used in the feature file
     * (e.g. "username"). It is resolved through the schema-driven alias and
     * fallback metadata before extraction.</p>
     *
     * <p>On failure, the mismatch is recorded so
     * {@link #triggerSelfHealingIfMismatchDetected()} can invoke the LLM healer.</p>
     *
     * @param logicalFieldName logical field name from the feature file
     * @throws AssertionError if the field is missing or blank
     */
    public void validateFieldHasValidData(String logicalFieldName) {
        requireResponse();

        String actualValue = dynamicValidationEngine.extractDynamicField(
                lastEndpointUrl,
                "GET",
                lastResponse.body(),
                logicalFieldName);

        log.info("Validating field '{}' via schema-driven lookup — actual='{}'", logicalFieldName, actualValue);

        if (actualValue == null || actualValue.isBlank()) {
            // Record mismatch details for the self-healer
            this.failedField        = logicalFieldName;
            this.expectedFieldValue = "<non-empty>";
            this.actualFieldValue   = actualValue == null ? "<missing>" : "<blank>";

            log.error("Field validation failed — field='{}' actual='{}'", logicalFieldName, actualValue);
            throw new AssertionError(
                    String.format("Field '%s' is missing or blank in response: %s",
                            logicalFieldName, lastResponse.body()));
        }
        log.info("✅ Field '{}' validated — value='{}'", logicalFieldName, actualValue);
    }

    /**
     * Generic contract validation step used by stabilized feature files.
     *
     * <p>This method intentionally records mismatches instead of failing
     * immediately so the existing self-healing step can run after the body
     * mismatch is detected.</p>
     */
    public void validateResponseAgainstExpectedJsonContract() {
        requireResponse();
        lastContractValidation = dynamicValidationEngine.validate(
                lastEndpointUrl,
                "GET",
                BASELINE_RESOURCE,
                lastResponse.body());

        if (lastContractValidation.matched()) {
            log.info("✅ Generic expected-JSON contract matched for userId={}", currentUserId);
            return;
        }

        failedField = lastContractValidation.firstFailedField();
        String[] values = lastContractValidation.diff().get(failedField);
        expectedFieldValue = values[0];
        actualFieldValue = values[1];

        log.warn("⚠️  Contract mismatch detected before healing — field='{}' expected={} actual={}",
                failedField, expectedFieldValue, actualFieldValue);
    }

    // =========================================================================
    // 5. SCHEMA COMPARISON & AUTO-HEALING
    // =========================================================================

    /**
     * Compares the live response against the stored baseline in
     * {@code user-api-expected-response.json}.
     *
     * <ul>
     *   <li>If the baseline entry for the current userId is found, a deep field-level
     *       diff is performed.</li>
     *   <li>If a mismatch is found the self-healer is invoked immediately so the
     *       baseline, feature file, and step definitions are patched before the
     *       scenario ends.</li>
     *   <li>If no baseline entry exists yet, the live response is written as the
     *       initial baseline (first-run bootstrap).</li>
     * </ul>
     *
     * <p>This method does NOT throw — schema drift is treated as auto-fixable,
     * not a hard failure.</p>
     */
    public void autoHealSchemaIfChanged() {
        requireResponse();

        try {
            List<Map<String, Object>> baseline = loadBaseline();
            String endpointPath = ApiEndpoints.loginById(currentUserId);
            String actualJson = lastResponse.body();

            if (lastContractValidation == null) {
                validateResponseAgainstExpectedJsonContract();
            }

            DynamicValidationEngine.ValidationResult validation = lastContractValidation;

            if (validation == null || validation.matched()) {
                log.info("✅ Schema check passed — response matches baseline for userId={}", currentUserId);
                return;
            }

            Map<String, String[]> diff = validation.diff();

            if (diff.isEmpty()) {
                log.info("✅ Schema check passed — response matches baseline for userId={}", currentUserId);
            } else {
                log.warn("⚕️  Schema drift detected for userId={} — {} field(s) differ:", currentUserId, diff.size());
                diff.forEach((k, v) -> log.warn("   [{}] expected={} | actual={}", k, v[0], v[1]));

                // Invoke the LLM-powered healer (lazy — only created if needed)
                boolean healed = getHealer().heal(
                        lastEndpointUrl,
                        actualJson,
                        buildBodyCompareResult(validation.expectedJson(), actualJson, diff));

                if (healed) {
                    healingWasTriggered = true;
                    postHealingOrchestrator.reconcile(getHealer().getLastHealingSession());
                    updateBaselineEntry(baseline, endpointPath, actualJson);
                    DynamicValidationEngine.ValidationResult postHealValidation = dynamicValidationEngine.validate(
                            lastEndpointUrl,
                            "GET",
                            BASELINE_RESOURCE,
                            actualJson);
                    lastContractValidation = postHealValidation;
                    if (!postHealValidation.matched()) {
                        throw new AssertionError("Post-healing validation still shows mismatches: " + postHealValidation.diff());
                    }
                    log.info("✅ Baseline updated for userId={}", currentUserId);
                } else {
                    log.warn("⚕️  Healing not applied for userId={} — manual review may be needed.", currentUserId);
                }
            }
        } catch (Exception e) {
            log.error("Schema auto-healing failed: {}", e.getMessage(), e);
        }
    }

    // =========================================================================
    // 6. DYNAMIC FEATURE FILE & STEP DEFINITION UPDATES
    // =========================================================================

    /**
     * Triggers an LLM-powered patch of the feature file and step definitions
     * if a field mismatch was recorded during {@link #validateFieldHasValidData}.
     *
     * <p>This separates the concern of <em>detecting</em> a mismatch (done in the
     * Then step) from <em>healing</em> it (done in this And step / After hook),
     * keeping step definitions focused on single actions.</p>
     */
    public void triggerSelfHealingIfMismatchDetected() {
        if (failedField == null) {
            log.info("No field mismatch recorded — feature file / step definition update not required.");
            return;
        }

        log.warn("⚕️  Field mismatch — triggering self-healing: field='{}' expected='{}' actual='{}'",
                failedField, expectedFieldValue, actualFieldValue);

        String body = lastResponse != null ? lastResponse.body() : "{}";
        String url  = lastEndpointUrl != null ? lastEndpointUrl : configuredBaseUrl;

        getHealer().healFeatureAssertion(url, body, failedField, expectedFieldValue, actualFieldValue);

        if (getHealer().wasHealingPerformed()) {
            healingWasTriggered = true;
            log.info("✅ Feature file / step definitions updated. Re-run: mvn test -Dapi.base.url={}",
                    configuredBaseUrl);
        } else {
            log.info("LLM returned no feature/step updates for mismatch on field '{}'.", failedField);
        }
    }

    // =========================================================================
    // Public state accessors (read by step definitions and hooks)
    // =========================================================================

    /** Returns the raw HTTP response from the most recent GET call. */
    public ApiClient.ApiCallResult getLastResponse()   { return lastResponse; }

    /** Returns the full URL of the most recent GET call. */
    public String getLastEndpointUrl()                 { return lastEndpointUrl; }

    /** Returns the field name that failed validation (null if none). */
    public String getFailedField()                     { return failedField; }

    /** Returns the expected value that was checked (null if none). */
    public String getExpectedFieldValue()              { return expectedFieldValue; }

    /** Returns the actual value returned by the API (null if none). */
    public String getActualFieldValue()                { return actualFieldValue; }

    /** Returns true if self-healing was performed during this scenario. */
    public boolean wasHealingTriggered()               { return healingWasTriggered; }

    /** Resets per-scenario state (called in @Before). */
    public void reset() {
        currentUserId       = null;
        lastResponse        = null;
        lastEndpointUrl     = null;
        failedField         = null;
        expectedFieldValue  = null;
        actualFieldValue    = null;
        healingWasTriggered = false;
        lastContractValidation = null;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private void requireResponse() {
        if (lastResponse == null) {
            throw new IllegalStateException("No HTTP response — did you call executeGetUserById() first?");
        }
        if (lastResponse.hasFailed()) {
            throw new IllegalStateException("HTTP call failed: " + lastResponse.error());
        }
    }

    /**
     * Lazily creates {@link ApiSelfHealer} on first use so that the OPENAI_API_KEY
     * is only required when self-healing is actually triggered, not at object
     * construction time (which would fail in environments without the key).
     */
    private ApiSelfHealer getHealer() {
        if (healer == null) {
            healer = new ApiSelfHealer();
        }
        return healer;
    }

    // ── Baseline loading / saving ─────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadBaseline() throws IOException {
        // Try classpath resource first (fast path during normal test runs)
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(BASELINE_RESOURCE)) {
            if (is != null) {
                return mapper.readValue(is.readAllBytes(), List.class);
            }
        }
        // Fall back to filesystem path (needed after the healer writes an update)
        Path fsPath = Paths.get(BASELINE_WRITE_PATH);
        if (Files.exists(fsPath)) {
            return mapper.readValue(Files.readString(fsPath, StandardCharsets.UTF_8), List.class);
        }
        return new ArrayList<>();
    }

    private void bootstrapBaseline(List<Map<String, Object>> baseline, String endpointPath)
            throws IOException {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("url",            endpointPath);
        entry.put("method",         "GET");
        entry.put("expectedStatus", 200);
        entry.put("responseBody",   mapper.readTree(lastResponse.body()));
        baseline.add(entry);
        writeBaseline(baseline);
        log.info("✅ Baseline bootstrapped for {}", endpointPath);
    }

    private void updateBaselineEntry(List<Map<String, Object>> baseline, String endpointPath,
            String newBody) throws IOException {
        for (Map<String, Object> entry : baseline) {
            if (endpointPath.equals(entry.get("url")) && "GET".equals(entry.get("method"))) {
                entry.put("responseBody", mapper.readTree(newBody));
                writeBaseline(baseline);
                return;
            }
        }
        bootstrapBaseline(baseline, endpointPath);
    }

    private void writeBaseline(List<Map<String, Object>> baseline) throws IOException {
        Path path = Paths.get(BASELINE_WRITE_PATH);
        Files.createDirectories(path.getParent());
        String pretty = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(baseline);
        Files.writeString(path, pretty, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        log.info("Baseline written → {}", path.toAbsolutePath());
    }

    private api.ApiValidator.BodyCompareResult buildBodyCompareResult(
            String expectedJson, String actualJson, Map<String, String[]> diff) {
        return new api.ApiValidator.BodyCompareResult(
                diff.isEmpty(), expectedJson, actualJson, diff);
    }
}
