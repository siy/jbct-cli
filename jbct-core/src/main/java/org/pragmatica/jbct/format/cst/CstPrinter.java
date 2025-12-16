package org.pragmatica.jbct.format.cst;

import org.pragmatica.jbct.format.FormatterConfig;
import org.pragmatica.jbct.parser.Java25Parser.CstNode;
import org.pragmatica.jbct.parser.Java25Parser.Trivia;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.pragmatica.jbct.parser.CstNodes.*;

/**
 * CST printer for JBCT formatting.
 *
 * Key formatting rules:
 * - Chain alignment: `.` aligns to end of first method call
 * - Arguments: align to opening `(`
 * - Imports: grouped (pragmatica, java/javax, static)
 * - 120 char max line length, 4 space indent
 */
public class CstPrinter {

    private final FormatterConfig config;
    private final String source;
    private final StringBuilder output;
    private int currentColumn;
    private int indentLevel;

    // Alignment tracking
    private final Deque<Integer> alignmentStack = new ArrayDeque<>();
    private int chainAlignColumn = -1;

    // Keywords that shouldn't have preceding newlines removed
    private static final Set<String> BLOCK_KEYWORDS = Set.of(
        "if", "else", "for", "while", "do", "try", "catch", "finally", "switch", "case", "default"
    );

    public CstPrinter(FormatterConfig config, String source) {
        this.config = config;
        this.source = source;
        this.output = new StringBuilder();
        this.currentColumn = 0;
        this.indentLevel = 0;
    }

    public String print(CstNode root) {
        printNode(root);
        return output.toString().stripTrailing() + "\n";
    }

    private void printNode(CstNode node) {
        // Print leading trivia
        printTrivia(node.leadingTrivia());

        switch (node) {
            case CstNode.Terminal t -> printTerminal(t);
            case CstNode.Token tok -> printToken(tok);
            case CstNode.NonTerminal nt -> printNonTerminal(nt);
        }

        // Print trailing trivia
        printTrivia(node.trailingTrivia());
    }

    private void printNodeSkipTrivia(CstNode node) {
        // Skip whitespace trivia but preserve comments
        printCommentsOnly(node.leadingTrivia());

        switch (node) {
            case CstNode.Terminal t -> printTerminal(t);
            case CstNode.Token tok -> printToken(tok);
            case CstNode.NonTerminal nt -> printNonTerminal(nt);
        }

        printCommentsOnly(node.trailingTrivia());
    }

    private void printCommentsOnly(List<Trivia> triviaList) {
        for (var trivia : triviaList) {
            switch (trivia) {
                case Trivia.LineComment lc -> {
                    print(lc.text());
                    println();
                    printIndent();  // Restore indent after line comment
                }
                case Trivia.BlockComment bc -> {
                    print(bc.text());
                }
                case Trivia.Whitespace _ -> {
                    // Skip whitespace - we control layout
                }
            }
        }
    }

    private void printTerminal(CstNode.Terminal terminal) {
        print(terminal.text());
    }

    private void printToken(CstNode.Token token) {
        print(token.text());
    }

    private void printNonTerminal(CstNode.NonTerminal nt) {
        var rule = nt.rule();

        // Handle special formatting rules
        switch (rule) {
            case "CompilationUnit" -> printCompilationUnit(nt);
            case "ImportDecl" -> printImportDecl(nt);
            case "ClassDecl", "InterfaceDecl", "EnumDecl", "RecordDecl" -> printTypeDecl(nt);
            case "MethodDecl", "ConstructorDecl" -> printMethodDecl(nt);
            case "ClassBody" -> printClassBody(nt);
            case "Block" -> printBlock(nt);
            case "Stmt" -> printStatement(nt);
            case "Expr" -> printExpression(nt);
            case "Primary" -> printPrimary(nt);
            case "PostOp" -> printPostOp(nt);
            case "Args" -> printArgs(nt);
            case "Lambda" -> printLambda(nt);
            case "Ternary" -> printTernary(nt);
            default -> printChildren(nt);
        }
    }

    private void printCompilationUnit(CstNode.NonTerminal cu) {
        // Print package declaration
        childByRule(cu, "PackageDecl").fold(
            () -> null,
            pkg -> {
                printNode(pkg);
                return null;
            }
        );

        // Collect and organize imports
        var imports = childrenByRule(cu, "ImportDecl");
        if (!imports.isEmpty()) {
            println();
            println();
            printOrganizedImports(imports);
        }

        // Print type declarations
        var types = childrenByRule(cu, "TypeDecl");
        for (var type : types) {
            println();
            println();
            printNodeSkipTrivia(type);  // Skip leading trivia - we control blank lines
        }
    }

    private void printOrganizedImports(List<CstNode> imports) {
        // Group imports: pragmatica, java/javax, other, static
        var pragmatica = imports.stream()
            .filter(i -> text(i, source).contains("org.pragmatica"))
            .filter(i -> !text(i, source).contains("static"))
            .toList();

        var javaImports = imports.stream()
            .filter(i -> text(i, source).contains("java.") || text(i, source).contains("javax."))
            .filter(i -> !text(i, source).contains("static"))
            .toList();

        var otherImports = imports.stream()
            .filter(i -> !text(i, source).contains("org.pragmatica"))
            .filter(i -> !text(i, source).contains("java."))
            .filter(i -> !text(i, source).contains("javax."))
            .filter(i -> !text(i, source).contains("static"))
            .toList();

        var staticImports = imports.stream()
            .filter(i -> text(i, source).contains("static"))
            .toList();

        boolean needsBlank = false;

        if (!pragmatica.isEmpty()) {
            for (var imp : pragmatica) {
                printImportDecl((CstNode.NonTerminal) imp);
            }
            needsBlank = true;
        }

        if (!javaImports.isEmpty()) {
            if (needsBlank) println();
            for (var imp : javaImports) {
                printImportDecl((CstNode.NonTerminal) imp);
            }
            needsBlank = true;
        }

        if (!otherImports.isEmpty()) {
            if (needsBlank) println();
            for (var imp : otherImports) {
                printImportDecl((CstNode.NonTerminal) imp);
            }
            needsBlank = true;
        }

        if (!staticImports.isEmpty()) {
            if (needsBlank) println();
            for (var imp : staticImports) {
                printImportDecl((CstNode.NonTerminal) imp);
            }
        }
    }

    private void printImportDecl(CstNode.NonTerminal imp) {
        var importText = text(imp, source).trim();
        print(importText);
        println();
    }

    private void printTypeDecl(CstNode.NonTerminal type) {
        printChildren(type);
    }

    private void printMethodDecl(CstNode.NonTerminal method) {
        printChildren(method);
    }

    private void printClassBody(CstNode.NonTerminal classBody) {
        var children = children(classBody);

        // Find and print opening brace with its trivia
        for (var child : children) {
            if (isTerminalWithText(child, "{")) {
                printNode(child);
                break;
            }
        }

        indentLevel++;
        println();

        boolean first = true;
        CstNode prevMember = null;
        for (var child : children) {
            if (child.rule().equals("ClassMember")) {
                if (!first && needsBlankLineBefore(child, prevMember)) {
                    println();
                }
                printIndent();
                printNodeSkipTrivia(child);  // Skip trivia - we control layout
                println();
                first = false;
                prevMember = child;
            } else if (!isTerminalWithText(child, "{") && !isTerminalWithText(child, "}")) {
                printNode(child);
            }
        }

        indentLevel--;
        printIndent();

        // Find and print closing brace
        for (var child : children) {
            if (isTerminalWithText(child, "}")) {
                printNode(child);
                break;
            }
        }
    }

    private void printBlock(CstNode.NonTerminal block) {
        var children = children(block);

        // Find and print opening brace with its trivia
        for (var child : children) {
            if (isTerminalWithText(child, "{")) {
                printNode(child);
                break;
            }
        }

        indentLevel++;

        var stmts = children.stream()
            .filter(c -> c.rule().equals("BlockStmt"))
            .toList();

        if (!stmts.isEmpty()) {
            println();
            for (var stmt : stmts) {
                printIndent();
                printNodeSkipTrivia(stmt);  // Skip trivia - we control layout
                println();
            }
        }

        indentLevel--;
        printIndent();

        // Find and print closing brace with its trivia
        for (var child : children) {
            if (isTerminalWithText(child, "}")) {
                printNode(child);
                break;
            }
        }
    }

    private void printStatement(CstNode.NonTerminal stmt) {
        printChildren(stmt);
    }

    private void printExpression(CstNode.NonTerminal expr) {
        printChildren(expr);
    }

    private void printPrimary(CstNode.NonTerminal primary) {
        var primaryText = text(primary, source);

        // Check for method chain
        if (primaryText.contains(".") && primaryText.contains("(")) {
            printMethodChain(primary);
        } else {
            printChildren(primary);
        }
    }

    private void printMethodChain(CstNode.NonTerminal primary) {
        // Find all PostOp children (method calls in chain)
        var children = children(primary);

        // Print first part (receiver)
        boolean inChain = false;
        int chainStart = currentColumn;

        for (int i = 0; i < children.size(); i++) {
            var child = children.get(i);

            if ("PostOp".equals(child.rule())) {
                var postOpText = text(child, source);
                if (postOpText.startsWith(".") && postOpText.contains("(")) {
                    // This is a method call in chain
                    if (!inChain) {
                        // First chained call - print on same line, record align column
                        inChain = true;
                        chainAlignColumn = chainStart;
                    } else {
                        // Subsequent chained calls - align to chain start
                        println();
                        printAlignedTo(chainAlignColumn);
                    }
                }
                printNode(child);
            } else {
                printNode(child);
                if (!inChain) {
                    chainStart = currentColumn;
                }
            }
        }

        chainAlignColumn = -1;
    }

    private void printPostOp(CstNode.NonTerminal postOp) {
        printChildren(postOp);
    }

    private void printArgs(CstNode.NonTerminal args) {
        var argsText = text(args, source);
        int totalWidth = currentColumn + argsText.length();

        if (totalWidth <= config.maxLineLength()) {
            // Fits on one line
            printChildren(args);
        } else {
            // Break arguments onto multiple lines
            printBrokenArgs(args);
        }
    }

    private void printBrokenArgs(CstNode.NonTerminal args) {
        var children = children(args);
        int alignCol = currentColumn;  // Align to current position (after opening paren)

        boolean first = true;
        for (var child : children) {
            if (isTerminalWithText(child, ",")) {
                print(",");
                println();
                printAlignedTo(alignCol);
            } else if ("Expr".equals(child.rule())) {
                if (first) {
                    printNode(child);
                } else {
                    printNodeSkipTrivia(child);  // Skip trivia - we control alignment
                }
                first = false;
            } else {
                printNode(child);
            }
        }
    }

    private void printLambda(CstNode.NonTerminal lambda) {
        var children = children(lambda);
        boolean afterArrow = false;
        for (var child : children) {
            if (isTerminalWithText(child, "->")) {
                print(" -> ");  // Ensure spacing around arrow
                afterArrow = true;
            } else if (afterArrow) {
                printNodeSkipTrivia(child);  // Skip trivia after arrow - we added space
                afterArrow = false;
            } else {
                printNode(child);
            }
        }
    }

    private void printTernary(CstNode.NonTerminal ternary) {
        var ternaryText = text(ternary, source);

        // Check if it's actually a ternary (has ? and :)
        if (ternaryText.contains("?") && ternaryText.contains(":")) {
            // Multi-line ternary formatting
            var children = children(ternary);
            int alignCol = currentColumn;

            for (var child : children) {
                if (isTerminalWithText(child, "?")) {
                    println();
                    printAlignedTo(alignCol);
                    print("? ");
                } else if (isTerminalWithText(child, ":")) {
                    println();
                    printAlignedTo(alignCol);
                    print(": ");
                } else {
                    printNode(child);
                }
            }
        } else {
            printChildren(ternary);
        }
    }

    private void printChildren(CstNode.NonTerminal nt) {
        for (var child : children(nt)) {
            printNode(child);
        }
    }

    private void printTrivia(List<Trivia> triviaList) {
        for (var trivia : triviaList) {
            switch (trivia) {
                case Trivia.Whitespace ws -> {
                    // Normalize whitespace - preserve newlines but normalize spaces
                    var text = ws.text();
                    long newlines = text.chars().filter(c -> c == '\n').count();
                    if (newlines > 0) {
                        for (int i = 0; i < Math.min(newlines, 2); i++) {
                            println();
                        }
                    } else if (!text.isEmpty()) {
                        print(" ");
                    }
                }
                case Trivia.LineComment lc -> {
                    print(lc.text());
                }
                case Trivia.BlockComment bc -> {
                    print(bc.text());
                }
            }
        }
    }

    // ===== Helper methods =====

    private void print(String text) {
        output.append(text);
        int lastNewline = text.lastIndexOf('\n');
        if (lastNewline >= 0) {
            currentColumn = text.length() - lastNewline - 1;
        } else {
            currentColumn += text.length();
        }
    }

    private void println() {
        output.append("\n");
        currentColumn = 0;
    }

    private void printIndent() {
        String indent = " ".repeat(indentLevel * config.indentSize());
        print(indent);
    }

    private void printAlignedTo(int column) {
        if (currentColumn < column) {
            print(" ".repeat(column - currentColumn));
        }
    }

    private boolean isTerminalWithText(CstNode node, String text) {
        return switch (node) {
            case CstNode.Terminal t -> text.equals(t.text());
            case CstNode.Token tok -> text.equals(tok.text());
            case CstNode.NonTerminal _ -> false;
        };
    }

    private boolean needsBlankLineBefore(CstNode member, CstNode prevMember) {
        // Check if member is a simple interface method (no body)
        var memberText = text(member, source);

        // Simple heuristic: if both current and previous are simple interface methods
        // (end with ; without { }), don't add blank line
        boolean isSimple = memberText.trim().endsWith(";") && !memberText.contains("{");
        boolean prevIsSimple = prevMember != null &&
            text(prevMember, source).trim().endsWith(";") &&
            !text(prevMember, source).contains("{");

        // No blank line between consecutive simple interface methods
        if (isSimple && prevIsSimple) {
            return false;
        }

        // Add blank line otherwise
        return true;
    }
}
