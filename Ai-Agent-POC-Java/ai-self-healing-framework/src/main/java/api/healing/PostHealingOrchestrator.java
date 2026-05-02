package api.healing;

import api.ApiSelfHealer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extends the existing self-healing workflow after expected JSON has already
 * been updated.
 */
public class PostHealingOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PostHealingOrchestrator.class);

    private final JsonDiffEngine jsonDiffEngine;
    private final SchemaManager schemaManager;
    private final DependencyGraphBuilder dependencyGraphBuilder;
    private final FeatureFileUpdater featureFileUpdater;
    private final AstRefactoringEngine astRefactoringEngine;

    public PostHealingOrchestrator() {
        this.jsonDiffEngine = new JsonDiffEngine();
        this.schemaManager = new SchemaManager();
        this.dependencyGraphBuilder = new DependencyGraphBuilder();
        this.featureFileUpdater = new FeatureFileUpdater();
        this.astRefactoringEngine = new AstRefactoringEngine();
    }

    public HealingExtensionReport reconcile(ApiSelfHealer.HealingSession session) {
        if (session == null || session.updatedExpectedJson() == null || session.updatedExpectedJson().isBlank()) {
            return HealingExtensionReport.noop();
        }

        JsonDiffResult diff = jsonDiffEngine.compareJson(session.previousExpectedJson(), session.updatedExpectedJson());
        schemaManager.syncHealedContract(
                session.endpointUrl(),
                session.method(),
                session.expectedResource(),
                session.expectedStatus(),
                session.updatedExpectedJson(),
                diff);

        DependencyGraph dependencyGraph = dependencyGraphBuilder.build(diff.trackedFields());
        FeatureFileUpdater.FeatureUpdateReport featureReport = featureFileUpdater.apply(diff, dependencyGraph);
        AstRefactoringEngine.AstRefactoringReport astReport = astRefactoringEngine.apply(diff, dependencyGraph);

        log.info("Post-heal reconciliation complete. {}", diff.summary());
        return new HealingExtensionReport(diff, dependencyGraph, featureReport, astReport);
    }

    public SchemaManager schemaManager() {
        return schemaManager;
    }

    public record HealingExtensionReport(JsonDiffResult diffResult,
                                         DependencyGraph dependencyGraph,
                                         FeatureFileUpdater.FeatureUpdateReport featureReport,
                                         AstRefactoringEngine.AstRefactoringReport astReport) {
        public static HealingExtensionReport noop() {
            return new HealingExtensionReport(
                    new JsonDiffResult(java.util.List.of(), java.util.List.of(), java.util.Map.of(), java.util.Map.of()),
                    new DependencyGraph(),
                    new FeatureFileUpdater.FeatureUpdateReport(java.util.Set.of()),
                    new AstRefactoringEngine.AstRefactoringReport(java.util.Set.of()));
        }

        public boolean changedAnything() {
            return !diffResult.isEmpty() || featureReport.changed() || astReport.changed();
        }
    }
}
