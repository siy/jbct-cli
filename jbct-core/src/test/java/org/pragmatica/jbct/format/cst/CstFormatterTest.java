package org.pragmatica.jbct.format.cst;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pragmatica.jbct.parser.Java25Parser;
import org.pragmatica.jbct.parser.Java25Parser.CstNode;
import org.pragmatica.jbct.shared.SourceFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for CST-based formatter.
 */
class CstFormatterTest {

    private static final Path EXAMPLES_DIR = Path.of("src/test/resources/format-examples");

    private CstFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = CstFormatter.cstFormatter();
    }

    @Test
    void format_simpleClass() {
        var source = new SourceFile(
            Path.of("Test.java"),
            """
            package com.example;

            public class Test {
                public String hello() {
                    return "world";
                }
            }
            """
        );

        formatter.format(source)
            .onFailure(cause -> fail("Format failed: " + cause.message()))
            .onSuccess(formatted -> {
                System.out.println("=== Formatted output ===");
                System.out.println(formatted.content());
                assertThat(formatted.content()).contains("package com.example;");
                assertThat(formatted.content()).contains("public class Test");
            });
    }

    @Test
    void format_chainAlignment() {
        var source = new SourceFile(
            Path.of("Chain.java"),
            """
            package test;
            import org.pragmatica.lang.Result;
            class Chain {
                Result<String> test(Result<String> input) {
                    return input.map(String::trim).map(String::toUpperCase);
                }
            }
            """
        );

        formatter.format(source)
            .onFailure(cause -> fail("Format failed: " + cause.message()))
            .onSuccess(formatted -> {
                System.out.println("=== Chain output ===");
                System.out.println(formatted.content());
                // Should align continuation to receiver end
            });
    }

    @Test
    void format_goldenExample_chainAlignment() throws IOException {
        var path = EXAMPLES_DIR.resolve("ChainAlignment.java");
        var content = Files.readString(path);
        var source = new SourceFile(path, content);

        formatter.format(source)
            .onFailure(cause -> fail("Format failed: " + cause.message()))
            .onSuccess(formatted -> {
                System.out.println("=== ChainAlignment.java output ===");
                System.out.println(formatted.content());
                System.out.println("=== Expected ===");
                System.out.println(content);
            });
    }

    @Test
    void debug_cstTrivia() {
        var parser = new Java25Parser();
        // Test with comment
        var code = "class Foo { // comment\n  void bar() { } }";
        var result = parser.parse(code);
        System.out.println("Parse result: " + result);
        assertTrue(result.isSuccess(), () -> "Parse failed: " + result);
        result.onSuccess(cst -> {
            dumpCst(cst, 0, code);
        });
    }

    private void dumpCst(CstNode node, int depth, String source) {
        var indent = "  ".repeat(depth);
        var trivia = "leading=" + node.leadingTrivia().size() + ", trailing=" + node.trailingTrivia().size();
        switch (node) {
            case CstNode.Terminal t -> System.out.println(indent + "Terminal(" + t.rule() + "): '" + t.text() + "' [" + trivia + "]");
            case CstNode.Token tok -> System.out.println(indent + "Token(" + tok.rule() + "): '" + tok.text() + "' [" + trivia + "]");
            case CstNode.NonTerminal nt -> {
                System.out.println(indent + "NonTerminal(" + nt.rule() + ") [" + trivia + "]");
                for (var child : nt.children()) {
                    dumpCst(child, depth + 1, source);
                }
            }
        }
    }
}
