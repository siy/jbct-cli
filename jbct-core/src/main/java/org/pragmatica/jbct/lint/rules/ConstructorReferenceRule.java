package org.pragmatica.jbct.lint.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import org.pragmatica.jbct.lint.Diagnostic;
import org.pragmatica.jbct.lint.LintContext;

import java.util.Set;
import java.util.stream.Stream;

/**
 * JBCT-STY-02: Prefer constructor references when possible.
 *
 * Use .map(X::new) instead of .map(v -> new X(v))
 */
public class ConstructorReferenceRule implements LintRule {

    private static final String RULE_ID = "JBCT-STY-02";
    private static final String DOC_LINK = "https://github.com/siy/coding-technology/blob/main/skills/jbct/SKILL.md";

    private static final Set<String> MONADIC_METHODS = Set.of(
            "map", "flatMap", "filter", "recover", "onSuccess", "onFailure"
    );

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public String description() {
        return "Prefer constructor references: .map(X::new) instead of .map(v -> new X(v))";
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
                .filter(this::canBeConstructorReference)
                .map(lambda -> createDiagnostic(lambda, ctx));
    }

    private boolean isInMonadicOperation(LambdaExpr lambda) {
        return lambda.findAncestor(MethodCallExpr.class)
                .filter(call -> MONADIC_METHODS.contains(call.getNameAsString()))
                .isPresent();
    }

    private boolean canBeConstructorReference(LambdaExpr lambda) {
        var params = lambda.getParameters();
        if (params.isEmpty()) {
            return false;
        }

        var body = lambda.getBody();

        // Check if body is a single expression that's an object creation
        if (body.isExpressionStmt()) {
            return isSimpleConstructorCall(body.asExpressionStmt(), params.size());
        }

        // Check if body is just an expression (no braces)
        if (!body.isBlockStmt()) {
            var expr = lambda.getExpressionBody();
            if (expr.isPresent() && expr.get().isObjectCreationExpr()) {
                return isMatchingConstructorCall(expr.get().asObjectCreationExpr(), lambda);
            }
        }

        return false;
    }

    private boolean isSimpleConstructorCall(ExpressionStmt stmt, int paramCount) {
        var expr = stmt.getExpression();
        if (!expr.isObjectCreationExpr()) {
            return false;
        }

        var creation = expr.asObjectCreationExpr();
        return creation.getArguments().size() == paramCount;
    }

    private boolean isMatchingConstructorCall(ObjectCreationExpr creation, LambdaExpr lambda) {
        var params = lambda.getParameters();
        var args = creation.getArguments();

        if (params.size() != args.size()) {
            return false;
        }

        // Check if all arguments are just the parameter names in order
        for (int i = 0; i < params.size(); i++) {
            var paramName = params.get(i).getNameAsString();
            var arg = args.get(i);

            if (!arg.isNameExpr() || !arg.asNameExpr().getNameAsString().equals(paramName)) {
                return false;
            }
        }

        return true;
    }

    private Diagnostic createDiagnostic(LambdaExpr lambda, LintContext ctx) {
        var line = lambda.getBegin().map(p -> p.line).orElse(1);
        var column = lambda.getBegin().map(p -> p.column).orElse(1);

        var typeName = lambda.getExpressionBody()
                .filter(e -> e.isObjectCreationExpr())
                .map(e -> e.asObjectCreationExpr().getType().getNameAsString())
                .orElse("Type");

        return Diagnostic.diagnostic(
                RULE_ID,
                ctx.severityFor(RULE_ID),
                ctx.fileName(),
                line,
                column,
                "Use constructor reference: " + typeName + "::new",
                "Constructor references are more concise and readable."
        ).withExample("""
                // Before: lambda with constructor
                .map(value -> new Email(value))
                .map((a, b) -> new Pair(a, b))

                // After: constructor reference
                .map(Email::new)
                .map(Pair::new)
                """)
                .withDocLink(DOC_LINK);
    }
}
