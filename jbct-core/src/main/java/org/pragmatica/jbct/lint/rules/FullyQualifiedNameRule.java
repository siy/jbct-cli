package org.pragmatica.jbct.lint.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import org.pragmatica.jbct.lint.Diagnostic;
import org.pragmatica.jbct.lint.LintContext;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * JBCT-STY-03: No fully qualified class names in method body.
 *
 * Use imports instead of org.foo.Bar in code.
 */
public class FullyQualifiedNameRule implements LintRule {

    private static final String RULE_ID = "JBCT-STY-03";
    private static final String DOC_LINK = "https://github.com/siy/coding-technology/blob/main/skills/jbct/SKILL.md";

    // Pattern to detect likely package prefixes (at least 2 dot-separated parts before the class)
    private static final Pattern FQCN_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+\\.[A-Z]");

    // Common packages that might appear as FQCN
    private static final Set<String> COMMON_PACKAGES = Set.of(
            "java", "javax", "org", "com", "net", "io"
    );

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public String description() {
        return "Use imports instead of fully qualified class names";
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
                .flatMap(method -> checkMethodBody(method, ctx));
    }

    private Stream<Diagnostic> checkMethodBody(MethodDeclaration method, LintContext ctx) {
        if (method.getBody().isEmpty()) {
            return Stream.empty();
        }

        var body = method.getBody().get();

        // Find field access chains that look like FQCNs
        return body.findAll(FieldAccessExpr.class).stream()
                .filter(this::looksLikeFqcn)
                .map(expr -> createDiagnostic(expr, ctx));
    }

    private boolean looksLikeFqcn(FieldAccessExpr expr) {
        // Build the full expression string
        var fullName = buildFullName(expr);

        // Check if it matches FQCN pattern
        if (!FQCN_PATTERN.matcher(fullName).find()) {
            return false;
        }

        // Check if root starts with common package prefix
        var root = getRootName(expr);
        return COMMON_PACKAGES.contains(root);
    }

    private String buildFullName(FieldAccessExpr expr) {
        var sb = new StringBuilder();
        buildFullNameRecursive(expr, sb);
        return sb.toString();
    }

    private void buildFullNameRecursive(FieldAccessExpr expr, StringBuilder sb) {
        var scope = expr.getScope();

        if (scope.isFieldAccessExpr()) {
            buildFullNameRecursive(scope.asFieldAccessExpr(), sb);
            sb.append(".");
        } else if (scope.isNameExpr()) {
            sb.append(scope.asNameExpr().getNameAsString()).append(".");
        }

        sb.append(expr.getNameAsString());
    }

    private String getRootName(FieldAccessExpr expr) {
        var scope = expr.getScope();

        while (scope.isFieldAccessExpr()) {
            scope = scope.asFieldAccessExpr().getScope();
        }

        if (scope.isNameExpr()) {
            return scope.asNameExpr().getNameAsString();
        }

        return "";
    }

    private Diagnostic createDiagnostic(FieldAccessExpr expr, LintContext ctx) {
        var line = expr.getBegin().map(p -> p.line).orElse(1);
        var column = expr.getBegin().map(p -> p.column).orElse(1);

        var fullName = buildFullName(expr);

        return Diagnostic.diagnostic(
                RULE_ID,
                ctx.severityFor(RULE_ID),
                ctx.fileName(),
                line,
                column,
                "Use import instead of fully qualified name: " + fullName,
                "Fully qualified class names reduce readability. Add an import statement."
        ).withExample("""
                // Before: FQCN in code
                org.pragmatica.lang.Result.success(value)

                // After: use import
                import org.pragmatica.lang.Result;
                ...
                Result.success(value)
                """)
                .withDocLink(DOC_LINK);
    }
}
