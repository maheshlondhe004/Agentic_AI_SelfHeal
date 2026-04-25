package tests;

import core.DriverManager;
import core.SelfHealingDriver;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import pages.LoginPage;

import static org.testng.Assert.*;

/**
 * End-to-end login tests using the AI Self-Healing Framework.
 *
 * <p>
 * Target: <a href=
 * "https://the-internet.herokuapp.com/login">the-internet.herokuapp.com</a>
 * — a stable public demo login form.
 * </p>
 *
 * <p>
 * <strong>To run:</strong>
 * </p>
 * 
 * <pre>
 *   # Set OPENAI_API_KEY in .env, then:
 *   mvn test                    # headless (CI)
 *   mvn test -Dheadless=false   # watch the browser
 * </pre>
 */
public class LoginTest {

    private static final String TARGET_URL = "https://the-internet.herokuapp.com/login";
    private static final String VALID_USER = "tomsmith";
    private static final String VALID_PASS = "SuperSecretPassword!";
    private static final String INVALID_USER = "wronguser";
    private static final String INVALID_PASS = "wrongpassword";

    private SelfHealingDriver driver;
    private LoginPage loginPage;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @BeforeClass
    public void setUp() {
        boolean headless = Boolean.parseBoolean(System.getProperty("headless", "true"));
        DriverManager.launch(headless);
        driver = new SelfHealingDriver(DriverManager.getPage());
        loginPage = new LoginPage(driver);
        driver.navigateTo(TARGET_URL);
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() {
        DriverManager.quit();
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test(priority = 1, description = "Valid credentials should redirect to secure area")
    public void testSuccessfulLogin() {
        driver.navigateTo(TARGET_URL);
        loginPage.login(VALID_USER, VALID_PASS);

        // Verify URL changed to /secure
        String url = driver.getPage().url();
        assertTrue(url.contains("/secure"),
                "Expected redirect to /secure but URL was: " + url);

        // Check success flash using raw Playwright locator (the-internet:
        // #flash.success)
        boolean flashVisible = driver.getPage().locator("#flash.success").isVisible();
        assertTrue(flashVisible, "Expected success flash message (#flash.success) to be visible");
    }

    @Test(priority = 2, description = "Invalid credentials should show an error message")
    public void testFailedLogin_wrongCredentials() {
        driver.navigateTo(TARGET_URL);
        loginPage.login(INVALID_USER, INVALID_PASS);

        String url = driver.getPage().url();
        assertTrue(url.contains("/login"),
                "Expected to stay on /login but URL was: " + url);

        loginPage.waitForError();
        assertTrue(loginPage.hasErrorMessage(),
                "Expected an error message after failed login");
        assertFalse(loginPage.getErrorMessage().isBlank(),
                "Error message should not be blank");
    }

    @Test(priority = 3, description = "Empty credentials should show an error message")
    public void testFailedLogin_emptyCredentials() {
        driver.navigateTo(TARGET_URL);
        loginPage.login("", "");

        loginPage.waitForError();
        assertTrue(loginPage.hasErrorMessage(),
                "Expected error message when submitting empty credentials");
    }
}
