package core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes logical element locators from/to {@code locators.json}.
 * <p>
 * Each entry holds: {@code id}, {@code primary} selector, {@code alternatives},
 * and a human-readable {@code description}.
 * </p>
 */
public class LocatorStore {

    private static final Logger log = LoggerFactory.getLogger(LocatorStore.class);
    private static final String RESOURCE_PATH = "locators.json";
    private static final String FILE_PATH = "src/main/resources/locators.json";

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, LocatorEntry> store;
    private final Path storePath;

    public LocatorStore() {
        this.store = loadLocators();
        this.storePath = Paths.get(FILE_PATH);
    }

    private Map<String, LocatorEntry> loadLocators() {
        // 1. Classpath (jar deployments / test resources)
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (is != null) {
                Map<String, LocatorEntry> loaded = mapper.readValue(is, new TypeReference<>() {
                });
                log.info("Loaded {} locators from classpath.", loaded.size());
                return loaded;
            }
        } catch (IOException e) {
            log.warn("Could not read locators.json from classpath: {}", e.getMessage());
        }
        // 2. File system
        try {
            Map<String, LocatorEntry> loaded = mapper.readValue(Paths.get(FILE_PATH).toFile(), new TypeReference<>() {
            });
            log.info("Loaded {} locators from filesystem.", loaded.size());
            return loaded;
        } catch (IOException e) {
            log.warn("Could not read locators.json from filesystem — starting empty store.");
            return new LinkedHashMap<>();
        }
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public String getPrimary(String elementId) {
        LocatorEntry e = store.get(elementId);
        if (e == null)
            throw new IllegalArgumentException("Unknown element id: " + elementId);
        return e.primary;
    }

    public List<String> getAlternatives(String elementId) {
        LocatorEntry e = store.get(elementId);
        if (e == null || e.alternatives == null)
            return List.of();
        return e.alternatives;
    }

    /**
     * Returns all selectors in priority order: primary first, then alternatives.
     */
    public List<String> getAllSelectors(String elementId) {
        List<String> all = new ArrayList<>();
        all.add(getPrimary(elementId));
        all.addAll(getAlternatives(elementId));
        return all;
    }

    // ── Write (healing) ───────────────────────────────────────────────────────

    /**
     * Promotes {@code newPrimary}, demotes old primary to front of alternatives,
     * and persists the update to disk.
     */
    public void healPrimary(String elementId, String newPrimary) throws IOException {
        LocatorEntry entry = store.computeIfAbsent(elementId, id -> {
            LocatorEntry e = new LocatorEntry();
            e.id = id;
            e.alternatives = new ArrayList<>();
            return e;
        });
        if (entry.alternatives == null)
            entry.alternatives = new ArrayList<>();
        if (entry.primary != null && !entry.primary.equals(newPrimary))
            entry.alternatives.add(0, entry.primary);
        entry.primary = newPrimary;
        log.info("Healed locator [{}]: new primary = '{}'", elementId, newPrimary);
        persist();
    }

    private void persist() throws IOException {
        Files.createDirectories(storePath.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(storePath.toFile(), store);
        log.info("locators.json updated at {}", storePath.toAbsolutePath());
    }

    // ── Inner model ───────────────────────────────────────────────────────────

    public static class LocatorEntry {
        public String id;
        public String primary;
        public List<String> alternatives;
        public String description;
    }
}
