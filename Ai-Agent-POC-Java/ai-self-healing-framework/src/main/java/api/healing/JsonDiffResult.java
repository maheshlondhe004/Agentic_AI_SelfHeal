package api.healing;

import java.util.*;

/**
 * Structural diff between the previous expected JSON and the healed JSON.
 */
public class JsonDiffResult {

    private final List<String> added;
    private final List<String> removed;
    private final Map<String, String> renamed;
    private final Map<String, TypeChange> typeChanged;

    public JsonDiffResult(List<String> added,
                          List<String> removed,
                          Map<String, String> renamed,
                          Map<String, TypeChange> typeChanged) {
        this.added = List.copyOf(added);
        this.removed = List.copyOf(removed);
        this.renamed = Collections.unmodifiableMap(new LinkedHashMap<>(renamed));
        this.typeChanged = Collections.unmodifiableMap(new LinkedHashMap<>(typeChanged));
    }

    public List<String> added() {
        return added;
    }

    public List<String> removed() {
        return removed;
    }

    public Map<String, String> renamed() {
        return renamed;
    }

    public Map<String, TypeChange> typeChanged() {
        return typeChanged;
    }

    public boolean isEmpty() {
        return added.isEmpty() && removed.isEmpty() && renamed.isEmpty() && typeChanged.isEmpty();
    }

    public Set<String> trackedFields() {
        LinkedHashSet<String> fields = new LinkedHashSet<>();
        fields.addAll(added);
        fields.addAll(removed);
        fields.addAll(renamed.keySet());
        fields.addAll(renamed.values());
        fields.addAll(typeChanged.keySet());
        return fields;
    }

    public String summary() {
        return String.format("added=%s removed=%s renamed=%s typeChanged=%s",
                added, removed, renamed, typeChanged);
    }

    public record TypeChange(String oldType, String newType) {
        @Override
        public String toString() {
            return oldType + "->" + newType;
        }
    }
}
