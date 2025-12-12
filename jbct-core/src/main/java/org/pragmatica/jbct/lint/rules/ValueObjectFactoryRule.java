package org.pragmatica.jbct.lint.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.pragmatica.jbct.lint.Diagnostic;
import org.pragmatica.jbct.lint.LintContext;

import java.util.stream.Stream;

/**
 * JBCT-VO-01: Value objects need factory returning Result<T>.
 *
 * In JBCT, value objects should:
 * - Have private/package-private constructors
 * - Provide a static factory method returning Result<T>
 * - The factory validates input and returns failure on invalid data
 *
 * This rule detects value objects (records or final classes with only final fields)
 * that have public constructors without a corresponding Result-returning factory.
 */
public class ValueObjectFactoryRule implements LintRule {

    private static final String RULE_ID = "JBCT-VO-01";
    private static final String DOC_LINK = "https://github.com/siy/coding-technology/blob/main/series/part-3-value-objects.md";

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public String description() {
        return "Value objects should have factory methods returning Result<T>";
    }

    @Override
    public Stream<Diagnostic> analyze(CompilationUnit cu, LintContext ctx) {
        var packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

        if (!ctx.isBusinessPackage(packageName)) {
            return Stream.empty();
        }

        var recordDiagnostics = cu.findAll(RecordDeclaration.class).stream()
                .filter(this::needsFactoryMethod)
                .map(record -> createRecordDiagnostic(record, ctx));

        var classDiagnostics = cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                .filter(this::isValueObjectClass)
                .filter(this::needsFactoryMethod)
                .map(clazz -> createClassDiagnostic(clazz, ctx));

        return Stream.concat(recordDiagnostics, classDiagnostics);
    }

    private boolean isValueObjectClass(ClassOrInterfaceDeclaration clazz) {
        if (clazz.isInterface() || clazz.isAbstract()) {
            return false;
        }

        // Value object: final class with only final fields
        if (!clazz.isFinal()) {
            return false;
        }

        // All fields should be final
        return clazz.getFields().stream()
                .allMatch(field -> field.isFinal());
    }

    private boolean needsFactoryMethod(RecordDeclaration record) {
        // Check if record has a Result-returning factory
        return !hasResultFactory(record.getMethods(), record.getNameAsString());
    }

    private boolean needsFactoryMethod(ClassOrInterfaceDeclaration clazz) {
        // Check if has public constructor
        var hasPublicConstructor = clazz.getConstructors().stream()
                .anyMatch(c -> c.isPublic());

        if (!hasPublicConstructor) {
            return false; // Already has restricted constructor
        }

        // Check if has Result-returning factory
        return !hasResultFactory(clazz.getMethods(), clazz.getNameAsString());
    }

    private boolean hasResultFactory(java.util.List<MethodDeclaration> methods, String typeName) {
        return methods.stream()
                .filter(m -> m.isStatic())
                .filter(m -> !m.isPrivate())
                .anyMatch(m -> returnsResultOf(m, typeName));
    }

    private boolean returnsResultOf(MethodDeclaration method, String typeName) {
        var returnType = method.getType();
        if (!(returnType instanceof ClassOrInterfaceType classType)) {
            return false;
        }

        if (!classType.getNameAsString().equals("Result")) {
            return false;
        }

        // Check type argument matches the value object type
        var typeArgs = classType.getTypeArguments();
        if (typeArgs.isEmpty()) {
            return true; // Raw Result, assume it's correct
        }

        return typeArgs.get().stream()
                .anyMatch(arg -> arg.asString().contains(typeName));
    }

    private Diagnostic createRecordDiagnostic(RecordDeclaration record, LintContext ctx) {
        var line = record.getBegin().map(p -> p.line).orElse(1);
        var column = record.getBegin().map(p -> p.column).orElse(1);
        var name = record.getNameAsString();

        return Diagnostic.diagnostic(
                RULE_ID,
                ctx.severityFor(RULE_ID),
                ctx.fileName(),
                line,
                column,
                "Record '" + name + "' should have a factory method returning Result<" + name + ">",
                "JBCT value objects use factory methods for validation. " +
                        "Add a static factory method that validates input and returns Result<" + name + ">."
        ).withExample("""
                // Add factory method to record
                public record %s(...) {
                    public static Result<%s> %s(...) {
                        // Validate input
                        if (invalid) {
                            return Result.failure(Causes.cause("Validation failed"));
                        }
                        return Result.success(new %s(...));
                    }
                }
                """.formatted(name, name, camelCase(name), name))
                .withDocLink(DOC_LINK);
    }

    private Diagnostic createClassDiagnostic(ClassOrInterfaceDeclaration clazz, LintContext ctx) {
        var line = clazz.getBegin().map(p -> p.line).orElse(1);
        var column = clazz.getBegin().map(p -> p.column).orElse(1);
        var name = clazz.getNameAsString();

        return Diagnostic.diagnostic(
                RULE_ID,
                ctx.severityFor(RULE_ID),
                ctx.fileName(),
                line,
                column,
                "Value object '" + name + "' has public constructor but no Result-returning factory",
                "JBCT value objects should have private constructors and factory methods returning Result<T>. " +
                        "Make constructor private and add a static factory method."
        ).withExample("""
                public final class %s {
                    private %s(...) { ... }  // Private constructor

                    public static Result<%s> %s(...) {
                        // Validate input
                        return Result.success(new %s(...));
                    }
                }
                """.formatted(name, name, name, camelCase(name), name))
                .withDocLink(DOC_LINK);
    }

    private String camelCase(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
