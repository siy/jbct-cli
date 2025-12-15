package org.pragmatica.jbct.lint.cst.rules;

import org.pragmatica.jbct.lint.Diagnostic;
import org.pragmatica.jbct.lint.LintContext;
import org.pragmatica.jbct.lint.cst.CstLintRule;
import org.pragmatica.jbct.parser.Java25Parser.CstNode;

import java.util.stream.Stream;

import static org.pragmatica.jbct.parser.CstNodes.*;

/**
 * JBCT-NAM-02: Use Valid prefix, not Validated.
 */
public class CstValidatedNamingRule implements CstLintRule {

    private static final String RULE_ID = "JBCT-NAM-02";

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public String description() {
        return "Use 'Valid' prefix, not 'Validated'";
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

        // Check classes and records for Validated prefix
        var classDiagnostics = findAll(root, "ClassDecl").stream()
            .filter(cls -> hasValidatedName(cls, source))
            .map(cls -> createDiagnostic(cls, source, ctx));

        var recordDiagnostics = findAll(root, "RecordDecl").stream()
            .filter(rec -> hasValidatedName(rec, source))
            .map(rec -> createDiagnostic(rec, source, ctx));

        return Stream.concat(classDiagnostics, recordDiagnostics);
    }

    private boolean hasValidatedName(CstNode node, String source) {
        return childByRule(node, "Identifier")
            .map(id -> text(id, source))
            .filter(name -> name.startsWith("Validated"))
            .isPresent();
    }

    private Diagnostic createDiagnostic(CstNode node, String source, LintContext ctx) {
        var name = childByRule(node, "Identifier")
            .map(id -> text(id, source))
            .or("Validated...");
        var suggestedName = name.replaceFirst("Validated", "Valid");

        return Diagnostic.diagnostic(
            RULE_ID,
            ctx.severityFor(RULE_ID),
            ctx.fileName(),
            startLine(node),
            startColumn(node),
            "Type '" + name + "' should be named '" + suggestedName + "'",
            "JBCT uses 'Valid' prefix, not 'Validated'."
        );
    }
}
