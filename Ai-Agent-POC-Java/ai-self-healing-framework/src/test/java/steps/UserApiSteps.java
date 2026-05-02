package steps;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pages.UserApiPage;

/**
 * Cucumber step definitions for {@code user-api.feature}.
 *
 * <h3>Design Contract</h3>
 * <p>
 * These step definitions are <strong>intentionally thin</strong>. They contain
 * <em>no</em> HTTP logic, assertion logic, schema comparison logic, or
 * self-healing implementation. Every method delegates immediately to
 * {@link UserApiPage}, which is the single source of truth for all business
 * logic.
 * </p>
 *
 * <h3>Responsibility mapping</h3>
 * <pre>
 * Step keyword  Step text                                              Delegated to
 * ─────────────────────────────────────────────────────────────────────────────────
 * Given         the User API endpoint is configured                  → configureEndpoint()
 * When          I send a GET request for user with id "<UserId>"     → executeGetUserById()
 * Then          the response status code should be 200               → shared LoginApiSteps step (reads ctx.lastResponse)
 * And           the response should contain field "X" with valid data → validateFieldHasValidData()
 * And           the schema should be auto-healed if changes detected → autoHealSchemaIfChanged()
 * And           the feature file and step defs should be updated...  → triggerSelfHealingIfMismatchDetected()
 * </pre>
 *
 * <h3>Cross-layer bridging</h3>
 * <p>
 * {@link UserApiPage} lives in {@code main/java} and cannot import
 * {@link ApiTestContext} (a test-scoped class). This class bridges the two
 * layers: after each HTTP call it copies {@code userApiPage.getLastResponse()}
 * into {@code ctx.lastResponse} so that all Cucumber shared steps — including
 * the status-code assertion registered once in {@link LoginApiSteps} — read the
 * correct response without any duplicate step registration.
 * </p>
 *
 * <h3>Self-healing integration</h3>
 * <p>
 * When a field assertion in {@link UserApiPage#validateFieldHasValidData} fails,
 * the mismatch details are stored inside {@link UserApiPage} BEFORE the exception
 * is re-thrown. The {@code @After} hook defined here then calls
 * {@link UserApiPage#triggerSelfHealingIfMismatchDetected()} so the LLM healer
 * can patch the feature file and step definitions for the next run — even if the
 * scenario ended early due to the assertion failure.
 * </p>
 */
public class UserApiSteps {

    private static final Logger log = LoggerFactory.getLogger(UserApiSteps.class);

    /**
     * Shared scenario context injected by PicoContainer.
     * The same instance is shared with {@link LoginApiSteps} and {@link HealingHooks}.
     */
    private final ApiTestContext ctx;

    /**
     * Page object that owns all User API business logic.
     * Receives {@code ctx.client} so the response recorder is the same instance
     * used across the whole scenario.
     */
    private final UserApiPage userApiPage;

    /** PicoContainer constructor — injects the shared scenario-scoped context. */
    public UserApiSteps(ApiTestContext ctx) {
        this.ctx = ctx;
        this.userApiPage = new UserApiPage(ctx.client);
    }

    // =========================================================================
    // Lifecycle hooks
    // =========================================================================

    /**
     * Reset page-level mismatch state before each scenario so data does not
     * bleed across Scenario Outline rows.
     */
    @Before("@self-healing")
    public void beforeUserApiScenario(Scenario scenario) {
        ctx.clearMismatch();
        userApiPage.reset();
        log.info("▶ Starting scenario: {}", scenario.getName());
    }

    /**
     * After-hook safety net: if a field-validation step threw an exception before
     * the explicit "trigger self-healing" step could run, this hook ensures
     * self-healing is still attempted.
     */
    @After("@self-healing")
    public void afterUserApiScenario(Scenario scenario) {
        if (scenario.isFailed()) {
            log.warn("⚕️  Scenario '{}' failed — attempting safety-net self-heal.", scenario.getName());
            // Sync ctx mismatch fields from page (in case mismatch was set before failure)
            syncMismatchToContext();
            userApiPage.triggerSelfHealingIfMismatchDetected();
        }
        log.info("■ Finished scenario: {} — {}", scenario.getName(),
                scenario.isFailed() ? "FAILED" : "PASSED");
    }

    // =========================================================================
    // Given — Background step
    // =========================================================================

    /**
     * Configures the User API endpoint URL and mirrors the base URL into the
     * shared context so all step classes see the same value.
     *
     * <p><strong>Logic owner:</strong> {@link UserApiPage#configureEndpoint()}</p>
     */
    @Given("the User API endpoint is configured")
    public void theUserApiEndpointIsConfigured() {
        String baseUrl = userApiPage.configureEndpoint();
        // Mirror into shared context if not already set by the Background step
        if (ctx.baseUrl == null || ctx.baseUrl.isBlank()) {
            ctx.baseUrl = baseUrl;
        }
    }

    // =========================================================================
    // When — Request execution
    // =========================================================================

    /**
     * Sends a GET request for the specified user ID.
     *
     * <p>After the call, the response is written into {@code ctx.lastResponse} and
     * {@code ctx.failedEndpointUrl} so that the shared
     * {@code "the response status code should be {int}"} step (registered in
     * {@link LoginApiSteps}) can read it without needing its own registration.</p>
     *
     * <p><strong>Logic owner:</strong> {@link UserApiPage#executeGetUserById(String)}</p>
     *
     * @param userId the user identifier from the Scenario Outline Examples table
     */
    @When("I send a GET request for user with id {string}")
    public void iSendAGetRequestForUserWithId(String userId) {
        userApiPage.executeGetUserById(userId);
        // Bridge: write result into shared context so shared steps can read it
        ctx.lastResponse      = userApiPage.getLastResponse();
        ctx.failedEndpointUrl = userApiPage.getLastEndpointUrl();
    }

    // =========================================================================
    // Then / And — Response validation
    // =========================================================================

    // NOTE: "the response status code should be {int}" is NOT re-declared here.
    // It is registered once in LoginApiSteps and Cucumber shares all glue within
    // the same package. Because ctx.lastResponse is populated in the When step
    // above, that shared assertion reads the correct User API response.

    /**
     * Validates that the named field exists and contains non-blank data.
     *
     * <p>Field names use the business-level alias from the feature file
     * (e.g. "username", "email", "id"). {@link UserApiPage} resolves these to
     * actual JSON dot-paths before extraction.</p>
     *
     * <p><strong>Logic owner:</strong> {@link UserApiPage#validateFieldHasValidData(String)}</p>
     *
     * @param fieldName logical field name as written in the feature file
     */
    @And("the response should contain field {string} with valid data")
    public void theResponseShouldContainFieldWithValidData(String fieldName) {
        userApiPage.validateFieldHasValidData(fieldName);
    }

    /**
     * Generic contract validation that keeps feature files stable even when
     * individual field names evolve.
     */
    @And("the API response should match the expected JSON contract")
    public void theApiResponseShouldMatchTheExpectedJsonContract() {
        userApiPage.validateResponseAgainstExpectedJsonContract();
    }

    /**
     * Triggers schema comparison against the stored baseline and invokes the
     * LLM-powered self-healer if structural drift is detected.
     *
     * <p><strong>Logic owner:</strong> {@link UserApiPage#autoHealSchemaIfChanged()}</p>
     */
    @And("the schema should be auto-healed if changes are detected")
    public void theSchemaShouldBeAutoHealedIfChangesAreDetected() {
        userApiPage.autoHealSchemaIfChanged();
    }

    /**
     * Checks whether any field mismatch was recorded and, if so, patches the
     * feature file and step definitions via the LLM healer.
     *
     * <p><strong>Logic owner:</strong>
     * {@link UserApiPage#triggerSelfHealingIfMismatchDetected()}</p>
     */
    @And("the feature file and step definitions should be updated if required")
    public void theFeatureFileAndStepDefinitionsShouldBeUpdatedIfRequired() {
        syncMismatchToContext();
        userApiPage.triggerSelfHealingIfMismatchDetected();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Copies any mismatch data from {@link UserApiPage} into the shared
     * {@link ApiTestContext} so that {@link HealingHooks} has the full picture
     * in its {@code @After} hook (which fires after this class's hook).
     */
    private void syncMismatchToContext() {
        if (userApiPage.getFailedField() != null) {
            ctx.failedField        = userApiPage.getFailedField();
            ctx.expectedFieldValue = userApiPage.getExpectedFieldValue();
            ctx.actualFieldValue   = userApiPage.getActualFieldValue();
        }
    }
}
