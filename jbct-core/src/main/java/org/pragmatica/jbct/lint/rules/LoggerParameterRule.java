package org.pragmatica.jbct.lint.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import org.pragmatica.jbct.lint.Diagnostic;
import org.pragmatica.jbct.lint.LintContext;

import java.util.Set;
import java.util.stream.Stream;

/**
 * JBCT-LOG-02: No logger as method parameter.
 *
 * Logging should be owned by the component, not passed in as a parameter.
 */
public class LoggerParameterRule implements LintRule {

    private static final String RULE_ID = "JBCT-LOG-02";
    private static final String DOC_LINK = "https://github.com/siy/coding-technology/blob/main/skills/jbct/SKILL.md";

    private static final Set<String> LOGGER_TYPES = Set.of(
            "Logger", "Log",
            "org.slf4j.Logger",
            "java.util.logging.Logger",
            "org.apache.logging.log4j.Logger",
            "org.apache.commons.logging.Log"
    );

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public String description() {
        return "Logger should be owned by component, not passed as parameter";
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
                .flatMap(method -> checkParameters(method, ctx));
    }

    private Stream<Diagnostic> checkParameters(MethodDeclaration method, LintContext ctx) {
        return method.getParameters().stream()
                .filter(this::isLoggerParameter)
                .map(param -> createDiagnostic(method, param, ctx));
    }

    private boolean isLoggerParameter(Parameter param) {
        var typeName = param.getTypeAsString();

        // Check for exact matches or partial matches
        if (LOGGER_TYPES.contains(typeName)) {
            return true;
        }

        // Check for simple name Logger or Log
        return typeName.equals("Logger") || typeName.equals("Log");
    }

    private Diagnostic createDiagnostic(MethodDeclaration method, Parameter param, LintContext ctx) {
        var line = param.getBegin().map(p -> p.line).orElse(1);
        var column = param.getBegin().map(p -> p.column).orElse(1);

        var methodName = method.getNameAsString();
        var paramName = param.getNameAsString();

        return Diagnostic.diagnostic(
                RULE_ID,
                ctx.severityFor(RULE_ID),
                ctx.fileName(),
                line,
                column,
                "Method '" + methodName + "' has Logger parameter '" + paramName + "'; logger should be owned by component",
                "Passing loggers as parameters breaks encapsulation. " +
                        "Each component should own its own logger."
        ).withExample("""
                // Before: logger as parameter
                public void process(Data data, Logger log) {
                    log.info("Processing: {}", data);
                }

                // After: logger owned by component
                public class DataProcessor {
                    private static final Logger log = LoggerFactory.getLogger(DataProcessor.class);

                    public void process(Data data) {
                        log.info("Processing: {}", data);
                    }
                }
                """)
                .withDocLink(DOC_LINK);
    }
}
