package api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the API self-healing loop:
 * <ol>
 * <li>Detects mismatch via {@link ApiValidator}</li>
 * <li>Calls {@link ApiHealingAgent} (OpenRouter) for instructions</li>
 * <li>Persists updated {@code expected-api-response.json}</li>
 * <li>Patches the Cucumber feature file with new step text</li>
 * <li>Returns {@code true} if healing succeeded (callers can re-compare
 * in-place)</li>
 * </ol>
 */
public class ApiSelfHealer {

    private static final Logger log = LoggerFactory.getLogger(ApiSelfHealer.class);

    private static final String EXPECTED_JSON_PATH = "src/test/resources/expected-api-response.json";
    private static final String FEATURE_FILE_PATH = "src/test/resources/features/login-api.feature";

    private final ApiHealingAgent agent;
    private final ApiValidator validator;
    private final ObjectMapper mapper = new ObjectMapper();

    private boolean healingPerformed = false;

    public ApiSelfHealer() {
        this.agent = new ApiHealingAgent();
        this.validator = new ApiValidator();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Heals {@code expected-api-response.json} and the Cucumber feature file when
     * the live {@code actualBody} doesn't match the stored expected data.
     *
     * @param endpointUrl   the API URL that was tested
     * @param actualBody    the live HTTP response body
     * @param compareResult the diff from {@link ApiValidator#compareBody}
     * @return {@code true} if healing succeeded and the expected JSON was updated
     */
    public boolean heal(String endpointUrl,
            String actualBody,
            ApiValidator.BodyCompareResult compareResult) {
        if (compareResult.matched()) {
            log.debug("No mismatch — healing not needed.");
            return false;
        }

        log.warn("⚕️  Body mismatch detected for [{}]. Invoking LLM self-healer…", endpointUrl);
        log.warn("Diff:\n{}", compareResult.diffSummary());

        try {
            ApiHealingAgent.ApiHealingResult result = agent.analyze(
                    endpointUrl,
                    actualBody,
                    compareResult.expectedJson(),
                    compareResult.diffSummary());

            // 1. Update the expected JSON file on disk
            if (result.updatedExpectedJson() != null
                    && !result.updatedExpectedJson().isBlank()
                    && !result.updatedExpectedJson().equals("null")) {
                persistExpectedJson(result.updatedExpectedJson(), endpointUrl);
            }

            // 2. Patch the Cucumber feature file
            if (!result.featureFileUpdates().isEmpty()) {
                patchFeatureFile(result.featureFileUpdates());
            }

            // 3. Patch the step definition files
            if (!result.stepDefinitionUpdates().isEmpty()) {
                patchStepDefinitionFiles(result.stepDefinitionUpdates());
            }

            healingPerformed = true;
            log.info("✅ Self-healing complete. Reasoning: {}", result.reasoning());
            return true;

        } catch (Exception e) {
            log.error("Self-healing failed: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Heals from a Cucumber field assertion failure.
     * Called by {@link steps.HealingHooks} after a failed scenario.
     *
     * @param endpointUrl the URL that was called
     * @param actualBody  the actual response body from the last request
     * @param failedField dot-path of the field that didn't match (e.g.
     *                    {@code data.username})
     * @param expected    the expected value
     * @param actual      the actual value returned
     */
    public void healFeatureAssertion(String endpointUrl,
            String actualBody,
            String failedField,
            String expected,
            String actual) {
        log.warn("⚕️  Cucumber field mismatch — field='{}' expected='{}' actual='{}'",
                failedField, expected, actual);

        String fakeExpected = String.format("{\"%s\":\"%s\"}", failedField, expected);
        String fakeDiff = String.format("  [%s] expected=%s | actual=%s", failedField, expected, actual);

        try {
            ApiHealingAgent.ApiHealingResult result = agent.analyze(
                    endpointUrl, actualBody, fakeExpected, fakeDiff);

            if (!result.featureFileUpdates().isEmpty()) {
                patchFeatureFile(result.featureFileUpdates());
                healingPerformed = true;
            }

            if (!result.stepDefinitionUpdates().isEmpty()) {
                patchStepDefinitionFiles(result.stepDefinitionUpdates());
                healingPerformed = true;
            }

            if (healingPerformed) {
                log.info("✅ Feature/step definitions patched. Re-run: mvn test -Dtest.api.url={}",
                        System.getProperty("test.api.url", "http://localhost:3000"));
            } else {
                log.info("LLM returned no updates. Reasoning: {}", result.reasoning());
            }
        } catch (Exception e) {
            log.error("Feature file healing failed: {}", e.getMessage(), e);
        }
    }

    public boolean wasHealingPerformed() {
        return healingPerformed;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void persistExpectedJson(String updatedJson, String endpointUrl) throws IOException {
        Path path = Paths.get(EXPECTED_JSON_PATH);
        Files.createDirectories(path.getParent());

        // Extract url and method
        String url = endpointUrl.replace("http://localhost:3000", "").replace("https://localhost:3000", "");
        String method = "GET"; // Assume GET for now

        // Load current expected as list
        List<Map<String, Object>> expectedList = new ArrayList<>();
        if (Files.exists(path)) {
            expectedList = mapper.readValue(Files.readString(path, StandardCharsets.UTF_8), List.class);
        }

        // Find or add entry
        boolean found = false;
        for (Map<String, Object> entry : expectedList) {
            if (url.equals(entry.get("url")) && method.equals(entry.get("method"))) {
                entry.put("responseBody", mapper.readTree(updatedJson));
                found = true;
                break;
            }
        }
        if (!found) {
            Map<String, Object> newEntry = new HashMap<>();
            newEntry.put("url", url);
            newEntry.put("method", method);
            newEntry.put("expectedStatus", 200);
            newEntry.put("responseBody", mapper.readTree(updatedJson));
            expectedList.add(newEntry);
        }

        // Pretty-print and write
        String pretty = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(expectedList);
        Files.writeString(path, pretty, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
        log.info("Updated expected JSON → {}", path.toAbsolutePath());
    }

    private void patchStepDefinitionFiles(List<ApiHealingAgent.ApiHealingResult.StepDefinitionUpdate> updates)
            throws IOException {
        for (ApiHealingAgent.ApiHealingResult.StepDefinitionUpdate update : updates) {
            Path path = Paths.get("src/test/java", update.filePath());
            if (!Files.exists(path)) {
                log.warn("Step definition file not found at: {}", path.toAbsolutePath());
                continue;
            }

            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (content.contains(update.oldCode())) {
                content = content.replace(update.oldCode(), update.newCode());
                Files.writeString(path, content, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                log.info("Step definition file patched: {} → {}", update.filePath(), path.toAbsolutePath());
            } else {
                log.warn("Could not find old code to patch in {}: '{}'", update.filePath(), update.oldCode());
            }
        }
    }

    private void patchFeatureFile(List<ApiHealingAgent.ApiHealingResult.FeatureFileUpdate> updates)
            throws IOException {
        for (ApiHealingAgent.ApiHealingResult.FeatureFileUpdate update : updates) {
            Path path = Paths.get(FEATURE_FILE_PATH);
            if (!Files.exists(path)) {
                log.warn("Feature file not found at: {}", path.toAbsolutePath());
                continue;
            }

            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (content.contains(update.oldStep())) {
                content = content.replace(update.oldStep(), update.newStep());
                Files.writeString(path, content, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                log.info("Feature file patched: {} → {}", update.oldStep(), update.newStep());
            } else {
                log.warn("Could not find old step to patch in {}: '{}'", FEATURE_FILE_PATH, update.oldStep());
            }
        }
    }
}
