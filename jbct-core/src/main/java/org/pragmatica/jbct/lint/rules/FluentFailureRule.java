package org.pragmatica.jbct.lint.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.pragmatica.jbct.lint.Diagnostic;
import org.pragmatica.jbct.lint.LintContext;

import java.util.Set;
import java.util.stream.Stream;

/**
 * JBCT-STY-01: Prefer fluent failure style.
 *
 * Use cause.result() instead of Result.failure(cause).
 * Use cause.promise() instead of Promise.failure(cause).
 */
public class FluentFailureRule implements LintRule {

    private static final String RULE_ID = "JBCT-STY-01";
    private static final String DOC_LINK = "https://github.com/siy/coding-technology/blob/main/skills/jbct/SKILL.md";

    private static final Set<String> FAILURE_METHODS = Set.of("failure");
    private static final Set<String> WRAPPER_TYPES = Set.of("Result", "Promise");

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public String description() {
        return "Prefer fluent failure style: cause.result() instead of Result.failure(cause)";
    }

    @Override
    public Stream<Diagnostic> analyze(CompilationUnit cu, LintContext ctx) {
        var packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

        if (!ctx.isBusinessPackage(packageName)) {
            return Stream.empty();
        }

        return cu.findAll(MethodCallExpr.class).stream()
                .filter(this::isStaticFailureCall)
                .map(call -> createDiagnostic(call, ctx));
    }

    private boolean isStaticFailureCall(MethodCallExpr call) {
        if (!FAILURE_METHODS.contains(call.getNameAsString())) {
            return false;
        }

        // Check if it's Result.failure() or Promise.failure()
        return call.getScope()
                .filter(scope -> scope.isNameExpr())
                .map(scope -> scope.asNameExpr().getNameAsString())
                .filter(WRAPPER_TYPES::contains)
                .isPresent();
    }

    private Diagnostic createDiagnostic(MethodCallExpr call, LintContext ctx) {
        var line = call.getBegin().map(p -> p.line).orElse(1);
        var column = call.getBegin().map(p -> p.column).orElse(1);

        var wrapperType = call.getScope()
                .map(s -> s.asNameExpr().getNameAsString())
                .orElse("Result");
        var fluentMethod = wrapperType.equals("Promise") ? "promise()" : "result()";

        return Diagnostic.diagnostic(
                RULE_ID,
                ctx.severityFor(RULE_ID),
                ctx.fileName(),
                line,
                column,
                "Use fluent style: cause." + fluentMethod + " instead of " + wrapperType + ".failure(cause)",
                "Fluent failure style reads left-to-right and is more concise."
        ).withExample("""
                // Before: static factory style
                return Result.failure(INVALID_CREDENTIALS);
                return Promise.failure(ACCOUNT_LOCKED);

                // After: fluent style (preferred)
                return INVALID_CREDENTIALS.result();
                return ACCOUNT_LOCKED.promise();
                """)
                .withDocLink(DOC_LINK);
    }
}
