package tests;

import api.ApiSelfHealer;
import api.ApiValidator;
import api.ApiValidator.ApiCallResult;
import api.ApiValidator.BodyCompareResult;
import core.DriverManager;
import core.SelfHealingDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.*;
import pages.LoginPage;

import java.nio.file.Paths;

/**
 * Combined API + UI test flow.
 *
 * <h3>Phase 1 — API Validation</h3>
 * <ol>
 * <li>HTTP GET {@value API_URL}</li>
 * <li>Assert HTTP 200</li>
 * <li>Deep-compare response body vs. {@code expected-api-response.json}
 * (developer-provided data)</li>
 * </ol>
 *
 * <h3>Phase 2 — UI Testing (local HTML page)</h3>
 * Runs only if both API checks pass ({@code dependsOnMethods}).
 * <ul>
 * <li>Valid credentials → success message visible</li>
 * <li>Wrong credentials → error message visible</li>
 * <li>Empty fields → error message visible</li>
 * </ul>
 *
 * <p>
 * The UI page is served via {@code file://} — no web server needed.
 * </p>
 *
 * <p>
 * <strong>Run:</strong> {@code mvn test -Dheadless=false -Dtest.api.url=<url>}
 * </p>
 */
public class ApiAndUiTest {

    private static final Logger log = LoggerFactory.getLogger(ApiAndUiTest.class);

    // ── API config ────────────────────────────────────────────────────────────
    /**
     * Full URL for the API status + body check.
     * Uses GET /login (the list endpoint) — always returns data regardless of
     * which individual records have been modified or deleted.
     * Supports: -Dtest.api.url=http://localhost:3000
     */
    private static final String API_URL = config.ApiEndpoints.url("/login");
    private static final String EXPECTED_RESOURCE = "expected-api-response.json";

    // ── UI config ─────────────────────────────────────────────────────────────
    private static final String VALID_USER = "admin";
    private static final String VALID_PASS = "test123";
    private static final String INVALID_USER = "hacker";
    private static final String INVALID_PASS = "wrongpass";

    // ── Shared state ──────────────────────────────────────────────────────────
    private ApiValidator apiValidator;
    private ApiSelfHealer selfHealer;
    private ApiCallResult apiResult; // cached so body check reuses the response
    private SelfHealingDriver driver;
    private LoginPage loginPage;
    private String localHtmlUrl;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @BeforeClass
    public void setUp() {
        apiValidator = new ApiValidator();
        selfHealer = new ApiSelfHealer();

        // Compute absolute file:// URL for the local HTML page
        localHtmlUrl = Paths.get("src/main/resources/test-page/login.html")
                .toAbsolutePath().toUri().toString();
        log.info("Local UI page: {}", localHtmlUrl);

        // Launch headless mode by default; pass -Dheadless=false to watch
        boolean headless = Boolean.parseBoolean(System.getProperty("headless", "true"));
        DriverManager.launch(headless);
        driver = new SelfHealingDriver(DriverManager.getPage());
        loginPage = new LoginPage(driver);
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() {
        DriverManager.quit();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PHASE 1 — API TESTING
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Step 1: Call the API and assert HTTP 200.
     * The response is cached for the body comparison step.
     */
    @Test(priority = 1, description = "Phase 1a: API endpoint must return HTTP 200")
    public void step1_apiStatusCheck() {
        log.info("═══ PHASE 1a — API Status Check ═══");
        log.info("Endpoint: {}", API_URL);

        apiResult = apiValidator.call(API_URL);

        Assert.assertFalse(apiResult.hasFailed(),
                "HTTP call itself failed: " + apiResult.errorMessage());
        Assert.assertEquals(apiResult.statusCode(), 200,
                String.format("Expected HTTP 200 but got HTTP %d for %s",
                        apiResult.statusCode(), API_URL));

        log.info("✅ HTTP 200 OK");
    }

    /**
     * Step 2: Compare the response body against developer-provided expected JSON.
     * Runs only if Step 1 passed.
     */
    @Test(priority = 2, description = "Phase 1b: Response body must match developer-provided expected data", dependsOnMethods = "step1_apiStatusCheck")
    public void step2_apiBodyCheck() {
        log.info("═══ PHASE 1b — API Body Comparison ═══");
        log.info("Comparing against resource: {}", EXPECTED_RESOURCE);

        BodyCompareResult result = apiValidator.compareBody(apiResult.body(), EXPECTED_RESOURCE);

        // ── Self-healing: on mismatch, call LLM → update files → re-compare ────
        if (!result.matched()) {
            log.warn("⚕️  Body mismatch detected — attempting API self-healing via OpenRouter…");
            log.warn("Diff:\n{}", result.diffSummary());

            boolean healed = selfHealer.heal(API_URL, apiResult.body(), result);

            if (healed) {
                log.info("🔁 Self-healing applied — re-comparing with updated expected JSON…");
                result = apiValidator.compareBody(apiResult.body(), EXPECTED_RESOURCE);
            } else {
                log.error("Self-healing was not successful — test will fail.");
            }
        }
        // ─────────────────────────────────────────────────────────────────────────

        Assert.assertTrue(result.matched(),
                "API body mismatch (even after self-healing attempt).\n\nDiff:\n"
                        + result.diffSummary()
                        + "\n\nActual body:\n" + apiResult.body());

        log.info("✅ Response body matches all expected developer-provided field(s).");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PHASE 2 — UI TESTING (local HTML page)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Step 3: Valid credentials → success message visible.
     * Depends on API body check passing.
     */
    @Test(priority = 3, description = "Phase 2a: Valid credentials must show success message", dependsOnMethods = "step2_apiBodyCheck")
    public void step3_uiLogin_validCredentials() {
        log.info("═══ PHASE 2a — UI: Valid Login ═══");
        driver.navigateTo(localHtmlUrl);

        loginPage.login(VALID_USER, VALID_PASS);

        // The local HTML shows #success-msg on valid login
        boolean visible = driver.getPage().locator("#success-msg").isVisible();
        Assert.assertTrue(visible,
                "Expected #success-msg to be visible after valid login");

        String text = driver.getPage().locator("#success-msg").innerText();
        log.info("Success message text: '{}'", text);
        Assert.assertFalse(text.isBlank(), "Success message must not be blank");
        log.info("✅ Valid login shows success message");
    }

    /**
     * Step 4: Wrong credentials → error message visible.
     */
    @Test(priority = 4, description = "Phase 2b: Wrong credentials must show error message", dependsOnMethods = "step2_apiBodyCheck")
    public void step4_uiLogin_wrongCredentials() {
        log.info("═══ PHASE 2b — UI: Wrong Credentials ═══");
        driver.navigateTo(localHtmlUrl);

        loginPage.login(INVALID_USER, INVALID_PASS);

        loginPage.waitForError();
        Assert.assertTrue(loginPage.hasErrorMessage(),
                "Expected error message to be visible after wrong credentials");

        String errorText = loginPage.getErrorMessage();
        Assert.assertNotNull(errorText, "Error message text must not be null");
        Assert.assertFalse(errorText.isBlank(), "Error message must not be blank");
        log.info("✅ Wrong credentials shows error: '{}'", errorText);
    }

    /**
     * Step 5: Empty fields → error message visible.
     */
    @Test(priority = 5, description = "Phase 2c: Empty credentials must show error message", dependsOnMethods = "step2_apiBodyCheck")
    public void step5_uiLogin_emptyCredentials() {
        log.info("═══ PHASE 2c — UI: Empty Credentials ═══");
        driver.navigateTo(localHtmlUrl);

        loginPage.login("", "");

        loginPage.waitForError();
        Assert.assertTrue(loginPage.hasErrorMessage(),
                "Expected error message to be visible when credentials are empty");
        log.info("✅ Empty credentials shows error message");
    }
}
