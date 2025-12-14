package org.pragmatica.jbct.lint.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.pragmatica.jbct.lint.Diagnostic;
import org.pragmatica.jbct.lint.LintContext;

import java.util.Set;
import java.util.stream.Stream;

/**
 * JBCT-LAM-03: No ternary expressions in lambdas passed to monadic operations.
 *
 * Ternary expressions in lambdas should use filter() or be extracted to named methods.
 */
public class LambdaTernaryRule implements LintRule {

    private static final String RULE_ID = "JBCT-LAM-03";
    private static final String DOC_LINK = "https://github.com/siy/coding-technology/blob/main/skills/jbct/SKILL.md";

    private static final Set<String> MONADIC_METHODS = Set.of(
            "map", "flatMap", "filter", "recover", "onSuccess", "onFailure", "fold"
    );

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public String description() {
        return "No ternary in lambdas - use filter() or extract to method";
    }

    @Override
    public Stream<Diagnostic> analyze(CompilationUnit cu, LintContext ctx) {
        var packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

        if (!ctx.isBusinessPackage(packageName)) {
            return Stream.empty();
        }

        return cu.findAll(LambdaExpr.class).stream()
                .filter(this::isInMonadicOperation)
                .filter(this::hasTernary)
                .map(lambda -> createDiagnostic(lambda, ctx));
    }

    private boolean isInMonadicOperation(LambdaExpr lambda) {
        return lambda.findAncestor(MethodCallExpr.class)
                .filter(call -> MONADIC_METHODS.contains(call.getNameAsString()))
                .isPresent();
    }

    private boolean hasTernary(LambdaExpr lambda) {
        return !lambda.getBody().findAll(ConditionalExpr.class).isEmpty();
    }

    private Diagnostic createDiagnostic(LambdaExpr lambda, LintContext ctx) {
        var line = lambda.getBegin().map(p -> p.line).orElse(1);
        var column = lambda.getBegin().map(p -> p.column).orElse(1);

        return Diagnostic.diagnostic(
                RULE_ID,
                ctx.severityFor(RULE_ID),
                ctx.fileName(),
                line,
                column,
                "Lambda contains ternary expression; use filter() or extract to named method",
                "Ternary expressions in lambdas reduce readability. " +
                        "Use filter() for simple conditions or extract to a named method."
        ).withExample("""
                // Before: ternary in lambda
                .map(value -> value.isValid() ? process(value) : fallback())

                // After option 1: use filter
                .filter(Value::isValid)
                .map(this::process)
                .orElse(fallback())

                // After option 2: extract to method
                .map(this::processOrFallback)

                private Result<T> processOrFallback(Value value) {
                    return value.isValid()
                        ? process(value)
                        : fallback();
                }
                """)
                .withDocLink(DOC_LINK);
    }
}
