package api.healing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.*;

/**
 * Builds a structural diff between two JSON documents.
 */
public class JsonDiffEngine {

    private final ObjectMapper mapper = new ObjectMapper();

    public JsonDiffResult compareJson(String oldJson, String newJson) {
        try {
            JsonNode oldRoot = mapper.readTree(oldJson);
            JsonNode newRoot = mapper.readTree(newJson);

            Map<String, JsonNode> oldPaths = new LinkedHashMap<>();
            Map<String, JsonNode> newPaths = new LinkedHashMap<>();
            collectPaths("", oldRoot, oldPaths);
            collectPaths("", newRoot, newPaths);

            LinkedHashSet<String> added = new LinkedHashSet<>();
            LinkedHashSet<String> removed = new LinkedHashSet<>();
            LinkedHashMap<String, JsonDiffResult.TypeChange> typeChanged = new LinkedHashMap<>();

            for (Map.Entry<String, JsonNode> entry : oldPaths.entrySet()) {
                String path = entry.getKey();
                if (!newPaths.containsKey(path)) {
                    removed.add(path);
                    continue;
                }
                String oldType = typeOf(entry.getValue());
                String newType = typeOf(newPaths.get(path));
                if (!oldType.equals(newType)) {
                    typeChanged.put(path, new JsonDiffResult.TypeChange(oldType, newType));
                }
            }

            for (String path : newPaths.keySet()) {
                if (!oldPaths.containsKey(path)) {
                    added.add(path);
                }
            }

            Map<String, String> renamed = detectRenames(removed, added, oldPaths, newPaths);
            removed.removeAll(renamed.keySet());
            added.removeAll(renamed.values());

            return new JsonDiffResult(new ArrayList<>(added), new ArrayList<>(removed), renamed, typeChanged);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to diff JSON documents", e);
        }
    }

    private Map<String, String> detectRenames(Set<String> removed,
                                              Set<String> added,
                                              Map<String, JsonNode> oldPaths,
                                              Map<String, JsonNode> newPaths) {
        LinkedHashMap<String, String> renamed = new LinkedHashMap<>();
        LinkedHashSet<String> claimedAdded = new LinkedHashSet<>();

        for (String oldPath : removed) {
            JsonNode oldNode = oldPaths.get(oldPath);
            String parent = parentPath(oldPath);
            String bestMatch = null;

            for (String newPath : added) {
                if (claimedAdded.contains(newPath)) {
                    continue;
                }
                if (!Objects.equals(parent, parentPath(newPath))) {
                    continue;
                }

                JsonNode newNode = newPaths.get(newPath);
                if (!typeOf(oldNode).equals(typeOf(newNode))) {
                    continue;
                }

                if (Objects.equals(normalizeValue(oldNode), normalizeValue(newNode))
                        || isContainerType(oldNode)) {
                    bestMatch = newPath;
                    break;
                }
            }

            if (bestMatch != null) {
                renamed.put(oldPath, bestMatch);
                claimedAdded.add(bestMatch);
            }
        }

        return renamed;
    }

    private void collectPaths(String path, JsonNode node, Map<String, JsonNode> sink) {
        if (node == null) {
            return;
        }
        if (!path.isEmpty()) {
            sink.put(path, node);
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String childPath = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
                collectPaths(childPath, entry.getValue(), sink);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                String childPath = path + "[" + i + "]";
                collectPaths(childPath, node.get(i), sink);
            }
        }
    }

    private String typeOf(JsonNode node) {
        if (node == null || node.isNull()) {
            return "null";
        }
        if (node.isObject()) {
            return "object";
        }
        if (node.isArray()) {
            return "array";
        }
        if (node.isIntegralNumber() || node.isFloatingPointNumber()) {
            return "number";
        }
        if (node.isBoolean()) {
            return "boolean";
        }
        return "string";
    }

    private boolean isContainerType(JsonNode node) {
        return node != null && (node.isObject() || node.isArray());
    }

    private String normalizeValue(JsonNode node) {
        return node == null ? "<missing>" : node.toString();
    }

    private String parentPath(String path) {
        int dot = path.lastIndexOf('.');
        int bracket = path.lastIndexOf('[');
        int split = Math.max(dot, bracket);
        return split < 0 ? "" : path.substring(0, split);
    }
}
