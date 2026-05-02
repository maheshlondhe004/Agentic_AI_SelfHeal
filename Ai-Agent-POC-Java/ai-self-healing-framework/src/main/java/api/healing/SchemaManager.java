package api.healing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Maintains schema.json as the single source of truth for dynamic response
 * validation and compatibility fallbacks.
 */
public class SchemaManager {

    private static final Logger log = LoggerFactory.getLogger(SchemaManager.class);

    private static final String SCHEMA_RESOURCE = "schema.json";
    private static final Path SCHEMA_PATH = Paths.get("src/test/resources/schema.json");
    private static final Path TEST_RESOURCES_ROOT = Paths.get("src/test/resources");

    private final ObjectMapper mapper = new ObjectMapper();

    public synchronized void syncHealedContract(String endpointUrl,
                                                String method,
                                                String expectedResource,
                                                int expectedStatus,
                                                String healedExpectedJson,
                                                JsonDiffResult diffResult) {
        try {
            SchemaDocument schema = loadSchema();
            String actualPath = extractPath(endpointUrl);
            String normalizedPath = normalizePath(actualPath);

            EndpointSchema endpoint = findExact(schema, method, actualPath)
                    .orElseGet(() -> findExact(schema, method, normalizedPath)
                            .orElseGet(() -> createEndpoint(schema, method, actualPath, expectedResource, expectedStatus)));

            endpoint.method = method;
            endpoint.path = actualPath;
            endpoint.expectedResource = expectedResource;
            endpoint.expectedStatus = expectedStatus;
            endpoint.responseBody = mapper.readTree(healedExpectedJson);
            updateCompatibilityMetadata(endpoint, diffResult);

            Optional<EndpointSchema> templateSchema = findExact(schema, method, normalizedPath);
            templateSchema.ifPresent(template -> updateCompatibilityMetadata(template, diffResult));

            writeSchema(schema);
            syncExpectedResource(actualPath, method, expectedStatus, expectedResource, endpoint.responseBody);
            log.info("Schema SSOT updated for {} {}", method, actualPath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to sync schema.json", e);
        }
    }

    public synchronized JsonNode loadExpectedResponse(String endpointUrl,
                                                      String method,
                                                      String expectedResource) {
        try {
            String actualPath = extractPath(endpointUrl);
            SchemaDocument schema = loadSchema();

            Optional<EndpointSchema> exact = findExact(schema, method, actualPath);
            if (exact.isPresent() && exact.get().responseBody != null) {
                return exact.get().responseBody.deepCopy();
            }

            Optional<EndpointSchema> template = findExact(schema, method, normalizePath(actualPath));
            if (template.isPresent() && template.get().responseBody != null) {
                return template.get().responseBody.deepCopy();
            }

            JsonNode resourceRoot = loadResourceJson(expectedResource);
            if (resourceRoot != null && resourceRoot.isArray()) {
                for (JsonNode entry : resourceRoot) {
                    if (actualPath.equals(entry.path("url").asText())
                            && method.equalsIgnoreCase(entry.path("method").asText())) {
                        return entry.path("responseBody").deepCopy();
                    }
                }
            }
            if (resourceRoot != null && resourceRoot.isObject()) {
                return resourceRoot.deepCopy();
            }
            return mapper.createObjectNode();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load expected response for " + endpointUrl, e);
        }
    }

    public synchronized List<String> resolveCandidatePaths(String endpointUrl, String method, String requestedField) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(requestedField);

        EndpointSchema endpoint = findBestMatch(loadSchema(), method, extractPath(endpointUrl)).orElse(null);
        if (endpoint == null) {
            return new ArrayList<>(candidates);
        }

        endpoint.fieldAliases.forEach((logicalField, aliases) -> {
            if (logicalField.equals(requestedField) || leaf(logicalField).equals(requestedField)) {
                candidates.addAll(aliases);
            }
            for (String alias : aliases) {
                if (alias.equals(requestedField) || leaf(alias).equals(requestedField)) {
                    candidates.addAll(aliases);
                    candidates.add(logicalField);
                }
            }
        });

        endpoint.fallbacks.forEach((sourcePath, fallbackPaths) -> {
            if (sourcePath.equals(requestedField) || leaf(sourcePath).equals(requestedField)) {
                candidates.addAll(fallbackPaths);
            }
            for (String fallback : fallbackPaths) {
                if (fallback.equals(requestedField) || leaf(fallback).equals(requestedField)) {
                    candidates.add(sourcePath);
                    candidates.addAll(fallbackPaths);
                }
            }
        });

        return new ArrayList<>(candidates);
    }

    public synchronized List<String> resolveFallbackPaths(String endpointUrl, String method, String expectedPath) {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        paths.add(expectedPath);

        EndpointSchema endpoint = findBestMatch(loadSchema(), method, extractPath(endpointUrl)).orElse(null);
        if (endpoint == null) {
            return new ArrayList<>(paths);
        }

        List<String> directFallbacks = endpoint.fallbacks.get(expectedPath);
        if (directFallbacks != null) {
            paths.addAll(directFallbacks);
        }

        endpoint.fieldAliases.forEach((logicalField, aliases) -> {
            if (aliases.contains(expectedPath)) {
                paths.addAll(aliases);
                paths.add(logicalField);
            }
        });

        return new ArrayList<>(paths);
    }

    private SchemaDocument loadSchema() {
        try {
            if (Files.exists(SCHEMA_PATH)) {
                return mapper.readValue(Files.readString(SCHEMA_PATH, StandardCharsets.UTF_8), SchemaDocument.class);
            }
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(SCHEMA_RESOURCE)) {
                if (is != null) {
                    return mapper.readValue(is.readAllBytes(), SchemaDocument.class);
                }
            }
            return new SchemaDocument();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load schema.json", e);
        }
    }

    private void writeSchema(SchemaDocument document) throws IOException {
        Files.createDirectories(SCHEMA_PATH.getParent());
        Files.writeString(SCHEMA_PATH,
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(document),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void syncExpectedResource(String actualPath,
                                      String method,
                                      int expectedStatus,
                                      String expectedResource,
                                      JsonNode healedBody) throws IOException {
        Path resourcePath = TEST_RESOURCES_ROOT.resolve(expectedResource);
        Files.createDirectories(resourcePath.getParent());

        JsonNode existing = Files.exists(resourcePath)
                ? mapper.readTree(Files.readString(resourcePath, StandardCharsets.UTF_8))
                : null;

        if (existing != null && existing.isArray()) {
            boolean updated = false;
            for (JsonNode node : existing) {
                if (actualPath.equals(node.path("url").asText())
                        && method.equalsIgnoreCase(node.path("method").asText())) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) node).set("responseBody", healedBody.deepCopy());
                    ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("expectedStatus", expectedStatus);
                    updated = true;
                    break;
                }
            }
            if (!updated) {
                com.fasterxml.jackson.databind.node.ObjectNode newNode = mapper.createObjectNode();
                newNode.put("url", actualPath);
                newNode.put("method", method);
                newNode.put("expectedStatus", expectedStatus);
                newNode.set("responseBody", healedBody.deepCopy());
                ((com.fasterxml.jackson.databind.node.ArrayNode) existing).add(newNode);
            }
            Files.writeString(resourcePath,
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(existing),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            return;
        }

        Files.writeString(resourcePath,
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(healedBody),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
    }

    private JsonNode loadResourceJson(String expectedResource) throws IOException {
        Path filePath = TEST_RESOURCES_ROOT.resolve(expectedResource);
        if (Files.exists(filePath)) {
            return mapper.readTree(Files.readString(filePath, StandardCharsets.UTF_8));
        }
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(expectedResource)) {
            if (is != null) {
                return mapper.readTree(is.readAllBytes());
            }
        }
        return null;
    }

    private Optional<EndpointSchema> findBestMatch(SchemaDocument schema, String method, String actualPath) {
        return findExact(schema, method, actualPath)
                .or(() -> findExact(schema, method, normalizePath(actualPath)));
    }

    private Optional<EndpointSchema> findExact(SchemaDocument schema, String method, String path) {
        return schema.endpoints.stream()
                .filter(endpoint -> Objects.equals(method, endpoint.method) && Objects.equals(path, endpoint.path))
                .findFirst();
    }

    private EndpointSchema createEndpoint(SchemaDocument schema,
                                          String method,
                                          String path,
                                          String expectedResource,
                                          int expectedStatus) {
        EndpointSchema endpoint = new EndpointSchema();
        endpoint.method = method;
        endpoint.path = path;
        endpoint.expectedResource = expectedResource;
        endpoint.expectedStatus = expectedStatus;
        schema.endpoints.add(endpoint);
        return endpoint;
    }

    private void updateCompatibilityMetadata(EndpointSchema endpoint, JsonDiffResult diffResult) {
        if (endpoint.fieldAliases == null) {
            endpoint.fieldAliases = new LinkedHashMap<>();
        }
        if (endpoint.fallbacks == null) {
            endpoint.fallbacks = new LinkedHashMap<>();
        }
        if (endpoint.optionalFields == null) {
            endpoint.optionalFields = new ArrayList<>();
        }

        for (Map.Entry<String, String> rename : diffResult.renamed().entrySet()) {
            endpoint.fallbacks.computeIfAbsent(rename.getKey(), key -> new ArrayList<>());
            if (!endpoint.fallbacks.get(rename.getKey()).contains(rename.getValue())) {
                endpoint.fallbacks.get(rename.getKey()).add(rename.getValue());
            }

            updateAliasLists(endpoint.fieldAliases, rename.getKey(), rename.getValue());
            updateAliasLists(endpoint.fieldAliases, leaf(rename.getKey()), rename.getValue());
            updateAliasLists(endpoint.fieldAliases, leaf(rename.getValue()), rename.getValue());
        }

        for (String added : diffResult.added()) {
            if (!endpoint.optionalFields.contains(added)) {
                endpoint.optionalFields.add(added);
            }
        }
    }

    private void updateAliasLists(Map<String, List<String>> fieldAliases, String key, String canonicalPath) {
        List<String> aliases = fieldAliases.computeIfAbsent(key, ignored -> new ArrayList<>());
        if (!aliases.contains(canonicalPath)) {
            aliases.add(canonicalPath);
        }
        if (!aliases.contains(key) && key.contains(".")) {
            aliases.add(key);
        }
    }

    private String extractPath(String endpointUrl) {
        try {
            return URI.create(endpointUrl).getPath();
        } catch (Exception ignored) {
            return endpointUrl;
        }
    }

    private String normalizePath(String rawPath) {
        String[] segments = rawPath.split("/");
        StringBuilder normalized = new StringBuilder();
        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }
            normalized.append('/');
            normalized.append(segment.chars().allMatch(Character::isDigit) ? "{id}" : segment);
        }
        return normalized.length() == 0 ? "/" : normalized.toString();
    }

    private String leaf(String path) {
        int dot = path.lastIndexOf('.');
        int bracket = path.lastIndexOf('[');
        int index = Math.max(dot, bracket);
        return index < 0 ? path : path.substring(index + 1).replace("]", "");
    }

    public static class SchemaDocument {
        public List<EndpointSchema> endpoints = new ArrayList<>();
    }

    public static class EndpointSchema {
        public String method;
        public String path;
        public String expectedResource;
        public int expectedStatus = 200;
        public JsonNode responseBody;
        public Map<String, List<String>> fieldAliases = new LinkedHashMap<>();
        public Map<String, List<String>> fallbacks = new LinkedHashMap<>();
        public List<String> optionalFields = new ArrayList<>();
    }
}
