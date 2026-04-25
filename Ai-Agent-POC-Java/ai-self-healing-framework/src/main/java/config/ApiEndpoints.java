package config;

/**
 * Centralized API endpoint definitions for the Login REST API.
 *
 * <p>
 * All paths are relative to {@link #BASE_URL}. Use the helper methods
 * to construct full URLs with path parameters.
 * </p>
 *
 * <pre>
 * ┌──────────────────────────────────────────────────────────────┐
 * │  Method  │  Path              │  Purpose                     │
 * ├──────────────────────────────────────────────────────────────┤
 * │  GET     │  /login            │  Get all login records       │
 * │  GET     │  /login/:id        │  Get login record by ID      │
 * │  POST    │  /login            │  Create a new login record   │
 * │  PUT     │  /login/:id        │  Update a login record       │
 * │  DELETE  │  /login/:id        │  Delete a login record       │
 * └──────────────────────────────────────────────────────────────┘
 * </pre>
 */
public final class ApiEndpoints {

    private ApiEndpoints() {
    }

    // ── Base ──────────────────────────────────────────────────────────────────

    /**
     * Override via {@code -Dapi.base.url=http://...} or
     * {@code -Dtest.api.url=http://...}
     */
    public static final String BASE_URL = System.getProperty("api.base.url",
            System.getProperty("test.api.url", "http://localhost:3000"));

    // ── Path templates ────────────────────────────────────────────────────────

    /** GET /login — retrieve all login records */
    public static final String LOGIN_LIST = "/login";

    /** GET /login/:id — retrieve one login record by ID */
    public static final String LOGIN_GET_BY_ID = "/login/{id}";

    /** POST /login — create a new login record */
    public static final String LOGIN_CREATE = "/login";

    /** PUT /login/:id — fully replace a login record */
    public static final String LOGIN_UPDATE = "/login/{id}";

    /** DELETE /login/:id — remove a login record */
    public static final String LOGIN_DELETE = "/login/{id}";

    // ── Path builders (relative) ──────────────────────────────────────────────

    /** Returns {@code /login/<id>}. */
    public static String loginById(int id) {
        return "/login/" + id;
    }

    /** Returns {@code /login/<id>} (string variant). */
    public static String loginById(String id) {
        return "/login/" + id;
    }

    // ── Full URL builders ─────────────────────────────────────────────────────

    /** Returns {@code BASE_URL + path}. */
    public static String url(String path) {
        return BASE_URL + (path.startsWith("/") ? path : "/" + path);
    }

    /** Full URL for the login list endpoint. */
    public static String loginListUrl() {
        return url(LOGIN_LIST);
    }

    /** Full URL for a specific login record. */
    public static String loginByIdUrl(int id) {
        return url(loginById(id));
    }

    /** Full URL for a specific login record. */
    public static String loginByIdUrl(String id) {
        return url(loginById(id));
    }
}
