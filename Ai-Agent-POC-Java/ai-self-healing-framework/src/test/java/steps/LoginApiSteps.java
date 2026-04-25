package steps;

import com.fasterxml.jackson.databind.JsonNode;
import config.ApiEndpoints;
import io.cucumber.java.en.*;
import org.testng.Assert;

/**
 * Cucumber step definitions for the Login API feature.
 *
 * <p>
 * State is shared within a scenario via {@link ApiTestContext} (PicoContainer
 * DI).
 * Each scenario gets a fresh context so there is no cross-scenario bleed.
 * </p>
 *
 * <h3>{stored-id} placeholder</h3>
 * <p>
 * Paths may contain the literal string {@code {stored-id}}, which is replaced
 * at runtime by the value set via the
 * "I store response field ... as the target id" step.
 * </p>
 *
 * <h3>Self-healing integration</h3>
 * <p>
 * When a field assertion fails, the mismatch details are stored in
 * {@link ApiTestContext} BEFORE rethrowing — so {@link HealingHooks} can
 * invoke the LLM to patch the feature file automatically.
 * </p>
 */
public class LoginApiSteps {

    private final ApiTestContext ctx;

    public LoginApiSteps(ApiTestContext ctx) {
        this.ctx = ctx;
    }

    // ── Given ─────────────────────────────────────────────────────────────────

    @Given("the API base URL is {string}")
    public void theApiBaseUrlIs(String url) {
        ctx.baseUrl = System.getProperty("api.base.url",
                System.getProperty("test.api.url", url));
    }

    /**
     * Given step: creates a prerequisite login record via POST so subsequent
     * When steps (GET/PUT/DELETE) can act on a known record.
     * Stores the returned {@code data.id} in {@code ctx.storedId}.
     */
    @Given("a login record exists with body:")
    public void aLoginRecordExistsWithBody(String body) {
        String url = ctx.baseUrl + "/login";
        ctx.failedEndpointUrl = url;
        ctx.lastResponse = ctx.client.post(url, body.trim());
        Assert.assertEquals(ctx.lastResponse.status(), 201,
                "Precondition failed — could not create login record: " + ctx.lastResponse.body());
        ctx.storedId = ctx.client.extractField(ctx.lastResponse.body(), "data.id");
        Assert.assertNotNull(ctx.storedId, "Precondition failed — data.id missing in POST response");
    }

    // ── When — HTTP verbs ─────────────────────────────────────────────────────

    @When("I send a GET request to {string}")
    public void iSendGetRequest(String path) {
        String resolvedPath = ctx.baseUrl + resolve(path);
        ctx.failedEndpointUrl = resolvedPath;
        ctx.lastResponse = ctx.client.get(resolvedPath);
    }

    // ── Then / And — assertions ───────────────────────────────────────────────

    @Then("the response status code should be {int}")
    public void theResponseStatusCodeShouldBe(int expected) {
        assertResponseReceived();
        Assert.assertEquals(ctx.lastResponse.status(), expected,
                String.format("Expected HTTP %d but got HTTP %d. Body: %s",
                        expected, ctx.lastResponse.status(), ctx.lastResponse.body()));
    }

    @Then("the response body should not be empty")
    public void theResponseBodyShouldNotBeEmpty() {
        assertResponseReceived();
        Assert.assertFalse(ctx.lastResponse.isEmpty(),
                "Expected a non-empty response body but got: " + ctx.lastResponse.body());
    }

    /**
     * Asserts a field value matches — stores mismatch details in context before
     * rethrowing so {@link HealingHooks} can trigger LLM healing.
     */
    @Then("the response body field {string} should equal {string}")
    public void theResponseBodyFieldShouldEqual(String field, String expectedValue) {
        assertResponseReceived();
        String actual = ctx.client.extractField(ctx.lastResponse.body(), field);
        if (!expectedValue.equals(actual)) {
            // Record mismatch for HealingHooks BEFORE throwing
            ctx.failedField = field;
            ctx.expectedFieldValue = expectedValue;
            ctx.actualFieldValue = actual;
            Assert.assertEquals(actual, expectedValue,
                    String.format("Field '%s' — expected='%s' actual='%s'. Body: %s",
                            field, expectedValue, actual, ctx.lastResponse.body()));
        }
    }

    @Then("the response body should contain field {string} with value {string}")
    public void theResponseBodyShouldContainField(String field, String value) {
        theResponseBodyFieldShouldEqual(field, value);
    }

    @Then("each login record should contain non-empty field {string}")
    public void eachLoginRecordShouldContainNonEmptyField(String field) {
        assertResponseReceived();
        JsonNode body = ctx.client.parseJson(ctx.lastResponse.body());
        Assert.assertNotNull(body, "Could not parse response JSON for login record validation");

        JsonNode data = body.path("data");
        Assert.assertTrue(data.isArray(), "Expected 'data' to be an array in GET /login response");

        for (JsonNode record : data) {
            JsonNode value = record.path(field);
            Assert.assertFalse(value.isMissingNode() || value.isNull() || value.asText().isBlank(),
                    String.format("Expected each login record to contain a non-empty '%s'; record was: %s",
                            field, record.toString()));
        }
    }

    @Then("each login record should contain a name field")
    public void eachLoginRecordShouldContainANameField() {
        assertResponseReceived();
        JsonNode body = ctx.client.parseJson(ctx.lastResponse.body());
        Assert.assertNotNull(body, "Could not parse response JSON for login record validation");

        JsonNode data = body.path("data");
        Assert.assertTrue(data.isArray(), "Expected 'data' to be an array in GET /login response");

        for (int i = 0; i < data.size(); i++) {
            JsonNode record = data.get(i);
            JsonNode name = record.path("name");
            if (name.isMissingNode() || name.isNull() || name.asText().isBlank()) {
                // Record mismatch for healing
                ctx.failedField = "data[" + i + "].name";
                ctx.expectedFieldValue = "non-empty";
                ctx.actualFieldValue = name.isMissingNode() ? "<missing>" : name.asText();
                ctx.failedEndpointUrl = ctx.baseUrl + "/login";
                Assert.fail(String.format("Expected each login record to contain a non-empty name field; record was: %s",
                        record.toString()));
            }
        }
    }

    @And("I store response field {string} as the target id")
    public void storeResponseFieldAsTargetId(String field) {
        assertResponseReceived();
        ctx.storedId = ctx.client.extractField(ctx.lastResponse.body(), field);
        Assert.assertNotNull(ctx.storedId,
                "Could not extract field '" + field + "' from response: "
                        + ctx.lastResponse.body());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String resolve(String path) {
        if (ctx.storedId != null && path.contains("{stored-id}")) {
            return path.replace("{stored-id}", ctx.storedId);
        }
        return path;
    }

    private void assertResponseReceived() {
        Assert.assertNotNull(ctx.lastResponse,
                "No HTTP response recorded — did you send a request?");
        Assert.assertFalse(ctx.lastResponse.hasFailed(),
                "HTTP call failed with error: " + ctx.lastResponse.error());
    }
}
