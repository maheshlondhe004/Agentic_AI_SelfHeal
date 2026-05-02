package api.healing;

import io.cucumber.gherkin.GherkinParser;
import io.cucumber.messages.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Applies AST-guided updates to feature files after healing.
 */
public class FeatureFileUpdater {

    private static final Logger log = LoggerFactory.getLogger(FeatureFileUpdater.class);

    private static final String GENERIC_CONTRACT_STEP = "the API response should match the expected JSON contract";

    public FeatureUpdateReport apply(JsonDiffResult diffResult, DependencyGraph graph) {
        LinkedHashSet<Path> changedFiles = new LinkedHashSet<>();
        Set<Path> files = new LinkedHashSet<>(graph.filesForLayer("feature"));

        if (Files.exists(Paths.get("src/test/resources/features/user-api.feature"))) {
            files.add(Paths.get("src/test/resources/features/user-api.feature"));
        }

        for (Path file : files) {
            if (updateFeatureFile(file, diffResult)) {
                changedFiles.add(file);
            }
        }

        return new FeatureUpdateReport(changedFiles);
    }

    private boolean updateFeatureFile(Path featurePath, JsonDiffResult diffResult) {
        if (!Files.exists(featurePath)) {
            return false;
        }

        GherkinParser parser = GherkinParser.builder().includeGherkinDocument(true).build();

        try {
            Optional<GherkinDocument> document = parser.parse(featurePath)
                    .map(Envelope::getGherkinDocument)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst();

            if (document.isEmpty()) {
                return false;
            }

            List<String> lines = Files.readAllLines(featurePath, StandardCharsets.UTF_8);
            boolean changed = false;
            changed |= stabilizeFieldSpecificSteps(document.get(), lines);
            changed |= applyRenameUpdates(document.get(), lines, diffResult);

            if (changed) {
                Files.write(featurePath, lines, StandardCharsets.UTF_8,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.CREATE);
                log.info("Feature file AST update applied: {}", featurePath.toAbsolutePath());
            }
            return changed;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to update feature file " + featurePath, e);
        }
    }

    private boolean stabilizeFieldSpecificSteps(GherkinDocument document, List<String> lines) {
        List<Step> steps = collectAllSteps(document);
        LinkedHashSet<Integer> duplicateLines = new LinkedHashSet<>();
        Integer anchorLine = null;
        String anchorKeyword = "And ";

        for (Step step : steps) {
            if (!isLegacyFieldValidation(step.getText())) {
                continue;
            }
            int lineIndex = Math.toIntExact(step.getLocation().getLine() - 1);
            if (anchorLine == null) {
                anchorLine = lineIndex;
                anchorKeyword = step.getKeyword();
            } else {
                duplicateLines.add(lineIndex);
            }
        }

        if (anchorLine == null) {
            return false;
        }

        String originalLine = lines.get(anchorLine);
        String indent = originalLine.substring(0, Math.max(0, originalLine.indexOf(anchorKeyword)));
        lines.set(anchorLine, indent + anchorKeyword + GENERIC_CONTRACT_STEP);
        duplicateLines.stream().sorted(Comparator.reverseOrder()).forEach(lines::remove);
        return true;
    }

    private boolean applyRenameUpdates(GherkinDocument document, List<String> lines, JsonDiffResult diffResult) {
        boolean changed = false;
        for (Step step : collectAllSteps(document)) {
            int lineIndex = Math.toIntExact(step.getLocation().getLine() - 1);
            String updated = lines.get(lineIndex);
            for (Map.Entry<String, String> rename : diffResult.renamed().entrySet()) {
                updated = replaceFieldToken(updated, rename.getKey(), rename.getValue());
            }
            if (!updated.equals(lines.get(lineIndex))) {
                lines.set(lineIndex, updated);
                changed = true;
            }
        }
        return changed;
    }

    private List<Step> collectAllSteps(GherkinDocument document) {
        List<Step> steps = new ArrayList<>();
        document.getFeature().ifPresent(feature -> feature.getChildren().forEach(child -> {
            child.getBackground().ifPresent(background -> steps.addAll(background.getSteps()));
            child.getScenario().ifPresent(scenario -> steps.addAll(scenario.getSteps()));
            child.getRule().ifPresent(rule -> rule.getChildren().forEach(ruleChild -> {
                ruleChild.getBackground().ifPresent(background -> steps.addAll(background.getSteps()));
                ruleChild.getScenario().ifPresent(scenario -> steps.addAll(scenario.getSteps()));
            }));
        }));
        return steps;
    }

    private boolean isLegacyFieldValidation(String stepText) {
        return stepText.startsWith("the response should contain field ")
                && stepText.endsWith(" with valid data");
    }

    private String replaceFieldToken(String source, String oldField, String newField) {
        String updated = replaceExact(source, oldField, newField);
        updated = replaceExact(updated, leaf(oldField), leaf(newField));
        return updated;
    }

    private String replaceExact(String source, String oldValue, String newValue) {
        int index = source.indexOf("\"" + oldValue + "\"");
        if (index >= 0) {
            return source.substring(0, index + 1) + newValue + source.substring(index + oldValue.length() + 1);
        }
        return source;
    }

    private String leaf(String path) {
        int dot = path.lastIndexOf('.');
        int bracket = path.lastIndexOf('[');
        int index = Math.max(dot, bracket);
        return index < 0 ? path : path.substring(index + 1).replace("]", "");
    }

    public record FeatureUpdateReport(Set<Path> changedFiles) {
        public boolean changed() {
            return !changedFiles.isEmpty();
        }
    }
}
