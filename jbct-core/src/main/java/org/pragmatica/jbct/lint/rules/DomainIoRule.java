package org.pragmatica.jbct.lint.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import org.pragmatica.jbct.lint.Diagnostic;
import org.pragmatica.jbct.lint.LintContext;

import java.util.Set;
import java.util.stream.Stream;

/**
 * JBCT-MIX-01: No I/O operations in domain packages.
 *
 * Domain classes should be pure; I/O should be in adapter packages.
 */
public class DomainIoRule implements LintRule {

    private static final String RULE_ID = "JBCT-MIX-01";
    private static final String DOC_LINK = "https://github.com/siy/coding-technology/blob/main/skills/jbct/SKILL.md";

    // I/O-related imports that indicate domain pollution
    private static final Set<String> IO_IMPORTS = Set.of(
            "java.io.",
            "java.nio.",
            "java.net.",
            "java.sql.",
            "javax.sql.",
            "org.springframework.jdbc.",
            "org.springframework.data.",
            "org.jooq.",
            "jakarta.persistence.",
            "javax.persistence.",
            "org.hibernate.",
            "java.net.http.",
            "okhttp3.",
            "org.apache.http."
    );

    // I/O-related class names
    private static final Set<String> IO_CLASSES = Set.of(
            "File", "Path", "Files", "FileReader", "FileWriter",
            "InputStream", "OutputStream", "Reader", "Writer",
            "BufferedReader", "BufferedWriter",
            "Socket", "ServerSocket", "URL", "URI",
            "Connection", "Statement", "PreparedStatement", "ResultSet",
            "HttpClient", "HttpRequest", "HttpResponse",
            "DSLContext", "EntityManager", "Session"
    );

    // I/O-related method names
    private static final Set<String> IO_METHODS = Set.of(
            "readString", "writeString", "readAllBytes", "readAllLines",
            "newInputStream", "newOutputStream",
            "openConnection", "connect",
            "executeQuery", "executeUpdate",
            "send", "fetch", "persist", "merge", "find"
    );

    // Domain package patterns
    private static final Set<String> DOMAIN_PACKAGES = Set.of(
            ".domain.", ".usecase.", ".model.", ".entity.", ".vo."
    );

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public String description() {
        return "No I/O operations in domain packages - use adapter pattern";
    }

    @Override
    public Stream<Diagnostic> analyze(CompilationUnit cu, LintContext ctx) {
        var packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

        // Only check domain packages
        if (!isDomainPackage(packageName)) {
            return Stream.empty();
        }

        var diagnostics = Stream.<Diagnostic>builder();

        // Check imports
        cu.getImports().stream()
                .filter(this::isIoImport)
                .map(imp -> createImportDiagnostic(imp, ctx))
                .forEach(diagnostics::add);

        // Check for I/O class instantiation
        cu.findAll(ObjectCreationExpr.class).stream()
                .filter(this::isIoClassCreation)
                .map(expr -> createCreationDiagnostic(expr, ctx))
                .forEach(diagnostics::add);

        // Check for I/O method calls
        cu.findAll(MethodCallExpr.class).stream()
                .filter(this::isIoMethodCall)
                .map(call -> createMethodDiagnostic(call, ctx))
                .forEach(diagnostics::add);

        return diagnostics.build();
    }

    private boolean isDomainPackage(String packageName) {
        for (var pattern : DOMAIN_PACKAGES) {
            if (packageName.contains(pattern) || packageName.endsWith(pattern.substring(0, pattern.length() - 1))) {
                return true;
            }
        }
        return false;
    }

    private boolean isIoImport(ImportDeclaration imp) {
        var importName = imp.getNameAsString();
        return IO_IMPORTS.stream().anyMatch(importName::startsWith);
    }

    private boolean isIoClassCreation(ObjectCreationExpr expr) {
        var typeName = expr.getType().getNameAsString();
        return IO_CLASSES.contains(typeName);
    }

    private boolean isIoMethodCall(MethodCallExpr call) {
        var methodName = call.getNameAsString();
        if (!IO_METHODS.contains(methodName)) {
            return false;
        }

        // Check if called on an I/O class
        return call.getScope()
                .filter(scope -> scope.isNameExpr())
                .map(scope -> scope.asNameExpr().getNameAsString())
                .filter(name -> IO_CLASSES.contains(name) || name.equals("Files"))
                .isPresent();
    }

    private Diagnostic createImportDiagnostic(ImportDeclaration imp, LintContext ctx) {
        var line = imp.getBegin().map(p -> p.line).orElse(1);
        var column = imp.getBegin().map(p -> p.column).orElse(1);

        return Diagnostic.diagnostic(
                RULE_ID,
                ctx.severityFor(RULE_ID),
                ctx.fileName(),
                line,
                column,
                "I/O import '" + imp.getNameAsString() + "' in domain package",
                "Domain classes should be pure. Move I/O operations to adapter layer."
        ).withExample(getExample())
                .withDocLink(DOC_LINK);
    }

    private Diagnostic createCreationDiagnostic(ObjectCreationExpr expr, LintContext ctx) {
        var line = expr.getBegin().map(p -> p.line).orElse(1);
        var column = expr.getBegin().map(p -> p.column).orElse(1);

        var typeName = expr.getType().getNameAsString();

        return Diagnostic.diagnostic(
                RULE_ID,
                ctx.severityFor(RULE_ID),
                ctx.fileName(),
                line,
                column,
                "I/O class '" + typeName + "' instantiated in domain package",
                "Domain classes should be pure. Move I/O operations to adapter layer."
        ).withExample(getExample())
                .withDocLink(DOC_LINK);
    }

    private Diagnostic createMethodDiagnostic(MethodCallExpr call, LintContext ctx) {
        var line = call.getBegin().map(p -> p.line).orElse(1);
        var column = call.getBegin().map(p -> p.column).orElse(1);

        return Diagnostic.diagnostic(
                RULE_ID,
                ctx.severityFor(RULE_ID),
                ctx.fileName(),
                line,
                column,
                "I/O method '" + call.getNameAsString() + "' called in domain package",
                "Domain classes should be pure. Move I/O operations to adapter layer."
        ).withExample(getExample())
                .withDocLink(DOC_LINK);
    }

    private String getExample() {
        return """
                // Before: I/O in domain
                public record ExtensionConfig(...) {
                    public static Result<ExtensionConfig> load(Path file) {
                        return Files.readString(file)  // I/O in domain!
                            .flatMap(this::parse);
                    }
                }

                // After: separate concerns
                // Domain (pure)
                public record ExtensionConfig(...) { }

                // Adapter (I/O)
                public interface ConfigLoader {
                    static Result<ExtensionConfig> load(Path file) { ... }
                }
                """;
    }
}
