package org.pragmatica.jbct.lint.cst.rules;

import org.pragmatica.jbct.lint.Diagnostic;
import org.pragmatica.jbct.lint.LintContext;
import org.pragmatica.jbct.lint.cst.CstLintRule;
import org.pragmatica.jbct.parser.Java25Parser.CstNode;

import java.util.stream.Stream;

import static org.pragmatica.jbct.parser.CstNodes.*;

/**
 * JBCT-STY-01: Prefer fluent failure style (cause.result()).
 */
public class CstFluentFailureRule implements CstLintRule {

    private static final String RULE_ID = "JBCT-STY-01";

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public String description() {
        return "Prefer cause.result() over Result.failure(cause)";
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

        // Find Result.failure( patterns
        return findAll(root, "Primary").stream()
            .filter(node -> text(node, source).contains("Result.failure("))
            .map(node -> createDiagnostic(node, ctx));
    }

    private Diagnostic createDiagnostic(CstNode node, LintContext ctx) {
        return Diagnostic.diagnostic(
            RULE_ID,
            ctx.severityFor(RULE_ID),
            ctx.fileName(),
            startLine(node),
            startColumn(node),
            "Use cause.result() instead of Result.failure(cause)",
            "Fluent style improves readability: cause.result() reads naturally."
        );
    }
}
