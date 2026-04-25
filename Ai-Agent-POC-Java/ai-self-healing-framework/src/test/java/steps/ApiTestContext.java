package steps;

import api.ApiClient;

/**
 * Shared scenario context injected by PicoContainer.
 * A fresh instance is created for each Cucumber scenario.
 */
public class ApiTestContext {

    /** HTTP client for all API calls in the scenario. */
    public ApiClient client = new ApiClient();

    /** Response from the most recent HTTP call. */
    public ApiClient.ApiCallResult lastResponse;

    /** Base URL set in the Background step. */
    public String baseUrl;

    /**
     * ID stored by the "I store response field ... as the target id" step.
     * Used to replace {@code {stored-id}} placeholders in subsequent step paths.
     */
    public String storedId;

    /**
     * ID of any record created via POST during this scenario.
     * Set automatically by step definitions on every 201 response.
     * Used by {@link HealingHooks} to clean up (DELETE) the record after the
     * scenario.
     */
    public String lastCreatedId;

    // ── Self-healing fields (populated on field assertion failure) ────────────

    /** Dot-path of the field that failed assertion (e.g. {@code data.username}). */
    public String failedField;

    /** The expected value that was checked. */
    public String expectedFieldValue;

    /** The actual value returned by the API. */
    public String actualFieldValue;

    /** The full endpoint URL of the request that caused the mismatch. */
    public String failedEndpointUrl;

    /**
     * Clears all mismatch and cleanup tracking fields (called in @Before each
     * scenario).
     */
    public void clearMismatch() {
        failedField = null;
        expectedFieldValue = null;
        actualFieldValue = null;
        failedEndpointUrl = null;
        lastCreatedId = null;
        storedId = null;
    }

    /** Returns {@code true} if a field mismatch has been recorded. */
    public boolean hasMismatch() {
        return failedField != null;
    }
}
