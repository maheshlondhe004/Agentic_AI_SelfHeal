package tests;

import api.ApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import config.ApiEndpoints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeSuite;

/**
 * TestNG suite-level setup that runs <strong>once before all tests</strong>
 * (Cucumber scenarios and the 4-step self-healing test).
 *
 * <h3>What it does</h3>
 * The Node.js server seeds exactly three fixture records on startup:
 * 
 * <pre>
 *   id=1  user1  (name field)
 *   id=2  user2  (name field)
 *   id=3  user3  (name field)
 * </pre>
 * 
 * Any record with {@code id > 3} was created by a previous test run that did
 * not clean up. This setup deletes all such records so the server is in a
 * known-clean state before each suite execution.
 */
public class TestSuiteSetup {

    private static final Logger log = LoggerFactory.getLogger(TestSuiteSetup.class);

    /** IDs 1-3 are the original fixture records — never delete these. */
    private static final int FIXTURE_MAX_ID = 3;

    @BeforeSuite(alwaysRun = true)
    public void cleanServerTestData() {
        String baseUrl = ApiEndpoints.BASE_URL;
        ApiClient client = new ApiClient();
        ObjectMapper mapper = new ObjectMapper();

        log.info("══════════════════════════════════════════════════════");
        log.info(" Pre-Suite Cleanup: removing non-fixture server records");
        log.info("══════════════════════════════════════════════════════");

        // 1 — GET all current records
        ApiClient.ApiCallResult getAll = client.get(baseUrl + "/login");
        if (getAll.hasFailed() || getAll.status() != 200) {
            log.warn("⚠️  Could not reach {} — skipping cleanup (server may not be running)",
                    baseUrl + "/login");
            return;
        }

        // 2 — Parse the data array
        int deleted = 0;
        try {
            JsonNode root = mapper.readTree(getAll.body());
            JsonNode data = root.path("data");

            if (!data.isArray()) {
                log.warn("Unexpected response shape from GET /login — data is not an array");
                return;
            }

            for (JsonNode record : data) {
                int id = record.path("id").asInt(-1);
                if (id > FIXTURE_MAX_ID) {
                    // Test-created record — delete it
                    ApiClient.ApiCallResult del = client.delete(baseUrl + "/login/" + id);
                    if (del.status() == 200 || del.status() == 404) {
                        log.info("🧹 Pre-suite cleanup: deleted /login/{} (HTTP {})",
                                id, del.status());
                        deleted++;
                    } else {
                        log.warn("⚠️  Pre-suite cleanup: DELETE /login/{} returned HTTP {}",
                                id, del.status());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Pre-suite cleanup failed: {}", e.getMessage());
        }

        if (deleted == 0) {
            log.info("✅ Server data is already clean — only fixture records present");
        } else {
            log.info("✅ Pre-suite cleanup complete — deleted {} non-fixture record(s)", deleted);
        }
        log.info("══════════════════════════════════════════════════════");
    }
}
