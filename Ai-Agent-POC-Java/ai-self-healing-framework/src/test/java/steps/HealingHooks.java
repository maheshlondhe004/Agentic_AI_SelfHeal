package steps;

import api.ApiSelfHealer;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cucumber lifecycle hooks for:
 * <ol>
 * <li><strong>Cleanup</strong> — DELETE any record POSTed during the scenario
 * so the in-memory server stays clean (only original user1/user2/user3).</li>
 * <li><strong>API Self-Healing</strong> — when a field assertion fails, call
 * OpenAI LLM to patch the feature file for the next run.</li>
 * </ol>
 *
 * <p>
 * Cleanup runs first in {@code @After} so the healing check has an accurate
 * view of what failed.
 * </p>
 */
public class HealingHooks {

    private static final Logger log = LoggerFactory.getLogger(HealingHooks.class);

    private final ApiTestContext ctx;
    private final ApiSelfHealer healer = new ApiSelfHealer();

    /**
     * PicoContainer injects the same scenario-scoped context as step definitions.
     */
    public HealingHooks(ApiTestContext ctx) {
        this.ctx = ctx;
    }

    @Before
    public void beforeScenario(Scenario scenario) {
        ctx.clearMismatch();
        log.debug("Starting scenario: {}", scenario.getName());
    }

    /**
     * After each scenario:
     * <ol>
     * <li>DELETE any record created by this scenario (keeps server data
     * clean).</li>
     * <li>If scenario failed with a field mismatch, invoke LLM to heal the feature
     * file.</li>
     * </ol>
     */
    @After
    public void afterScenario(Scenario scenario) {
        // ── 1. Cleanup: delete record created by this scenario ────────────────
        if (ctx.lastCreatedId != null && ctx.baseUrl != null) {
            String deleteUrl = ctx.baseUrl + "/login/" + ctx.lastCreatedId;
            try {
                var result = ctx.client.delete(deleteUrl);
                if (result.status() == 200 || result.status() == 404) {
                    log.info("🧹 Cleanup: deleted /login/{} (HTTP {})",
                            ctx.lastCreatedId, result.status());
                } else {
                    log.warn("🧹 Cleanup: DELETE /login/{} returned HTTP {}",
                            ctx.lastCreatedId, result.status());
                }
            } catch (Exception e) {
                log.warn("🧹 Cleanup failed for /login/{}: {}", ctx.lastCreatedId, e.getMessage());
            }
        }

        // ── 2. Self-healing: patch feature file on assertion failure ──────────
        if (!scenario.isFailed())
            return;

        if (ctx.hasMismatch()) {
            log.warn("⚕️  Scenario '{}' failed with field mismatch — triggering API self-healing",
                    scenario.getName());

            String body = ctx.lastResponse != null ? ctx.lastResponse.body() : "{}";
            String url = ctx.failedEndpointUrl != null
                    ? ctx.failedEndpointUrl
                    : System.getProperty("test.api.url", "http://localhost:3000");

            healer.healFeatureAssertion(url, body,
                    ctx.failedField, ctx.expectedFieldValue, ctx.actualFieldValue);

            if (healer.wasHealingPerformed()) {
                log.info("🔁 Feature file healed. Re-run: mvn test -Dtest.api.url={}",
                        System.getProperty("test.api.url", "http://localhost:3000"));
            }
        } else {
            log.debug("Scenario '{}' failed but no field mismatch recorded — skipping API healing.",
                    scenario.getName());
        }
    }
}
