package org.pragmatica.jbct.lint.cst.rules;

import org.pragmatica.jbct.lint.Diagnostic;
import org.pragmatica.jbct.lint.LintContext;
import org.pragmatica.jbct.lint.cst.CstLintRule;
import org.pragmatica.jbct.parser.Java25Parser.CstNode;

import java.util.stream.Stream;

import static org.pragmatica.jbct.parser.CstNodes.*;

/**
 * JBCT-LOG-02: No logger as method parameter.
 */
public class CstLoggerParameterRule implements CstLintRule {

    private static final String RULE_ID = "JBCT-LOG-02";

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public String description() {
        return "No Logger as method parameter - component owns its logger";
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

        return findAll(root, "MethodDecl").stream()
            .filter(method -> hasLoggerParameter(method, source))
            .map(method -> createDiagnostic(method, source, ctx));
    }

    private boolean hasLoggerParameter(CstNode method, String source) {
        var methodText = text(method, source);
        return methodText.contains("Logger ") && methodText.contains("(") &&
               methodText.indexOf("Logger ") < methodText.indexOf(")");
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
            "Method '" + methodName + "' has Logger parameter - use class-level logger",
            "Each component should own its logger as a final field."
        );
    }
}
