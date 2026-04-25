package core;

import com.microsoft.playwright.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DOM utility helpers for extracting page context used by the AI healing agent.
 */
public class DOMUtils {

    private static final Logger log = LoggerFactory.getLogger(DOMUtils.class);
    private static final int MAX_DOM_LENGTH = 8_000;

    private DOMUtils() {
    }

    /**
     * Returns {@code <body>} outerHTML, truncated to {@value MAX_DOM_LENGTH} chars
     * to stay within AI token limits.
     */
    public static String getBodySnapshot(Page page) {
        try {
            String html = (String) page.evaluate("() => document.body.outerHTML");
            if (html == null)
                return "";
            if (html.length() > MAX_DOM_LENGTH) {
                log.debug("DOM truncated {} → {} chars.", html.length(), MAX_DOM_LENGTH);
                return html.substring(0, MAX_DOM_LENGTH) + "\n<!-- [truncated] -->";
            }
            return html;
        } catch (Exception e) {
            log.warn("Failed to get DOM snapshot: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Returns {@code true} if at least one element matching {@code selector} is
     * visible.
     */
    public static boolean isVisible(Page page, String selector) {
        try {
            return page.locator(selector).isVisible();
        } catch (Exception e) {
            return false;
        }
    }

    /** Returns outerHTML of first matching element, or empty string. */
    public static String getElementHtml(Page page, String selector) {
        try {
            return page.locator(selector).first().evaluate("el => el.outerHTML").toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** Count of elements matching {@code selector}. */
    public static int countMatches(Page page, String selector) {
        try {
            return page.locator(selector).count();
        } catch (Exception e) {
            return 0;
        }
    }

    /** Inner text of first matching element, or {@code null}. */
    public static String getText(Page page, String selector) {
        try {
            return page.locator(selector).first().innerText();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns a compact JSON array of up to 50 interactive elements
     * (tag, id, name, type, aria-label, data-testid, text) — used as AI context.
     */
    public static String getInteractiveElementsJson(Page page) {
        String script = """
                () => JSON.stringify(
                  [...document.querySelectorAll('input,button,a,select,textarea')]
                    .slice(0, 50)
                    .map(el => ({
                      tag:       el.tagName.toLowerCase(),
                      id:        el.id        || null,
                      name:      el.name      || null,
                      type:      el.type      || null,
                      ariaLabel: el.getAttribute('aria-label') || null,
                      testId:    el.getAttribute('data-testid') || null,
                      text:      (el.innerText || '').slice(0, 80).trim() || null
                    }))
                )
                """;
        try {
            return (String) page.evaluate(script);
        } catch (Exception e) {
            log.warn("Failed to extract interactive elements: {}", e.getMessage());
            return "[]";
        }
    }
}
