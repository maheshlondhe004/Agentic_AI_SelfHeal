package api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Smart per-endpoint response recorder.
 *
 * <h3>Logic (as requested)</h3>
 * <ol>
 * <li>First call to {@link #record} for a given URL+method → saves the
 * response.</li>
 * <li>Subsequent calls with the <em>same</em> body → no update (skip).</li>
 * <li>Subsequent calls with a <em>different</em> body → updates the entry and
 * sets {@code changed = true} so callers can trigger self-healing.</li>
 * </ol>
 *
 * <p>
 * All state is persisted to {@code actual-api-response.json}.
 * Existing file is loaded at startup so state survives across test runs.
 * </p>
 */
public class ApiResponseRecorder {

    private static final Logger log = LoggerFactory.getLogger(ApiResponseRecorder.class);
    private static final ApiResponseRecorder INSTANCE = new ApiResponseRecorder();

    private final ObjectMapper mapper;
    /** Key = "METHOD::url" → endpoint record. Insertion-ordered. */
    private final Map<String, Map<String, Object>> entries = new LinkedHashMap<>();
    /** Keys of entries whose response body changed in THIS run. */
    private final Set<String> changedKeys = ConcurrentHashMap.newKeySet();

    private boolean fileLoaded = false;

    private ApiResponseRecorder() {
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public static ApiResponseRecorder getInstance() {
        return INSTANCE;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Resets the recorder to an empty state — discards all in-memory entries
     * and clears the changed-keys set. Useful when starting a fresh test run
     * to prevent accumulation of stale data from previous runs.
     * 
     * Also resets the fileLoaded flag to false, allowing a fresh load if needed.
     */
    public synchronized void reset() {
        entries.clear();
        changedKeys.clear();
        fileLoaded = false;
        log.info("🔄 ApiResponseRecorder reset — recording fresh");
    }

    /**
     * Records one HTTP exchange using per-endpoint deduplication.
     *
     * @return {@code true} if this is a new endpoint or the body changed
     */
    public boolean record(String method, String url, int statusCode, Object parsedBody) {
        String key = method + "::" + url;

        Map<String, Object> existing = entries.get(key);

        if (existing == null) {
            // New endpoint — always save
            entries.put(key, buildEntry(method, url, statusCode, parsedBody, true));
            changedKeys.add(key);
            log.info("📝 New endpoint recorded: {} {}", method, url);
            return true;
        }

        // Compare existing body vs new body
        boolean changed = !bodiesMatch(existing.get("responseBody"), parsedBody);
        existing.put("lastCapturedAt", Instant.now().toString());

        if (changed) {
            existing.put("responseBody", parsedBody);
            existing.put("statusCode", statusCode);
            existing.put("changed", true);
            changedKeys.add(key);
            log.warn("🔄 Response changed for {} {} — will trigger healing", method, url);
        } else {
            existing.put("changed", false);
            log.debug("✓ No change detected for {} {}", method, url);
        }
        return changed;
    }

    /** Returns {@code true} if any endpoint's response changed in this run. */
    public boolean hasChanges() {
        return !changedKeys.isEmpty();
    }

    /** Returns all changed endpoint keys as a human-readable string. */
    public Set<String> getChangedKeys() {
        return Collections.unmodifiableSet(changedKeys);
    }

    /**
     * Loads existing data from the given file path into memory.
     * Call once at the start of the test run.
     */
    public synchronized void loadFrom(String filePath) {
        if (fileLoaded)
            return;
        fileLoaded = true;
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            log.info("actual-api-response.json not found — will create on first save.");
            return;
        }
        try {
            JsonNode root = mapper.readTree(path.toFile());
            JsonNode endpointsNode = root.path("endpoints");
            if (endpointsNode.isArray()) {
                for (JsonNode node : endpointsNode) {
                    String key = node.path("method").asText()
                            + "::" + node.path("url").asText();
                    Map<String, Object> entry = mapper.convertValue(
                            node, mapper.getTypeFactory()
                                    .constructMapType(LinkedHashMap.class, String.class, Object.class));
                    entry.put("changed", false); // reset per-run flag
                    entries.put(key, entry);
                }
                log.info("Loaded {} endpoint(s) from actual-api-response.json", entries.size());
            }
        } catch (Exception e) {
            log.warn("Could not load actual-api-response.json: {}", e.getMessage());
        }
    }

    /**
     * Writes the current state to the given file path.
     * Uses pretty-printing; only writes if something has changed.
     */
    public void save(String filePath) throws IOException {
        Path dest = Paths.get(filePath);
        Files.createDirectories(dest.getParent());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("generatedAt", Instant.now().toString());
        summary.put("totalEndpoints", entries.size());
        summary.put("changedCount", changedKeys.size());
        summary.put("endpoints", new ArrayList<>(entries.values()));

        String pretty = mapper.writeValueAsString(summary);
        Files.writeString(dest, pretty, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        log.info("💾 Saved {} endpoint(s) to {}", entries.size(), filePath);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> buildEntry(String method, String url,
            int statusCode, Object body, boolean changed) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("url", url);
        m.put("method", method);
        m.put("lastCapturedAt", Instant.now().toString());
        m.put("statusCode", statusCode);
        m.put("changed", changed);
        m.put("responseBody", body);
        return m;
    }

    /**
     * Deep-compares two response bodies (already parsed as Java objects).
     * Converts both to canonical JSON strings for reliable equality check.
     */
    private boolean bodiesMatch(Object a, Object b) {
        try {
            // Re-serialize both through Jackson so field order is canonical
            String jsonA = mapper.writeValueAsString(a);
            String jsonB = mapper.writeValueAsString(b);
            JsonNode nodeA = mapper.readTree(jsonA);
            JsonNode nodeB = mapper.readTree(jsonB);
            return nodeA.equals(nodeB);
        } catch (Exception e) {
            return Objects.equals(a, b);
        }
    }
}
