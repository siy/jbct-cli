package org.pragmatica.jbct.lint.cst.rules;

import org.pragmatica.jbct.lint.Diagnostic;
import org.pragmatica.jbct.lint.LintContext;
import org.pragmatica.jbct.lint.cst.CstLintRule;
import org.pragmatica.jbct.parser.Java25Parser.CstNode;

import java.util.stream.Stream;

import static org.pragmatica.jbct.parser.CstNodes.*;

/**
 * JBCT-LAM-03: No ternary in lambdas.
 */
public class CstLambdaTernaryRule implements CstLintRule {

    private static final String RULE_ID = "JBCT-LAM-03";

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public String description() {
        return "No ternary operators in lambdas - use filter() or extract";
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

        return findAll(root, "Lambda").stream()
            .filter(lambda -> hasTernary(lambda, source))
            .map(lambda -> createDiagnostic(lambda, ctx));
    }

    private boolean hasTernary(CstNode lambda, String source) {
        return contains(lambda, "Ternary") || text(lambda, source).contains("?");
    }

    private Diagnostic createDiagnostic(CstNode lambda, LintContext ctx) {
        return Diagnostic.diagnostic(
            RULE_ID,
            ctx.severityFor(RULE_ID),
            ctx.fileName(),
            startLine(lambda),
            startColumn(lambda),
            "Lambda contains ternary operator - use filter() or extract",
            "Ternary in lambdas reduces readability. Use filter() or extract to method."
        );
    }
}
