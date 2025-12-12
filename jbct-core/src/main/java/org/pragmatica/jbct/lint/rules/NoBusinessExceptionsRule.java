package org.pragmatica.jbct.lint.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.ThrowStmt;
import org.pragmatica.jbct.lint.Diagnostic;
import org.pragmatica.jbct.lint.LintContext;

import java.util.Set;
import java.util.stream.Stream;

/**
 * JBCT-EX-01: No business exceptions in domain/usecase packages.
 *
 * JBCT uses Result<T> for error handling instead of exceptions.
 * Business code should:
 * - Not throw exceptions (use Result.failure instead)
 * - Not declare checked exceptions (use Result<T>)
 * - Not define custom exception classes in business packages
 */
public class NoBusinessExceptionsRule implements LintRule {

    private static final String RULE_ID = "JBCT-EX-01";
    private static final String DOC_LINK = "https://github.com/siy/coding-technology/blob/main/series/part-2-four-return-types.md";

    private static final Set<String> EXCEPTION_BASE_TYPES = Set.of(
            "Exception", "RuntimeException", "Throwable", "Error"
    );

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public String description() {
        return "No exceptions in business code - use Result<T> instead";
    }

    @Override
    public Stream<Diagnostic> analyze(CompilationUnit cu, LintContext ctx) {
        var packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

        if (!ctx.isBusinessPackage(packageName)) {
            return Stream.empty();
        }

        // Check for exception class definitions
        var exceptionClassDiagnostics = cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                .filter(this::isExceptionClass)
                .map(clazz -> createExceptionClassDiagnostic(clazz, ctx));

        // Check for throw statements
        var throwDiagnostics = cu.findAll(ThrowStmt.class).stream()
                .map(stmt -> createThrowDiagnostic(stmt, ctx));

        // Check for methods with throws clause
        var throwsClauseDiagnostics = cu.findAll(MethodDeclaration.class).stream()
                .filter(m -> !m.getThrownExceptions().isEmpty())
                .map(m -> createThrowsClauseDiagnostic(m, ctx));

        return Stream.of(exceptionClassDiagnostics, throwDiagnostics, throwsClauseDiagnostics)
                .flatMap(s -> s);
    }

    private boolean isExceptionClass(ClassOrInterfaceDeclaration clazz) {
        if (clazz.isInterface()) {
            return false;
        }

        // Check if extends Exception or RuntimeException
        return clazz.getExtendedTypes().stream()
                .anyMatch(type -> {
                    var typeName = type.getNameAsString();
                    return EXCEPTION_BASE_TYPES.contains(typeName) ||
                           typeName.endsWith("Exception") ||
                           typeName.endsWith("Error");
                });
    }

    private Diagnostic createExceptionClassDiagnostic(ClassOrInterfaceDeclaration clazz, LintContext ctx) {
        var line = clazz.getBegin().map(p -> p.line).orElse(1);
        var column = clazz.getBegin().map(p -> p.column).orElse(1);
        var name = clazz.getNameAsString();

        return Diagnostic.diagnostic(
                RULE_ID,
                ctx.severityFor(RULE_ID),
                ctx.fileName(),
                line,
                column,
                "Exception class '" + name + "' defined in business package",
                "JBCT uses Result<T> for error handling instead of exceptions. " +
                        "Define error causes as sealed Cause types instead."
        ).withExample("""
                // Before: Exception class
                public class %s extends RuntimeException { ... }

                // After: Sealed Cause hierarchy
                public sealed interface %sCause extends Cause {
                    record InvalidInput(String message) implements %sCause { ... }
                    record NotFound(String id) implements %sCause { ... }
                }
                """.formatted(name, name.replace("Exception", ""), name.replace("Exception", ""), name.replace("Exception", "")))
                .withDocLink(DOC_LINK);
    }

    private Diagnostic createThrowDiagnostic(ThrowStmt stmt, LintContext ctx) {
        var line = stmt.getBegin().map(p -> p.line).orElse(1);
        var column = stmt.getBegin().map(p -> p.column).orElse(1);

        return Diagnostic.diagnostic(
                RULE_ID,
                ctx.severityFor(RULE_ID),
                ctx.fileName(),
                line,
                column,
                "Throw statement in business code",
                "JBCT uses Result.failure() instead of throwing exceptions. " +
                        "Return a Result with a Cause describing the failure."
        ).withExample("""
                // Before: throw
                if (invalid) {
                    throw new IllegalArgumentException("Invalid input");
                }

                // After: Result.failure
                if (invalid) {
                    return Result.failure(Causes.cause("Invalid input"));
                }
                """)
                .withDocLink(DOC_LINK);
    }

    private Diagnostic createThrowsClauseDiagnostic(MethodDeclaration method, LintContext ctx) {
        var line = method.getBegin().map(p -> p.line).orElse(1);
        var column = method.getBegin().map(p -> p.column).orElse(1);
        var name = method.getNameAsString();
        var exceptions = method.getThrownExceptions().stream()
                .map(e -> e.asString())
                .toList();

        return Diagnostic.diagnostic(
                RULE_ID,
                ctx.severityFor(RULE_ID),
                ctx.fileName(),
                line,
                column,
                "Method '" + name + "' declares throws " + exceptions,
                "JBCT methods should return Result<T> instead of throwing exceptions. " +
                        "Wrap the potentially failing operation and return Result."
        ).withExample("""
                // Before: throws clause
                public String process() throws IOException { ... }

                // After: Result return type
                public Result<String> process() {
                    return Result.lift(() -> /* operation that may throw */);
                }
                """)
                .withDocLink(DOC_LINK);
    }
}
