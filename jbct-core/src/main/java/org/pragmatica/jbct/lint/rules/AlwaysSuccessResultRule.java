package org.pragmatica.jbct.lint.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.pragmatica.jbct.lint.Diagnostic;
import org.pragmatica.jbct.lint.LintContext;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * JBCT-RET-05: Avoid methods that always return Result.success().
 *
 * If a method can never fail, it should return T directly, not Result<T>.
 */
public class AlwaysSuccessResultRule implements LintRule {

    private static final String RULE_ID = "JBCT-RET-05";
    private static final String DOC_LINK = "https://github.com/siy/coding-technology/blob/main/skills/jbct/SKILL.md";

    private static final Set<String> SUCCESS_METHODS = Set.of("success", "unitResult");
    private static final Set<String> FAILURE_INDICATORS = Set.of("failure", "result", "promise");

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public String description() {
        return "Avoid Result<T> when method always succeeds - return T directly";
    }

    @Override
    public Stream<Diagnostic> analyze(CompilationUnit cu, LintContext ctx) {
        var packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

        if (!ctx.isBusinessPackage(packageName)) {
            return Stream.empty();
        }

        return cu.findAll(MethodDeclaration.class).stream()
                .filter(this::returnsResult)
                .filter(this::alwaysReturnsSuccess)
                .map(method -> createDiagnostic(method, ctx));
    }

    private boolean returnsResult(MethodDeclaration method) {
        var returnType = method.getType();
        if (!returnType.isClassOrInterfaceType()) {
            return false;
        }

        var typeName = returnType.asClassOrInterfaceType().getNameAsString();
        return typeName.equals("Result");
    }

    private boolean alwaysReturnsSuccess(MethodDeclaration method) {
        if (method.getBody().isEmpty()) {
            return false;
        }

        var body = method.getBody().get();

        // Find all return statements
        var returns = body.findAll(ReturnStmt.class);
        if (returns.isEmpty()) {
            return false;
        }

        // Check if any return could be a failure
        for (var returnStmt : returns) {
            if (returnStmt.getExpression().isEmpty()) {
                continue;
            }

            var expr = returnStmt.getExpression().get();

            // If it's a method call, check if it's success() or could be failure
            if (expr.isMethodCallExpr()) {
                var call = expr.asMethodCallExpr();
                if (!isDefinitelySuccess(call)) {
                    return false; // Could be a failure path
                }
            } else {
                // If it's not a direct success call, assume it could fail
                return false;
            }
        }

        // Check for any failure calls in the method (even if not returned directly)
        var allCalls = body.findAll(MethodCallExpr.class);
        for (var call : allCalls) {
            if (isFailureCall(call)) {
                return false;
            }
        }

        return true;
    }

    private boolean isDefinitelySuccess(MethodCallExpr call) {
        var methodName = call.getNameAsString();

        // Direct Result.success() call
        if (SUCCESS_METHODS.contains(methodName)) {
            return call.getScope()
                    .filter(s -> s.isNameExpr())
                    .map(s -> s.asNameExpr().getNameAsString())
                    .filter(name -> name.equals("Result"))
                    .isPresent();
        }

        return false;
    }

    private boolean isFailureCall(MethodCallExpr call) {
        var methodName = call.getNameAsString();

        // Check for failure(), .result(), .promise() on cause
        if (methodName.equals("failure")) {
            return call.getScope()
                    .filter(s -> s.isNameExpr())
                    .map(s -> s.asNameExpr().getNameAsString())
                    .filter(name -> name.equals("Result") || name.equals("Promise"))
                    .isPresent();
        }

        // cause.result() or cause.promise() patterns
        return methodName.equals("result") || methodName.equals("promise");
    }

    private Diagnostic createDiagnostic(MethodDeclaration method, LintContext ctx) {
        var line = method.getBegin().map(p -> p.line).orElse(1);
        var column = method.getBegin().map(p -> p.column).orElse(1);

        var methodName = method.getNameAsString();

        return Diagnostic.diagnostic(
                RULE_ID,
                ctx.severityFor(RULE_ID),
                ctx.fileName(),
                line,
                column,
                "Method '" + methodName + "' always returns Result.success(); return T directly",
                "If a method can never fail, wrapping in Result adds unnecessary complexity."
        ).withExample("""
                // Before: Result that never fails
                public static Result<Config> config(String name) {
                    return Result.success(new Config(name));
                }

                // After: return T directly
                public static Config config(String name) {
                    return new Config(name);
                }
                """)
                .withDocLink(DOC_LINK);
    }
}
