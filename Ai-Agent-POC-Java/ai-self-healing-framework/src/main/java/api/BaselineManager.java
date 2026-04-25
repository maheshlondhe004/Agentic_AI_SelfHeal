package api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Manages baseline API responses on disk.
 *
 * <p>
 * Responsibilities:
 * <ul>
 * <li>Check if a baseline exists for an endpoint</li>
 * <li>Load existing baseline data</li>
 * <li>Save new baseline on first-time execution</li>
 * <li>Update baseline after AI healing</li>
 * <li>Organize baseline files by endpoint (optional compression)</li>
 * </ul>
 * </p>
 */
public class BaselineManager {

    private static final Logger log = LoggerFactory.getLogger(BaselineManager.class);
    private static final String BASELINE_DIR = "src/test/resources/baselines";

    private final ObjectMapper mapper;

    public BaselineManager() {
        this.mapper = new ObjectMapper();
        ensureBaselineDir();
    }

    /**
     * Checks if a baseline exists for the given endpoint key.
     *
     * @param endpointKey endpoint identifier (e.g. {@code "GET::http://localhost:3000/login"})
     * @return true if baseline JSON file exists
     */
    public boolean baselineExists(String endpointKey) {
        Path path = getBaselinePath(endpointKey);
        boolean exists = Files.exists(path);
        log.debug("Baseline check [{}]: {}", endpointKey, exists ? "exists" : "not found");
        return exists;
    }

    /**
     * Loads the baseline JSON response for an endpoint.
     *
     * @param endpointKey endpoint identifier
     * @return JSON string of the stored baseline
     * @throws IOException if file cannot be read
     */
    public String loadBaseline(String endpointKey) throws IOException {
        Path path = getBaselinePath(endpointKey);
        String json = Files.readString(path, StandardCharsets.UTF_8);
        log.info("Loaded baseline for [{}]: {} bytes", endpointKey, json.length());
        return json;
    }

    /**
     * Saves a response as the baseline for an endpoint (first-time or after healing).
     *
     * @param endpointKey  endpoint identifier
     * @param responseBody JSON response body
     * @throws IOException if file cannot be written
     */
    public void saveBaseline(String endpointKey, String responseBody) throws IOException {
        Path path = getBaselinePath(endpointKey);
        Files.createDirectories(path.getParent());
        Files.writeString(path, responseBody, StandardCharsets.UTF_8);
        log.info("Saved baseline for [{}]: {} bytes", endpointKey, responseBody.length());
    }

    /**
     * Gets the file path for an endpoint's baseline.
     *
     * @param endpointKey endpoint identifier
     * @return Path to the baseline JSON file
     */
    private Path getBaselinePath(String endpointKey) {
        // Convert endpoint key to safe filename (replace special chars)
        String safeKey = endpointKey
                .replace("::", "_")
                .replace("http://", "")
                .replace("https://", "")
                .replace("/", "_")
                .concat(".json");
        return Paths.get(BASELINE_DIR, safeKey);
    }

    private void ensureBaselineDir() {
        try {
            Path dir = Paths.get(BASELINE_DIR);
            Files.createDirectories(dir);
            log.debug("Baseline directory ready: {}", dir.toAbsolutePath());
        } catch (IOException e) {
            log.warn("Could not create baseline directory: {}", e.getMessage());
        }
    }

    /**
     * Cleanup: Removes baseline files for testing purposes.
     * Use with caution!
     */
    public void deleteAllBaselines() throws IOException {
        Path dir = Paths.get(BASELINE_DIR);
        if (Files.exists(dir)) {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                            log.debug("Deleted: {}", p);
                        } catch (IOException e) {
                            log.warn("Could not delete {}: {}", p, e.getMessage());
                        }
                    });
            log.info("Baselines cleaned up");
        }
    }

    /**
     * Lists all stored baseline endpoints.
     *
     * @return list of endpoint keys
     */
    public List<String> listBaselines() throws IOException {
        Path dir = Paths.get(BASELINE_DIR);
        List<String> keys = new ArrayList<>();
        if (Files.exists(dir)) {
            Files.list(dir)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> {
                        String filename = p.getFileName().toString();
                        // Reverse the filename transformation: GET_localhost_3000_login.json → GET::localhost/3000/login
                        String key = filename.replace(".json", "")
                                .replaceFirst("_", "::") // Restore :: after method (first occurrence only)
                                .replace("_", "/"); // Restore path separators
                        keys.add(key);
                    });
        }
        return keys;
    }
}
