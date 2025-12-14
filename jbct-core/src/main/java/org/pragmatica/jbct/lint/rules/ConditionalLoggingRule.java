package org.pragmatica.jbct.lint.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import org.pragmatica.jbct.lint.Diagnostic;
import org.pragmatica.jbct.lint.LintContext;

import java.util.Set;
import java.util.stream.Stream;

/**
 * JBCT-LOG-01: No conditional logging.
 *
 * Don't wrap log statements in if conditions - let log level configuration handle filtering.
 */
public class ConditionalLoggingRule implements LintRule {

    private static final String RULE_ID = "JBCT-LOG-01";
    private static final String DOC_LINK = "https://github.com/siy/coding-technology/blob/main/skills/jbct/SKILL.md";

    private static final Set<String> LOG_METHODS = Set.of(
            "trace", "debug", "info", "warn", "error", "log"
    );

    private static final Set<String> LOGGER_NAMES = Set.of(
            "log", "logger", "LOG", "LOGGER"
    );

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public String description() {
        return "No conditional logging - let log level configuration handle filtering";
    }

    @Override
    public Stream<Diagnostic> analyze(CompilationUnit cu, LintContext ctx) {
        var packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

        if (!ctx.isBusinessPackage(packageName)) {
            return Stream.empty();
        }

        return cu.findAll(IfStmt.class).stream()
                .filter(this::containsOnlyLogging)
                .map(ifStmt -> createDiagnostic(ifStmt, ctx));
    }

    private boolean containsOnlyLogging(IfStmt ifStmt) {
        var thenStmt = ifStmt.getThenStmt();

        // Check if the then branch only contains logging
        var logCalls = thenStmt.findAll(MethodCallExpr.class).stream()
                .filter(this::isLogCall)
                .count();

        // If the only method calls in the if body are log calls, flag it
        var totalCalls = thenStmt.findAll(MethodCallExpr.class).size();

        return logCalls > 0 && logCalls == totalCalls;
    }

    private boolean isLogCall(MethodCallExpr call) {
        if (!LOG_METHODS.contains(call.getNameAsString())) {
            return false;
        }

        // Check if called on a logger-like object
        return call.getScope()
                .filter(scope -> scope.isNameExpr())
                .map(scope -> scope.asNameExpr().getNameAsString())
                .filter(name -> LOGGER_NAMES.contains(name) || name.toLowerCase().contains("log"))
                .isPresent();
    }

    private Diagnostic createDiagnostic(IfStmt ifStmt, LintContext ctx) {
        var line = ifStmt.getBegin().map(p -> p.line).orElse(1);
        var column = ifStmt.getBegin().map(p -> p.column).orElse(1);

        return Diagnostic.diagnostic(
                RULE_ID,
                ctx.severityFor(RULE_ID),
                ctx.fileName(),
                line,
                column,
                "Remove conditional around logging; let log level configuration handle filtering",
                "Wrapping log statements in conditions duplicates log level filtering logic."
        ).withExample("""
                // Before: conditional logging
                if (count > 0) {
                    log.debug("Processed {} items", count);
                }

                // After: unconditional logging
                log.debug("Processed {} items", count);
                // Configure log level to filter if needed
                """)
                .withDocLink(DOC_LINK);
    }
}
