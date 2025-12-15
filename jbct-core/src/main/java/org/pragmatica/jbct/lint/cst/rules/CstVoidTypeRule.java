package org.pragmatica.jbct.lint.cst.rules;

import org.pragmatica.jbct.lint.Diagnostic;
import org.pragmatica.jbct.lint.LintContext;
import org.pragmatica.jbct.lint.cst.CstLintRule;
import org.pragmatica.jbct.parser.Java25Parser.CstNode;

import java.util.stream.Stream;

import static org.pragmatica.jbct.parser.CstNodes.*;

/**
 * JBCT-RET-04: Use Unit instead of Void.
 */
public class CstVoidTypeRule implements CstLintRule {

    private static final String RULE_ID = "JBCT-RET-04";

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public String description() {
        return "Use Unit instead of Void for side-effect methods";
    }

    @Override
    public Stream<Diagnostic> analyze(CstNode root, String source, LintContext ctx) {
        var packageName = findFirst(root, "PackageDecl")
            .flatMap(pd -> findFirst(pd, "QualifiedName"))
            .map(qn -> text(qn, source))
            .or("");

        if (!ctx.isBusinessPackage(packageName)) {
            return Stream.empty();
        }

        // Find methods returning Void (boxed)
        return findAll(root, "MethodDecl").stream()
            .filter(method -> returnsBoxedVoid(method, source))
            .map(method -> createDiagnostic(method, source, ctx));
    }

    private boolean returnsBoxedVoid(CstNode method, String source) {
        var returnType = childByRule(method, "Type");
        if (returnType.isEmpty()) return false;
        var typeText = text(returnType.unwrap(), source).trim();
        return typeText.equals("Void") || typeText.contains("<Void>");
    }

    private Diagnostic createDiagnostic(CstNode method, String source, LintContext ctx) {
        var methodName = childByRule(method, "Identifier")
            .map(id -> text(id, source))
            .or("(unknown)");

        return Diagnostic.diagnostic(
            RULE_ID,
            ctx.severityFor(RULE_ID),
            ctx.fileName(),
            startLine(method),
            startColumn(method),
            "Method '" + methodName + "' uses Void; use Unit instead",
            "JBCT uses Unit instead of Void for side-effect returns."
        );
    }
}
