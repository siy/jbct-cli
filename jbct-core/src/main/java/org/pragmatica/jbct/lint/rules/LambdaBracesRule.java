package org.pragmatica.jbct.lint.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import org.pragmatica.jbct.lint.Diagnostic;
import org.pragmatica.jbct.lint.LintContext;

import java.util.Set;
import java.util.stream.Stream;

/**
 * JBCT-LAM-02: No braces in lambdas passed to monadic operations.
 *
 * Lambdas with block bodies should be extracted to named methods.
 */
public class LambdaBracesRule implements LintRule {

    private static final String RULE_ID = "JBCT-LAM-02";
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
        return "No braces in lambdas - extract to named methods";
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
                .filter(this::hasBlockBody)
                .filter(this::isNotSimpleSingleReturn)
                .map(lambda -> createDiagnostic(lambda, ctx));
    }

    private boolean isInMonadicOperation(LambdaExpr lambda) {
        return lambda.findAncestor(MethodCallExpr.class)
                .filter(call -> MONADIC_METHODS.contains(call.getNameAsString()))
                .isPresent();
    }

    private boolean hasBlockBody(LambdaExpr lambda) {
        return lambda.getBody().isBlockStmt();
    }

    private boolean isNotSimpleSingleReturn(LambdaExpr lambda) {
        if (!lambda.getBody().isBlockStmt()) {
            return false;
        }

        var block = lambda.getBody().asBlockStmt();
        var statements = block.getStatements();

        // Allow single return statement with simple expression
        if (statements.size() == 1 && statements.get(0).isReturnStmt()) {
            var returnStmt = statements.get(0).asReturnStmt();
            // If return expression is simple (name, method call, field access), allow it
            if (returnStmt.getExpression().isPresent()) {
                var expr = returnStmt.getExpression().get();
                return !expr.isNameExpr() && !expr.isMethodCallExpr() && !expr.isFieldAccessExpr();
            }
        }

        // Multiple statements = definitely needs extraction
        return statements.size() > 1;
    }

    private Diagnostic createDiagnostic(LambdaExpr lambda, LintContext ctx) {
        var line = lambda.getBegin().map(p -> p.line).orElse(1);
        var column = lambda.getBegin().map(p -> p.column).orElse(1);

        var statementCount = lambda.getBody().isBlockStmt()
                ? lambda.getBody().asBlockStmt().getStatements().size()
                : 1;

        return Diagnostic.diagnostic(
                RULE_ID,
                ctx.severityFor(RULE_ID),
                ctx.fileName(),
                line,
                column,
                "Lambda has block body with " + statementCount + " statements; extract to named method",
                "Lambdas in monadic operations should be expression-only. " +
                        "Extract multi-statement logic to named methods."
        ).withExample("""
                // Before: lambda with braces
                .map(data -> {
                    cache.put(key, data);
                    log.info("Cached: {}", data);
                    return data.size();
                })

                // After: named method
                .map(this::cacheAndCount)

                private int cacheAndCount(Data data) {
                    cache.put(key, data);
                    log.info("Cached: {}", data);
                    return data.size();
                }
                """)
                .withDocLink(DOC_LINK);
    }
}
