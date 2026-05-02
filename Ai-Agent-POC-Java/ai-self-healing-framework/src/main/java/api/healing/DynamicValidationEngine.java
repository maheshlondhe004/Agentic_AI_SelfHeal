package api.healing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * Generic response validation that resolves aliases and fallbacks from
 * schema.json instead of hardcoded field names.
 */
public class DynamicValidationEngine {

    private static final Logger log = LoggerFactory.getLogger(DynamicValidationEngine.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final SchemaManager schemaManager;

    public DynamicValidationEngine() {
        this(new SchemaManager());
    }

    public DynamicValidationEngine(SchemaManager schemaManager) {
        this.schemaManager = schemaManager;
    }

    public ValidationResult validate(String endpointUrl, String method, String expectedResource, String actualJson) {
        try {
            JsonNode expected = schemaManager.loadExpectedResponse(endpointUrl, method, expectedResource);
            JsonNode actual = mapper.readTree(actualJson);

            Map<String, String[]> diff = new LinkedHashMap<>();
            compareNodes("", expected, actual, actual, endpointUrl, method, diff);

            if (diff.isEmpty()) {
                log.info("Dynamic validation matched schema contract for {} {}", method, endpointUrl);
            } else {
                log.warn("Dynamic validation found {} mismatch(es) for {} {}", diff.size(), method, endpointUrl);
            }

            return new ValidationResult(diff.isEmpty(),
                    mapper.writeValueAsString(expected),
                    actualJson,
                    diff);
        } catch (IOException e) {
            throw new IllegalStateException("Dynamic validation failed for " + endpointUrl, e);
        }
    }

    private void compareNodes(String path,
                              JsonNode expected,
                              JsonNode actualNodeAtPath,
                              JsonNode actualRoot,
                              String endpointUrl,
                              String method,
                              Map<String, String[]> diff) {
        if (expected == null) {
            return;
        }

        if (expected.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = expected.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String childPath = path.isEmpty() ? field.getKey() : path + "." + field.getKey();
                JsonNode childActual = actualNodeAtPath != null ? actualNodeAtPath.get(field.getKey()) : null;
                if (isMissing(childActual)) {
                    childActual = resolveFallbackNode(actualRoot, endpointUrl, method, childPath);
                }
                if (isMissing(childActual)) {
                    diff.put(childPath, new String[]{field.getValue().toString(), "<missing>"});
                } else {
                    compareNodes(childPath, field.getValue(), childActual, actualRoot, endpointUrl, method, diff);
                }
            }
            return;
        }

        if (expected.isArray()) {
            if (actualNodeAtPath == null || !actualNodeAtPath.isArray()) {
                diff.put(path, new String[]{expected.toString(), actualNodeAtPath == null ? "<missing>" : actualNodeAtPath.toString()});
                return;
            }

            if (expected.size() != actualNodeAtPath.size()) {
                diff.put(path + ".length", new String[]{String.valueOf(expected.size()), String.valueOf(actualNodeAtPath.size())});
                return;
            }

            for (int i = 0; i < expected.size(); i++) {
                compareNodes(path + "[" + i + "]", expected.get(i), actualNodeAtPath.get(i), actualRoot, endpointUrl, method, diff);
            }
            return;
        }

        String expectedValue = expected.toString();
        String actualValue = actualNodeAtPath == null ? "<missing>" : actualNodeAtPath.toString();
        if (!expectedValue.equals(actualValue)) {
            diff.put(path, new String[]{expectedValue, actualValue});
        }
    }

    public String extractDynamicField(String endpointUrl, String method, String actualJson, String logicalField) {
        try {
            JsonNode actual = mapper.readTree(actualJson);
            for (String candidate : schemaManager.resolveCandidatePaths(endpointUrl, method, logicalField)) {
                JsonNode resolved = extractNode(actual, candidate);
                if (!isMissing(resolved) && !resolved.asText().isBlank()) {
                    return resolved.asText();
                }
            }
            return null;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to extract field '" + logicalField + "'", e);
        }
    }

    private JsonNode resolveFallbackNode(JsonNode actualRoot, String endpointUrl, String method, String expectedPath) {
        for (String fallback : schemaManager.resolveFallbackPaths(endpointUrl, method, expectedPath)) {
            JsonNode node = extractNode(actualRoot, fallback);
            if (!isMissing(node)) {
                return node;
            }
        }
        return null;
    }

    private JsonNode extractNode(JsonNode root, String path) {
        if (root == null || path == null || path.isBlank()) {
            return null;
        }
        JsonNode current = root;
        for (String token : tokenize(path)) {
            if (current == null) {
                return null;
            }
            if (token.startsWith("[") && token.endsWith("]")) {
                if (!current.isArray()) {
                    return null;
                }
                int index = Integer.parseInt(token.substring(1, token.length() - 1));
                current = current.get(index);
            } else {
                current = current.get(token);
            }
        }
        return current;
    }

    private List<String> tokenize(String path) {
        List<String> tokens = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            char ch = path.charAt(i);
            if (ch == '.') {
                if (buffer.length() > 0) {
                    tokens.add(buffer.toString());
                    buffer.setLength(0);
                }
                continue;
            }
            if (ch == '[') {
                if (buffer.length() > 0) {
                    tokens.add(buffer.toString());
                    buffer.setLength(0);
                }
                int end = path.indexOf(']', i);
                tokens.add(path.substring(i, end + 1));
                i = end;
                continue;
            }
            buffer.append(ch);
        }
        if (buffer.length() > 0) {
            tokens.add(buffer.toString());
        }
        return tokens;
    }

    private boolean isMissing(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull();
    }

    public record ValidationResult(boolean matched,
                                   String expectedJson,
                                   String actualJson,
                                   Map<String, String[]> diff) {
        public String firstFailedField() {
            return diff.isEmpty() ? null : diff.keySet().iterator().next();
        }
    }
}
