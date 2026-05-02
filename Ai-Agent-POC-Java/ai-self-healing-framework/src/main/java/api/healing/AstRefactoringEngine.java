package api.healing;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * Uses JavaParser to update string-based field references safely.
 */
public class AstRefactoringEngine {

    private static final Logger log = LoggerFactory.getLogger(AstRefactoringEngine.class);

    public AstRefactoringReport apply(JsonDiffResult diffResult, DependencyGraph graph) {
        LinkedHashSet<Path> changedFiles = new LinkedHashSet<>();
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();
        candidates.addAll(graph.filesForLayer("step"));
        candidates.addAll(graph.filesForLayer("page"));

        for (Path file : candidates) {
            if (refactorJavaFile(file, diffResult)) {
                changedFiles.add(file);
            }
        }

        return new AstRefactoringReport(changedFiles);
    }

    private boolean refactorJavaFile(Path file, JsonDiffResult diffResult) {
        if (!Files.exists(file)) {
            return false;
        }

        try {
            CompilationUnit unit = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(unit);

            boolean[] changed = {false};
            for (StringLiteralExpr literal : unit.findAll(StringLiteralExpr.class)) {
                String updated = literal.getValue();
                for (Map.Entry<String, String> rename : diffResult.renamed().entrySet()) {
                    updated = refactorLiteral(updated, rename.getKey(), rename.getValue());
                }
                if (!updated.equals(literal.getValue())) {
                    literal.setString(updated);
                    changed[0] = true;
                }
            }

            if (changed[0]) {
                Files.writeString(file,
                        LexicalPreservingPrinter.print(unit),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.CREATE);
                log.info("Java AST refactor applied: {}", file.toAbsolutePath());
            }
            return changed[0];
        } catch (Exception e) {
            throw new IllegalStateException("Unable to AST-refactor " + file, e);
        }
    }

    private String refactorLiteral(String source, String oldField, String newField) {
        String updated = replaceFieldAware(source, oldField, newField);
        updated = replaceFieldAware(updated, leaf(oldField), leaf(newField));
        return updated;
    }

    private String replaceFieldAware(String source, String oldValue, String newValue) {
        if (source.equals(oldValue)) {
            return newValue;
        }
        if (source.contains("." + oldValue)) {
            return source.replace("." + oldValue, "." + newValue);
        }
        if (source.contains("\"" + oldValue + "\"")) {
            return source.replace("\"" + oldValue + "\"", "\"" + newValue + "\"");
        }
        if (source.contains(oldValue) && isFieldLike(source)) {
            return source.replace(oldValue, newValue);
        }
        return source;
    }

    private boolean isFieldLike(String source) {
        for (int i = 0; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (!(Character.isLetterOrDigit(ch) || ch == '.' || ch == '_' || ch == '[' || ch == ']')) {
                return false;
            }
        }
        return true;
    }

    private String leaf(String path) {
        int dot = path.lastIndexOf('.');
        int bracket = path.lastIndexOf('[');
        int index = Math.max(dot, bracket);
        return index < 0 ? path : path.substring(index + 1).replace("]", "");
    }

    public record AstRefactoringReport(Set<Path> changedFiles) {
        public boolean changed() {
            return !changedFiles.isEmpty();
        }
    }
}
