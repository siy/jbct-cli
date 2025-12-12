package org.pragmatica.jbct.lint.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.pragmatica.jbct.lint.Diagnostic;
import org.pragmatica.jbct.lint.LintContext;

import java.util.stream.Stream;

/**
 * JBCT-NAM-01: Factory method naming convention.
 *
 * In JBCT, factory methods should follow the pattern:
 * - TypeName.typeName(...) returns Result<TypeName> or TypeName
 *
 * This provides consistency and makes the API predictable.
 */
public class FactoryNamingRule implements LintRule {

    private static final String RULE_ID = "JBCT-NAM-01";
    private static final String DOC_LINK = "https://github.com/siy/coding-technology/blob/main/series/part-3-value-objects.md";

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public String description() {
        return "Factory methods should be named after the type: TypeName.typeName()";
    }

    @Override
    public Stream<Diagnostic> analyze(CompilationUnit cu, LintContext ctx) {
        var packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

        if (!ctx.isBusinessPackage(packageName)) {
            return Stream.empty();
        }

        var classDiagnostics = cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                .filter(c -> !c.isInterface())
                .flatMap(clazz -> checkFactoryMethods(clazz, ctx));

        var recordDiagnostics = cu.findAll(RecordDeclaration.class).stream()
                .flatMap(record -> checkRecordFactoryMethods(record, ctx));

        return Stream.concat(classDiagnostics, recordDiagnostics);
    }

    private Stream<Diagnostic> checkFactoryMethods(ClassOrInterfaceDeclaration clazz, LintContext ctx) {
        var typeName = clazz.getNameAsString();
        var expectedName = camelCase(typeName);

        return clazz.getMethods().stream()
                .filter(m -> m.isStatic())
                .filter(m -> !m.isPrivate())
                .filter(m -> returnsType(m, typeName))
                .filter(m -> !isCorrectlyNamed(m, expectedName))
                .map(m -> createDiagnostic(m, typeName, expectedName, ctx));
    }

    private Stream<Diagnostic> checkRecordFactoryMethods(RecordDeclaration record, LintContext ctx) {
        var typeName = record.getNameAsString();
        var expectedName = camelCase(typeName);

        return record.getMethods().stream()
                .filter(m -> m.isStatic())
                .filter(m -> !m.isPrivate())
                .filter(m -> returnsType(m, typeName))
                .filter(m -> !isCorrectlyNamed(m, expectedName))
                .map(m -> createDiagnostic(m, typeName, expectedName, ctx));
    }

    private boolean returnsType(MethodDeclaration method, String typeName) {
        var returnType = method.getType();

        // Direct return of type
        if (returnType.asString().equals(typeName)) {
            return true;
        }

        // Result<TypeName> or Option<TypeName>
        if (returnType instanceof ClassOrInterfaceType classType) {
            var wrapper = classType.getNameAsString();
            if ("Result".equals(wrapper) || "Option".equals(wrapper)) {
                var typeArgs = classType.getTypeArguments();
                if (typeArgs.isPresent()) {
                    return typeArgs.get().stream()
                            .anyMatch(arg -> arg.asString().equals(typeName));
                }
            }
        }

        return false;
    }

    private boolean isCorrectlyNamed(MethodDeclaration method, String expectedName) {
        var methodName = method.getNameAsString();

        // Exact match
        if (methodName.equals(expectedName)) {
            return true;
        }

        // Allow common prefixes: create, of, from, parse
        if (methodName.equals("create") || methodName.equals("of") ||
            methodName.equals("from") || methodName.equals("parse")) {
            return true;
        }

        // Allow prefixed versions: createTypeName, ofTypeName
        if (methodName.startsWith("create") || methodName.startsWith("of") ||
            methodName.startsWith("from") || methodName.startsWith("parse")) {
            return true;
        }

        return false;
    }

    private Diagnostic createDiagnostic(MethodDeclaration method, String typeName, String expectedName, LintContext ctx) {
        var line = method.getBegin().map(p -> p.line).orElse(1);
        var column = method.getBegin().map(p -> p.column).orElse(1);
        var actualName = method.getNameAsString();

        return Diagnostic.diagnostic(
                RULE_ID,
                ctx.severityFor(RULE_ID),
                ctx.fileName(),
                line,
                column,
                "Factory method '" + actualName + "' should be named '" + expectedName + "'",
                "JBCT naming convention: factory methods should match the type name in camelCase. " +
                        "This makes APIs predictable: " + typeName + "." + expectedName + "(...)."
        ).withExample("""
                // Before
                public static Result<%s> %s(...) { ... }

                // After
                public static Result<%s> %s(...) { ... }
                """.formatted(typeName, actualName, typeName, expectedName))
                .withDocLink(DOC_LINK);
    }

    private String camelCase(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
