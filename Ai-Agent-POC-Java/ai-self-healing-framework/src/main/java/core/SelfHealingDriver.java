package core;

import ai.HealingAgent;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.RetryUtils;

import java.io.IOException;
import java.util.List;

/**
 * ⭐ IMPORTANT — Self-Healing Playwright Driver.
 *
 * <p>
 * Wraps a Playwright {@link Page} and intercepts every element interaction.
 * When a locator fails it automatically:
 * </p>
 * <ol>
 * <li>Tries all alternative selectors from {@link LocatorStore}</li>
 * <li>Calls {@link HealingAgent} (OpenAI) to get a new selector</li>
 * <li>Persists the healed selector back to {@link LocatorStore}</li>
 * <li>Retries the action with the new locator</li>
 * </ol>
 *
 * <p>
 * All interactions are wrapped with {@link RetryUtils} for flakiness tolerance.
 * </p>
 */
public class SelfHealingDriver {

    private static final Logger log = LoggerFactory.getLogger(SelfHealingDriver.class);

    private static final int MAX_ACTION_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 500;
    private static final long DEFAULT_TIMEOUT_MS = 5_000;

    private final Page page;
    private final LocatorStore store;
    private final HealingAgent healingAgent;

    // ── Constructors ──────────────────────────────────────────────────────────

    public SelfHealingDriver(Page page) {
        this(page, new LocatorStore(), new HealingAgent());
    }

    /** Full DI constructor — use in tests with mocked collaborators. */
    public SelfHealingDriver(Page page, LocatorStore store, HealingAgent healingAgent) {
        this.page = page;
        this.store = store;
        this.healingAgent = healingAgent;
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    public void navigateTo(String url) {
        log.info("Navigating to {}", url);
        page.navigate(url);
    }

    // ── Interactions ──────────────────────────────────────────────────────────

    /** Types {@code value} into the element identified by {@code elementId}. */
    public void fill(String elementId, String value) {
        Locator loc = resolveLocator(elementId);
        RetryUtils.retryVoid(MAX_ACTION_RETRIES, RETRY_DELAY_MS, () -> loc.fill(value));
        log.debug("fill('{}') OK", elementId);
    }

    /** Clicks the element identified by {@code elementId}. */
    public void click(String elementId) {
        Locator loc = resolveLocator(elementId);
        RetryUtils.retryVoid(MAX_ACTION_RETRIES, RETRY_DELAY_MS, loc::click);
        log.debug("click('{}') OK", elementId);
    }

    /** Returns the visible inner text of the element. */
    public String getText(String elementId) {
        return RetryUtils.retry(MAX_ACTION_RETRIES, RETRY_DELAY_MS,
                resolveLocator(elementId)::innerText);
    }

    /** Returns {@code true} when the element is currently visible. */
    public boolean isVisible(String elementId) {
        try {
            return resolveLocator(elementId).isVisible();
        } catch (Exception e) {
            return false;
        }
    }

    /** Waits until element is visible (up to {@value DEFAULT_TIMEOUT_MS} ms). */
    public void waitUntilVisible(String elementId) {
        resolveLocator(elementId).waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(DEFAULT_TIMEOUT_MS));
    }

    // ── Core: Locator resolution with self-healing ────────────────────────────

    /**
     * Tries primary selector, then alternatives, then invokes the AI agent
     * if all known selectors are invisible.
     */
    public Locator resolveLocator(String elementId) {
        List<String> selectors = store.getAllSelectors(elementId);

        for (String selector : selectors) {
            if (DOMUtils.isVisible(page, selector)) {
                log.debug("Resolved '{}' → '{}'", elementId, selector);
                return page.locator(selector);
            }
            log.warn("Selector '{}' not visible for '{}'", selector, elementId);
        }

        log.warn("All {} selector(s) failed for '{}' — invoking AI self-healing (OpenAI).",
                selectors.size(), elementId);
        return healWithAI(elementId, selectors.get(0));
    }

    private Locator healWithAI(String elementId, String failedLocator) {
        String domSnapshot = DOMUtils.getBodySnapshot(page);
        String interactiveElements = DOMUtils.getInteractiveElementsJson(page);
        String description = "UI element with logical id: " + elementId;

        // NOTE: HealingAgent internally retries with back-off for slow OpenAI
        // responses
        HealingAgent.HealingResult result = healingAgent.healLocator(
                elementId, description, failedLocator, interactiveElements, domSnapshot);

        String newSelector = result.selector();
        log.info("AI proposed selector '{}' for '{}' (confidence={})",
                newSelector, elementId, result.confidence());

        if (!DOMUtils.isVisible(page, newSelector)) {
            throw new RuntimeException(
                    "AI-proposed selector '" + newSelector + "' is still not visible on page.");
        }

        try {
            store.healPrimary(elementId, newSelector);
        } catch (IOException e) {
            log.error("Could not persist healed locator for '{}': {}", elementId, e.getMessage());
        }

        return page.locator(newSelector);
    }

    // ── Delegation ────────────────────────────────────────────────────────────

    /** Returns the underlying Playwright {@link Page} for direct access. */
    public Page getPage() {
        return page;
    }
}
