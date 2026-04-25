package core;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the Playwright {@link Browser} and {@link Page} lifecycle.
 * <p>
 * Usage:
 * </p>
 * 
 * <pre>
 * DriverManager.launch(false);
 * Page page = DriverManager.getPage();
 * // ... tests ...
 * DriverManager.quit();
 * </pre>
 */
public class DriverManager {

    private static final Logger log = LoggerFactory.getLogger(DriverManager.class);

    private static Playwright playwright;
    private static Browser browser;
    private static BrowserContext context;
    private static Page page;

    private DriverManager() {
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public static void launch(boolean headless) {
        if (playwright != null) {
            log.warn("DriverManager already launched — call quit() first.");
            return;
        }
        log.info("Launching Playwright Chromium (headless={})", headless);
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(headless));
        context = browser.newContext();
        page = context.newPage();
        log.info("Browser ready.");
    }

    public static void quit() {
        log.info("Closing Playwright resources.");
        if (page != null) {
            page.close();
            page = null;
        }
        if (context != null) {
            context.close();
            context = null;
        }
        if (browser != null) {
            browser.close();
            browser = null;
        }
        if (playwright != null) {
            playwright.close();
            playwright = null;
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public static Page getPage() {
        if (page == null)
            throw new IllegalStateException(
                    "Browser not launched. Call DriverManager.launch() first.");
        return page;
    }

    public static Browser getBrowser() {
        return browser;
    }

    public static BrowserContext getContext() {
        return context;
    }
}
