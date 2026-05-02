package api;

import api.healing.PostHealingOrchestrator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * Orchestrates the complete API testing workflow according to the specification:
 *
 * <ol>
 * <li><strong>Initiate API Testing</strong> — Execute all configured endpoints</li>
 * <li><strong>Validate Response Status</strong> — Mark as failed if status ≠ 200</li>
 * <li><strong>Validate Response Body</strong> — Only for 200 status</li>
 * <li><strong>Check Stored Baseline</strong> — Save if first-time execution</li>
 * <li><strong>Compare with Baseline</strong> — Validate against stored data</li>
 * <li><strong>Self-Healing on Mismatch</strong> — Invoke AI agent for updates</li>
 * <li><strong>Re-run Validation</strong> — Confirm healing success</li>
 * <li><strong>Report Results</strong> — Summary of all tests</li>
 * </ol>
 */
public class ApiTestOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ApiTestOrchestrator.class);

    private final ApiClient client;
    private final ApiValidator validator;
    private final ApiSelfHealer selfHealer;
    private final PostHealingOrchestrator postHealingOrchestrator;
    private final BaselineManager baselineManager;
    private final ObjectMapper mapper;

    private final List<EndpointTestResult> results = new ArrayList<>();
    private final Map<String, Integer> healingAttempts = new HashMap<>();
    private static final int MAX_HEALING_ATTEMPTS = 3;

    public ApiTestOrchestrator() {
        this.client = new ApiClient();
        this.validator = new ApiValidator();
        this.selfHealer = new ApiSelfHealer();
        this.postHealingOrchestrator = new PostHealingOrchestrator();
        this.baselineManager = new BaselineManager();
        this.mapper = new ObjectMapper();
        ApiResponseRecorder.getInstance().reset();
        healingAttempts.clear();
    }

    /**
     * Executes the complete test workflow for a single endpoint.
     *
     * @param url the endpoint URL to test
     * @return result object with pass/fail status and details
     */
    public EndpointTestResult testEndpoint(String url) {
        log.info("");
        log.info("═════════════════════════════════════════════════════════");
        log.info("Testing Endpoint: {}", url);
        log.info("═════════════════════════════════════════════════════════");

        EndpointTestResult result = new EndpointTestResult(url);

        try {
            // ─────────────────────────────────────────────────────────
            // STEP 1: Call the API
            // ─────────────────────────────────────────────────────────
            log.info("STEP 1: Initiating API call...");
            ApiClient.ApiCallResult response = client.get(url);

            if (response.hasFailed()) {
                result.setStatus(TestStatus.FAILED);
                result.setReason("Network error: " + response.error());
                log.error("❌ API call failed: {}", response.error());
                results.add(result);
                return result;
            }

            result.setResponseStatus(response.status());
            result.setResponseBody(response.body());

            // ─────────────────────────────────────────────────────────
            // STEP 2: Validate Response Status
            // ─────────────────────────────────────────────────────────
            log.info("STEP 2: Validating response status...");
            if (response.status() != 200) {
                result.setStatus(TestStatus.FAILED);
                result.setReason(String.format("HTTP %d (expected 200)", response.status()));
                log.error("❌ Expected HTTP 200 but got HTTP {}", response.status());
                results.add(result);
                return result; // Stop here, don't validate body
            }
            log.info("✅ HTTP 200 OK");

            // ─────────────────────────────────────────────────────────
            // STEP 3: Check Stored Baseline
            // ─────────────────────────────────────────────────────────
            String endpointKey = generateEndpointKey(url);
            log.info("STEP 3: Checking stored baseline...");

            if (!baselineManager.baselineExists(endpointKey)) {
                log.info("ℹ️  First-time execution — saving response as baseline");
                // Normalize the response for baseline
                String normalizedResponse = normalizeResponse(response.body());
                baselineManager.saveBaseline(endpointKey, normalizedResponse);
                result.setStatus(TestStatus.PASSED);
                result.setReason("Baseline created (first-time execution)");
                result.setBaselineNew(true);
                log.info("✅ Baseline saved");
                results.add(result);
                return result;
            }
            log.info("✅ Baseline exists, proceeding with comparison");

            // ─────────────────────────────────────────────────────────
            // STEP 4: Compare with Existing Baseline
            // ─────────────────────────────────────────────────────────
            log.info("STEP 4: Comparing response with baseline...");
            String baseline = baselineManager.loadBaseline(endpointKey);

            // Normalize both responses for comparison (handle dynamic fields)
            String normalizedBaseline = normalizeResponseForComparison(baseline);
            String normalizedActual = normalizeResponseForComparison(response.body());

            // Use inline comparison (basic field-level checking)
            Map<String, String[]> diff = compareResponses(normalizedBaseline, normalizedActual);

            if (diff.isEmpty()) {
                result.setStatus(TestStatus.PASSED);
                result.setReason("Response matches baseline");
                log.info("✅ Response matches baseline — no healing needed");
                results.add(result);
                return result;
            }

            log.warn("⚠️  Response mismatch detected:");
            diff.forEach((k, v) -> log.warn("   [{}] expected={} | actual={}", k, v[0], v[1]));
            result.setDiff(diff);
            log.info("📝 Original failure logged for endpoint: {}", url);

            // ─────────────────────────────────────────────────────────
            // STEP 5: Trigger Self-Healing on Mismatch
            // ─────────────────────────────────────────────────────────
            log.info("STEP 5: Triggering AI self-healing...");

            int attempts = healingAttempts.getOrDefault(endpointKey, 0);
            if (attempts >= MAX_HEALING_ATTEMPTS) {
                result.setStatus(TestStatus.FAILED);
                result.setReason("Maximum healing attempts exceeded (" + MAX_HEALING_ATTEMPTS + ")");
                log.error("❌ Maximum healing attempts exceeded for endpoint: {}", url);
                results.add(result);
                return result;
            }

            healingAttempts.put(endpointKey, attempts + 1);

            // Create a BodyCompareResult for the self-healer
            ApiValidator.BodyCompareResult compareResult = new ApiValidator.BodyCompareResult(
                    false, baseline, response.body(), diff);

            boolean healed = selfHealer.heal(url, response.body(), compareResult);

            if (!healed) {
                result.setStatus(TestStatus.FAILED);
                result.setReason("Self-healing failed");
                log.error("❌ Self-healing did not succeed");
                results.add(result);
                return result;
            }

            log.info("✅ Self-healing applied (attempt {}/{})", attempts + 1, MAX_HEALING_ATTEMPTS);
            result.setHealed(true);
            log.info("🔧 Healing changes made: baseline updated, feature files patched");

            // Update baseline to the healed response (normalized)
            String healedNormalizedResponse = normalizeResponseForComparison(response.body());
            baselineManager.saveBaseline(endpointKey, healedNormalizedResponse);

            // ─────────────────────────────────────────────────────────
            // STEP 6: Run JSON Diff (old vs new) + update downstream layers
            // ─────────────────────────────────────────────────────────
            log.info("STEP 6: Running post-heal JSON diff and schema reconciliation...");
            PostHealingOrchestrator.HealingExtensionReport extensionReport =
                    postHealingOrchestrator.reconcile(selfHealer.getLastHealingSession());
            log.info("STEP 7: Schema SSOT synchronized");
            log.info("STEP 8: Dependency analysis completed — {} usage(s) discovered",
                    extensionReport.dependencyGraph().usageCount());
            log.info("STEP 9: AST/Gherkin refactoring {}",
                    extensionReport.changedAnything() ? "applied where required" : "skipped (no downstream changes required)");

            // ─────────────────────────────────────────────────────────
            // STEP 10: Re-run API Call After Healing
            // ─────────────────────────────────────────────────────────
            log.info("STEP 10: Re-running API call after healing...");
            ApiClient.ApiCallResult rerunResponse = client.get(url);

            if (rerunResponse.hasFailed()) {
                result.setStatus(TestStatus.FAILED);
                result.setReason("Network error on rerun: " + rerunResponse.error());
                log.error("❌ Rerun API call failed: {}", rerunResponse.error());
                results.add(result);
                return result;
            }

            if (rerunResponse.status() != 200) {
                result.setStatus(TestStatus.FAILED);
                result.setReason(String.format("HTTP %d on rerun (expected 200)", rerunResponse.status()));
                log.error("❌ Rerun expected HTTP 200 but got HTTP {}", rerunResponse.status());
                results.add(result);
                return result;
            }

            result.setResponseBody(rerunResponse.body()); // Update with rerun response

            // ─────────────────────────────────────────────────────────
            // STEP 11: Re-validate After Healing and Rerun
            // ─────────────────────────────────────────────────────────
            log.info("STEP 11: Re-validating after healing and rerun...");
            String updatedBaseline = baselineManager.loadBaseline(endpointKey);
            String normalizedUpdatedBaseline = normalizeResponseForComparison(updatedBaseline);
            String normalizedRerunResponse = normalizeResponseForComparison(rerunResponse.body());

            Map<String, String[]> postHealDiff = compareResponses(normalizedUpdatedBaseline, normalizedRerunResponse);

            if (postHealDiff.isEmpty()) {
                result.setStatus(TestStatus.PASSED);
                result.setReason("Healing successful — rerun response matches updated baseline");
                log.info("✅ Re-validation passed after healing and rerun");
                log.info("🔄 Rerun result: PASSED");
            } else {
                result.setStatus(TestStatus.FAILED);
                result.setReason("Post-healing validation still shows mismatches after rerun");
                log.error("❌ Re-validation failed after rerun — healing incomplete");
                postHealDiff.forEach((k, v) -> log.error("   [{}] expected={} | actual={}", k, v[0], v[1]));
                log.info("🔄 Rerun result: FAILED");
            }

            log.info("Healing action log: {}", extensionReport.diffResult().summary());

            log.info("🏁 Final status for {}: {}", url, result.status);

            results.add(result);
            return result;

        } catch (Exception e) {
            log.error("❌ Unexpected error: {}", e.getMessage(), e);
            result.setStatus(TestStatus.FAILED);
            result.setReason("Exception: " + e.getMessage());
            results.add(result);
            return result;
        }
    }

    /**
     * Normalizes the response body for baseline storage.
     */
    private String normalizeResponse(String responseBody) {
        try {
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(responseBody);
            if (root.has("data") && root.get("data").isArray()) {
                com.fasterxml.jackson.databind.JsonNode data = root.get("data");
                com.fasterxml.jackson.databind.node.ArrayNode normalizedData = mapper.createArrayNode();
                for (int i = 0; i < Math.min(data.size(), 3); i++) {
                    com.fasterxml.jackson.databind.JsonNode item = data.get(i);
                    if (item.has("username")) {
                        ((com.fasterxml.jackson.databind.node.ObjectNode) item).put("name", item.get("username").asText());
                        ((com.fasterxml.jackson.databind.node.ObjectNode) item).remove("username");
                    }
                    normalizedData.add(item);
                }
                ((com.fasterxml.jackson.databind.node.ObjectNode) root).set("data", normalizedData);
                ((com.fasterxml.jackson.databind.node.ObjectNode) root).put("count", normalizedData.size());
            }
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("Failed to normalize response body: {}", e.getMessage());
            return responseBody;
        }
    }

    /**
     * Normalizes the response body for comparison, handling dynamic fields.
     */
    private String normalizeResponseForComparison(String responseBody) {
        try {
            JsonNode root = mapper.readTree(responseBody);
            normalizeJsonForComparison(root);
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("Failed to normalize response for comparison: {}", e.getMessage());
            return responseBody;
        }
    }

    /**
     * Recursively normalizes JSON for comparison by removing or masking dynamic fields.
     */
    private void normalizeJsonForComparison(JsonNode node) {
        if (node.isObject()) {
            Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                JsonNode fieldValue = node.get(fieldName);

                // Remove dynamic fields
                if (isDynamicField(fieldName)) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) node).remove(fieldName);
                } else {
                    // Recursively normalize nested objects/arrays
                    normalizeJsonForComparison(fieldValue);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                normalizeJsonForComparison(item);
            }
        }
    }

    /**
     * Checks if a field name represents a dynamic field that should be ignored in comparisons.
     */
    private boolean isDynamicField(String fieldName) {
        // Common dynamic fields to ignore
        return fieldName.equals("id") ||
               fieldName.equals("timestamp") ||
               fieldName.equals("created_at") ||
               fieldName.equals("updated_at") ||
               fieldName.equals("token") ||
               fieldName.equals("session_id") ||
               fieldName.equals("request_id") ||
               fieldName.contains("time") ||
               fieldName.contains("date");
    }

    /**
     * Executes tests for multiple endpoints.
     *
     * @param urls list of endpoint URLs to test
     * @return list of results for each endpoint
     */
    public List<EndpointTestResult> testEndpoints(List<String> urls) {
        log.info("");
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║         API Testing Workflow — Multiple Endpoints         ║");
        log.info("╚════════════════════════════════════════════════════════════╝");

        results.clear();
        for (String url : urls) {
            testEndpoint(url);
        }

        printSummary();

        // Check if any healing occurred and suggest retesting
        long healedCount = results.stream().filter(EndpointTestResult::isHealed).count();
        if (healedCount > 0) {
            log.warn("⚠️  {} endpoint(s) were healed. Consider re-running all tests to check for dependency impacts.", healedCount);
        }

        return results;
    }

    /**
     * Prints a summary of all test results.
     */
    public void printSummary() {
        log.info("");
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║                     TEST SUMMARY                           ║");
        log.info("╚════════════════════════════════════════════════════════════╝");

        long passed = results.stream().filter(r -> r.status == TestStatus.PASSED).count();
        long failed = results.stream().filter(r -> r.status == TestStatus.FAILED).count();
        long healed = results.stream().filter(EndpointTestResult::isHealed).count();

        log.info("Total Endpoints:  {}", results.size());
        log.info("✅ Passed:         {} {}", passed, passed == results.size() ? "(all)" : "");
        log.info("❌ Failed:         {}", failed);
        log.info("🔧 Healed:         {} (via AI)", healed);

        log.info("");
        for (EndpointTestResult result : results) {
            String icon = result.status == TestStatus.PASSED ? "✅" : "❌";
            String healIcon = result.isHealed() ? " (🔧 healed)" : "";
            log.info("{} {} → {}{}", icon, result.url, result.reason, healIcon);
        }
    }

    /**
     * Generates a unique key for an endpoint.
     */
    private String generateEndpointKey(String url) {
        // Format: "GET::http://localhost:3000/login"
        return "GET::" + url;
    }

    /**
     * Deep JSON response comparison using Jackson.
     * Compares only fields present in expected JSON.
     * Returns a map of differences found.
     */
    private Map<String, String[]> compareResponses(String expected, String actual) {
        Map<String, String[]> diff = new LinkedHashMap<>();
        try {
            JsonNode expectedNode = mapper.readTree(expected);
            JsonNode actualNode = mapper.readTree(actual);
            compareJsonNodes("", expectedNode, actualNode, diff);
        } catch (IOException e) {
            // If JSON parsing fails, fall back to string comparison
            log.warn("⚠️  JSON parsing failed, using string comparison: {}", e.getMessage());
            if (!expected.equals(actual)) {
                diff.put("responseBody", new String[]{
                        "baseline (" + expected.length() + " chars)",
                        "actual (" + actual.length() + " chars)"
                });
            }
        }
        return diff;
    }

    /**
     * Recursively compares JSON nodes.
     * Only fields present in expected are validated.
     */
    private void compareJsonNodes(String path, JsonNode expected, JsonNode actual,
                                   Map<String, String[]> diff) {
        if (expected == null)
            return;

        if (expected.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = expected.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
                JsonNode expectedChild = entry.getValue();
                JsonNode actualChild = actual != null ? actual.get(entry.getKey()) : null;

                if (actualChild == null) {
                    diff.put(key, new String[]{expectedChild.toString(), "<missing>"});
                } else {
                    compareJsonNodes(key, expectedChild, actualChild, diff);
                }
            }
        } else if (expected.isArray()) {
            if (actual == null || !actual.isArray()) {
                diff.put(path, new String[]{expected.toString(), actual != null ? actual.toString() : "<missing>"});
            } else if (expected.size() != actual.size()) {
                diff.put(path + ".length", new String[]{String.valueOf(expected.size()), String.valueOf(actual.size())});
            } else {
                for (int i = 0; i < expected.size(); i++) {
                    compareJsonNodes(path + "[" + i + "]", expected.get(i), actual.get(i), diff);
                }
            }
        } else {
            // Leaf node — compare values
            String expectedStr = expected.toString();
            String actualStr = actual != null ? actual.toString() : "<missing>";
            if (!expectedStr.equals(actualStr)) {
                diff.put(path, new String[]{expectedStr, actualStr});
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Result Types
    // ─────────────────────────────────────────────────────────────────────────

    public enum TestStatus {
        PASSED, FAILED
    }

    public static class EndpointTestResult {
        public final String url;
        public TestStatus status = TestStatus.FAILED;
        public String reason = "Unknown";
        public int responseStatus;
        public String responseBody;
        public Map<String, String[]> diff = new HashMap<>();
        public boolean baselineNew = false;
        public boolean healed = false;

        public EndpointTestResult(String url) {
            this.url = url;
        }

        public void setStatus(TestStatus status) {
            this.status = status;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public void setResponseStatus(int status) {
            this.responseStatus = status;
        }

        public void setResponseBody(String body) {
            this.responseBody = body;
        }

        public void setDiff(Map<String, String[]> diff) {
            this.diff = diff;
        }

        public void setBaselineNew(boolean isNew) {
            this.baselineNew = isNew;
        }

        public void setHealed(boolean healed) {
            this.healed = healed;
        }

        public boolean isHealed() {
            return healed;
        }

        public boolean isPassed() {
            return status == TestStatus.PASSED;
        }
    }
}
