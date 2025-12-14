package org.pragmatica.jbct.lint.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.pragmatica.jbct.lint.Diagnostic;
import org.pragmatica.jbct.lint.LintContext;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * JBCT-SEQ-01: Limit chain length in sequencer patterns.
 *
 * Chains of .map()/.flatMap() should be 2-5 steps. Longer chains should be split.
 */
public class ChainLengthRule implements LintRule {

    private static final String RULE_ID = "JBCT-SEQ-01";
    private static final String DOC_LINK = "https://github.com/siy/coding-technology/blob/main/skills/jbct/SKILL.md";

    private static final int MAX_CHAIN_LENGTH = 5;

    private static final Set<String> CHAIN_METHODS = Set.of(
            "map", "flatMap", "filter", "recover", "onSuccess", "onFailure"
    );

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public String description() {
        return "Chain length should be 2-5 steps; split longer chains";
    }

    @Override
    public Stream<Diagnostic> analyze(CompilationUnit cu, LintContext ctx) {
        var packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

        if (!ctx.isBusinessPackage(packageName)) {
            return Stream.empty();
        }

        // Track which method calls we've already counted as part of a chain
        var processedCalls = new HashSet<MethodCallExpr>();

        return cu.findAll(MethodDeclaration.class).stream()
                .flatMap(method -> findLongChains(method, processedCalls, ctx));
    }

    private Stream<Diagnostic> findLongChains(MethodDeclaration method, Set<MethodCallExpr> processed, LintContext ctx) {
        if (method.getBody().isEmpty()) {
            return Stream.empty();
        }

        return method.getBody().get().findAll(MethodCallExpr.class).stream()
                .filter(call -> !processed.contains(call))
                .filter(this::isChainMethod)
                .filter(this::isChainStart)
                .map(chainStart -> countChainLength(chainStart, processed))
                .filter(chain -> chain.length > MAX_CHAIN_LENGTH)
                .map(chain -> createDiagnostic(chain, method, ctx));
    }

    private boolean isChainMethod(MethodCallExpr call) {
        return CHAIN_METHODS.contains(call.getNameAsString());
    }

    private boolean isChainStart(MethodCallExpr call) {
        // A chain start is a chain method whose scope is NOT another chain method
        var scope = call.getScope();
        if (scope.isEmpty()) {
            return true;
        }

        if (scope.get().isMethodCallExpr()) {
            var scopeCall = scope.get().asMethodCallExpr();
            return !isChainMethod(scopeCall);
        }

        return true;
    }

    private ChainInfo countChainLength(MethodCallExpr start, Set<MethodCallExpr> processed) {
        var length = 0;
        var current = start;
        MethodCallExpr firstInChain = null;

        // Walk up the chain (parent method calls)
        while (current != null) {
            var parent = current.findAncestor(MethodCallExpr.class);

            if (parent.isPresent() && isChainMethod(parent.get())) {
                // Check if this call is the scope of the parent
                var parentScope = parent.get().getScope();
                if (parentScope.isPresent() && parentScope.get() == current) {
                    current = parent.get();
                    continue;
                }
            }
            break;
        }

        // Now walk down from the top of the chain
        firstInChain = current;
        while (current != null && isChainMethod(current)) {
            length++;
            processed.add(current);

            // Find next in chain (where current is the scope)
            current = findNextInChain(current);
        }

        return new ChainInfo(firstInChain, length);
    }

    private MethodCallExpr findNextInChain(MethodCallExpr current) {
        // Find a method call that has current as its scope
        var parent = current.getParentNode();
        if (parent.isEmpty()) {
            return null;
        }

        if (parent.get() instanceof MethodCallExpr parentCall) {
            if (parentCall.getScope().isPresent() && parentCall.getScope().get() == current) {
                if (isChainMethod(parentCall)) {
                    return parentCall;
                }
            }
        }

        return null;
    }

    private Diagnostic createDiagnostic(ChainInfo chain, MethodDeclaration method, LintContext ctx) {
        var line = chain.start.getBegin().map(p -> p.line).orElse(1);
        var column = chain.start.getBegin().map(p -> p.column).orElse(1);

        return Diagnostic.diagnostic(
                RULE_ID,
                ctx.severityFor(RULE_ID),
                ctx.fileName(),
                line,
                column,
                "Chain has " + chain.length + " steps (max " + MAX_CHAIN_LENGTH + "); split into composed methods",
                "Long chains are hard to read and debug. " +
                        "Extract intermediate steps into named methods."
        ).withExample("""
                // Before: long chain
                return validate(request)
                    .flatMap(this::step1)
                    .flatMap(this::step2)
                    .flatMap(this::step3)
                    .flatMap(this::step4)
                    .flatMap(this::step5)
                    .flatMap(this::step6);

                // After: split into composed methods
                return validate(request)
                    .flatMap(this::firstPhase)
                    .flatMap(this::secondPhase);

                private Result<T> firstPhase(ValidRequest req) {
                    return step1(req)
                        .flatMap(this::step2)
                        .flatMap(this::step3);
                }
                """)
                .withDocLink(DOC_LINK);
    }

    private record ChainInfo(MethodCallExpr start, int length) {}
}
