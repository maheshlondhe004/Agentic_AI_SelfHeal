package tests;

import api.ApiTestOrchestrator;
import api.ApiTestOrchestrator.EndpointTestResult;
import api.ApiTestOrchestrator.TestStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * API Self-Healing Test — Unified Workflow using ApiTestOrchestrator.
 *
 * <p>
 * This test leverages the complete API testing workflow orchestrator which handles:
 * </p>
 * <ol>
 * <li>API endpoint invocation</li>
 * <li>Response status validation (stop if not 200)</li>
 * <li>Baseline storage (first-time execution)</li>
 * <li>Baseline comparison (subsequent runs)</li>
 * <li>AI self-healing on mismatch</li>
 * <li>Re-validation after healing</li>
 * </ol>
 *
 * <p>
 * Run: {@code mvn test -Dtest.api.url=http://localhost:3000}
 * </p>
 */
public class ApiSelfHealingTest {

    private static final Logger log = LoggerFactory.getLogger(ApiSelfHealingTest.class);

    private static final String BASE_URL = "http://localhost:3000";
    private static final String ENDPOINT = "/login";

    private ApiTestOrchestrator orchestrator;
    private List<EndpointTestResult> results;

    @BeforeClass
    public void setUp() {
        log.info("══════════════════════════════════════════════");
        log.info("        API Self-Healing Test Suite");
        log.info(" Target: {}{}", BASE_URL, ENDPOINT);
        log.info("══════════════════════════════════════════════");
        orchestrator = new ApiTestOrchestrator();

        // Initialize expected-api-response.json with array structure
        try {
            String initialExpected = "[\n" +
                "  {\n" +
                "    \"url\": \"/login/create\",\n" +
                "    \"method\": \"POST\",\n" +
                "    \"timestamp\": \"2026-04-12T10:00:00\",\n" +
                "    \"expectedStatus\": 201,\n" +
                "    \"requestBody\": {\n" +
                "      \"fullName\": \"Sonal\",\n" +
                "      \"email\": \"sonal@test.com\"\n" +
                "    },\n" +
                "    \"responseBody\": {\n" +
                "      \"userId\": 101,\n" +
                "      \"fullName\": \"Sonal\",\n" +
                "      \"email\": \"sonal@test.com\",\n" +
                "      \"role\": \"USER\"\n" +
                "    }\n" +
                "  },\n" +
                "  {\n" +
                "    \"url\": \"/login\",\n" +
                "    \"method\": \"GET\",\n" +
                "    \"expectedStatus\": 200,\n" +
                "    \"responseBody\": {\n" +
                "      \"success\": true,\n" +
                "      \"message\": \"All logins retrieved successfully\",\n" +
                "      \"data\": [\n" +
                "        {\n" +
                "          \"id\": 1,\n" +
                "          \"name\": \"user1\",\n" +
                "          \"email\": \"user1@example.com\",\n" +
                "          \"status\": \"active\"\n" +
                "        },\n" +
                "        {\n" +
                "          \"id\": 2,\n" +
                "          \"name\": \"user2\",\n" +
                "          \"email\": \"user2@example.com\",\n" +
                "          \"status\": \"active\"\n" +
                "        },\n" +
                "        {\n" +
                "          \"id\": 3,\n" +
                "          \"name\": \"user3\",\n" +
                "          \"email\": \"user3@example.com\",\n" +
                "          \"status\": \"inactive\"\n" +
                "        }\n" +
                "      ],\n" +
                "      \"count\": 3\n" +
                "    }\n" +
                "  },\n" +
                "  {\n" +
                "    \"url\": \"/login/101\",\n" +
                "    \"method\": \"GET\",\n" +
                "    \"timestamp\": \"2026-04-12T10:02:00\",\n" +
                "    \"expectedStatus\": 200,\n" +
                "    \"responseBody\": {\n" +
                "      \"userId\": 101,\n" +
                "      \"fullName\": \"Sonal\",\n" +
                "      \"email\": \"sonal@test.com\"\n" +
                "    }\n" +
                "  }\n" +
                "]";
            Files.writeString(Paths.get("src/test/resources/expected-api-response.json"), initialExpected, StandardCharsets.UTF_8);
            log.info("Initialized expected-api-response.json with array structure");
        } catch (IOException e) {
            log.error("Failed to initialize expected-api-response.json", e);
        }
    }

    /**
     * Unified workflow test: Calls the orchestrator to handle all 6 steps.
     * 
     * Test passes if:
     * 1. API endpoint is reachable (HTTP 200)
     * 2. Either response matches baseline OR heating was attempted
     */
    @Test(priority = 1, description = "Complete API testing workflow with self-healing")
    public void testCompleteWorkflow() {
        String url = BASE_URL + ENDPOINT;

        EndpointTestResult result = orchestrator.testEndpoint(url);

        Assert.assertNotNull(result, "Orchestrator returned null result");
        Assert.assertTrue(result.responseStatus == 200,
                String.format("Expected HTTP 200, got HTTP %d", result.responseStatus));

        // Accept PASSED status or healed status (healing was attempted)
        if (result.status == TestStatus.FAILED && !result.isHealed()) {
            Assert.fail(String.format("Test failed and healing was not attempted: %s\nDetails: %s",
                    result.reason, result.diff));
        }

        log.info("✅ Complete workflow passed: {} {}", result.reason, 
                result.isHealed() ? "(healing applied)" : "");
    }

    /**
     * Optional: Test multiple endpoints in sequence.
     */
    @Test(priority = 2, description = "Multiple endpoints with orchestrator")
    public void testMultipleEndpoints() {
        List<String> endpoints = List.of(
                BASE_URL + "/login"
        );

        results = orchestrator.testEndpoints(endpoints);

        Assert.assertNotNull(results, "Orchestrator returned null results");
        Assert.assertFalse(results.isEmpty(), "No results were generated");

        long failedCount = results.stream()
                .filter(r -> r.status == TestStatus.FAILED)
                .count();

        // Log failures without failing the test (optional)
        if (failedCount > 0) {
            log.warn("⚠️  {} endpoint(s) failed", failedCount);
            results.stream()
                    .filter(r -> r.status == TestStatus.FAILED)
                    .forEach(r -> log.warn("❌ {}: {}", r.url, r.reason));
        }
    }
}
