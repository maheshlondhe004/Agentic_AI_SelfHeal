package api.healing;

import java.nio.file.Path;
import java.util.*;

/**
 * Maps changed fields to concrete usages across features, steps, and pages.
 */
public class DependencyGraph {

    private final Map<String, List<Usage>> usagesByField = new LinkedHashMap<>();

    public void addUsage(String field, Usage usage) {
        usagesByField.computeIfAbsent(field, ignored -> new ArrayList<>()).add(usage);
    }

    public Map<String, List<Usage>> usagesByField() {
        LinkedHashMap<String, List<Usage>> copy = new LinkedHashMap<>();
        usagesByField.forEach((field, usages) -> copy.put(field, List.copyOf(usages)));
        return Collections.unmodifiableMap(copy);
    }

    public Set<Path> filesForLayer(String layer) {
        LinkedHashSet<Path> files = new LinkedHashSet<>();
        usagesByField.values().forEach(usages ->
                usages.stream()
                        .filter(usage -> usage.layer().equals(layer))
                        .map(Usage::file)
                        .forEach(files::add));
        return files;
    }

    public int usageCount() {
        return usagesByField.values().stream().mapToInt(List::size).sum();
    }

    public record Usage(String field, String layer, Path file, int line, String snippet) {
    }
}
