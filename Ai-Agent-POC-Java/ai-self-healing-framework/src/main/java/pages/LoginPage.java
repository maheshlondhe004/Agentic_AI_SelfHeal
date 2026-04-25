package pages;

import core.SelfHealingDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Page Object for the Login page.
 * All element interactions go through {@link SelfHealingDriver} using
 * logical IDs from {@code locators.json}.
 */
public class LoginPage {

    private static final Logger log = LoggerFactory.getLogger(LoginPage.class);

    private static final String USERNAME_ID = "login-username";
    private static final String PASSWORD_ID = "login-password";
    private static final String SUBMIT_ID = "login-submit";
    private static final String ERROR_ID = "login-error-message";

    private final SelfHealingDriver driver;

    public LoginPage(SelfHealingDriver driver) {
        this.driver = driver;
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    public void enterUsername(String username) {
        log.info("Entering username: {}", username);
        driver.fill(USERNAME_ID, username);
    }

    public void enterPassword(String password) {
        log.info("Entering password.");
        driver.fill(PASSWORD_ID, password);
    }

    public void clickLogin() {
        log.info("Clicking login button.");
        driver.click(SUBMIT_ID);
    }

    public void login(String username, String password) {
        enterUsername(username);
        enterPassword(password);
        clickLogin();
    }

    // ── State queries ─────────────────────────────────────────────────────────

    public boolean hasErrorMessage() {
        return driver.isVisible(ERROR_ID);
    }

    public String getErrorMessage() {
        if (!hasErrorMessage())
            return null;
        return driver.getText(ERROR_ID);
    }

    public void waitForError() {
        driver.waitUntilVisible(ERROR_ID);
    }
}
