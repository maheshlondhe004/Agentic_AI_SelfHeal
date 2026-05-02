package api.healing;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import io.cucumber.gherkin.GherkinParser;
import io.cucumber.messages.types.Background;
import io.cucumber.messages.types.Envelope;
import io.cucumber.messages.types.FeatureChild;
import io.cucumber.messages.types.GherkinDocument;
import io.cucumber.messages.types.RuleChild;
import io.cucumber.messages.types.Scenario;
import io.cucumber.messages.types.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Builds a field-to-usage graph across feature files, step definitions, and
 * page classes.
 */
public class DependencyGraphBuilder {

    private static final Logger log = LoggerFactory.getLogger(DependencyGraphBuilder.class);

    private static final Path FEATURES_ROOT = Paths.get("src/test/resources/features");
    private static final Path STEPS_ROOT = Paths.get("src/test/java/steps");
    private static final Path PAGES_ROOT = Paths.get("src/main/java/pages");

    public DependencyGraph build(Set<String> trackedFields) {
        DependencyGraph graph = new DependencyGraph();
        if (trackedFields.isEmpty()) {
            return graph;
        }

        collectFeatureUsages(graph, trackedFields);
        collectJavaUsages(graph, trackedFields, STEPS_ROOT, "step");
        collectJavaUsages(graph, trackedFields, PAGES_ROOT, "page");

        log.info("Dependency graph built for {} tracked field(s); {} usage(s) found.",
                trackedFields.size(), graph.usageCount());
        return graph;
    }

    private void collectFeatureUsages(DependencyGraph graph, Set<String> trackedFields) {
        if (!Files.exists(FEATURES_ROOT)) {
            return;
        }

        GherkinParser parser = GherkinParser.builder().includeGherkinDocument(true).build();

        try (Stream<Path> files = Files.walk(FEATURES_ROOT)) {
            files.filter(path -> path.toString().endsWith(".feature")).forEach(path -> {
                try {
                    Optional<GherkinDocument> document = parser.parse(path)
                            .map(Envelope::getGherkinDocument)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .findFirst();

                    document.ifPresent(gherkinDocument -> visitFeature(graph, trackedFields, path, gherkinDocument));
                } catch (IOException e) {
                    log.warn("Unable to parse feature file {}: {}", path, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("Unable to walk feature directory {}: {}", FEATURES_ROOT, e.getMessage());
        }
    }

    private void visitFeature(DependencyGraph graph,
                              Set<String> trackedFields,
                              Path file,
                              GherkinDocument document) {
        document.getFeature().ifPresent(feature -> {
            for (FeatureChild child : feature.getChildren()) {
                child.getBackground().ifPresent(background -> collectStepUsages(graph, trackedFields, file, background.getSteps()));
                child.getScenario().ifPresent(scenario -> collectStepUsages(graph, trackedFields, file, scenario.getSteps()));
                child.getRule().ifPresent(rule -> rule.getChildren().forEach(ruleChild -> {
                    ruleChild.getBackground().ifPresent(background -> collectStepUsages(graph, trackedFields, file, background.getSteps()));
                    ruleChild.getScenario().ifPresent(scenario -> collectStepUsages(graph, trackedFields, file, scenario.getSteps()));
                }));
            }
        });
    }

    private void collectStepUsages(DependencyGraph graph,
                                   Set<String> trackedFields,
                                   Path file,
                                   List<Step> steps) {
        for (Step step : steps) {
            for (String field : trackedFields) {
                if (matches(step.getText(), field)) {
                            graph.addUsage(field, new DependencyGraph.Usage(
                            field,
                            "feature",
                            file,
                            Math.toIntExact(step.getLocation().getLine()),
                            step.getKeyword() + step.getText()));
                }
            }
        }
    }

    private void collectJavaUsages(DependencyGraph graph,
                                   Set<String> trackedFields,
                                   Path root,
                                   String layer) {
        if (!Files.exists(root)) {
            return;
        }

        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    CompilationUnit unit = StaticJavaParser.parse(path);
                    for (StringLiteralExpr literal : unit.findAll(StringLiteralExpr.class)) {
                        String value = literal.getValue();
                        for (String field : trackedFields) {
                            if (matches(value, field)) {
                                int line = literal.getBegin().map(position -> position.line).orElse(1);
                                graph.addUsage(field, new DependencyGraph.Usage(field, layer, path, line, value));
                            }
                        }
                    }
                } catch (java.lang.Exception e) {
                    log.warn("Unable to parse Java source {}: {}", path, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("Unable to walk Java directory {}: {}", root, e.getMessage());
        }
    }

    private boolean matches(String candidate, String field) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        String leaf = leaf(field);
        return candidate.contains(field) || (!leaf.equals(field) && candidate.contains(leaf));
    }

    private String leaf(String path) {
        int dot = path.lastIndexOf('.');
        int bracket = path.lastIndexOf('[');
        int index = Math.max(dot, bracket);
        return index < 0 ? path : path.substring(index + 1).replace("]", "");
    }
}
