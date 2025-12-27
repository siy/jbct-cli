package org.pragmatica.jbct.parser;

import org.junit.jupiter.api.Test;
import org.pragmatica.jbct.parser.Java25Parser.CstNode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the generated Java25Parser.
 */
class Java25ParserTest {

    private final Java25Parser parser = new Java25Parser();

    @Test
    void parseEmptyClass() {
        var result = parser.parse("class Foo { }");
        assertTrue(result.isSuccess(), () -> "Failed: " + result);

        var cst = result.unwrap();
        assertEquals("CompilationUnit", cst.rule().name());
    }

    @Test
    void parseClassWithField() {
        var result = parser.parse("class C { int x; }");
        assertTrue(result.isSuccess(), () -> "Failed: " + result);
    }

    @Test
    void parseClassWithMethod() {
        var result = parser.parse("""
            class C {
                void foo() { }
            }
            """);
        assertTrue(result.isSuccess(), () -> "Failed: " + result);
    }

    @Test
    void parseRecord() {
        var result = parser.parse("record Point(int x, int y) { }");
        assertTrue(result.isSuccess(), () -> "Failed: " + result);
    }

    @Test
    void parseMethodWithBody() {
        var result = parser.parse("""
            class C {
                int add(int a, int b) {
                    return a + b;
                }
            }
            """);
        assertTrue(result.isSuccess(), () -> "Failed: " + result);
    }

    @Test
    void parsePackageAndImports() {
        var result = parser.parse("""
            package com.example;

            import java.util.List;
            import java.util.*;

            public class Foo { }
            """);
        assertTrue(result.isSuccess(), () -> "Failed: " + result);
    }

    @Test
    void parseLambda() {
        var result = parser.parse("""
            class C {
                void test() {
                    Runnable r = () -> { };
                }
            }
            """);
        assertTrue(result.isSuccess(), () -> "Failed: " + result);
    }

    @Test
    void parseMethodChain() {
        var result = parser.parse("""
            class C {
                void test() {
                    list.stream()
                        .map(x -> x)
                        .filter(x -> true)
                        .toList();
                }
            }
            """);
        assertTrue(result.isSuccess(), () -> "Failed: " + result);
    }

    @Test
    void cstPreservesSourceLocation() {
        var result = parser.parse("class Foo { }");
        assertTrue(result.isSuccess());

        var cst = result.unwrap();
        var span = cst.span();

        assertEquals(1, span.start().line());
        assertEquals(1, span.start().column());
    }

    @Test
    void cstHasChildren() {
        var result = parser.parse("class Foo { int x; }");
        assertTrue(result.isSuccess());

        var cst = result.unwrap();
        assertTrue(cst instanceof CstNode.NonTerminal);

        var root = (CstNode.NonTerminal) cst;
        assertFalse(root.children().isEmpty());
    }

    @Test
    void parseSwitchExpressionWithThrow() {
        // Test switch expression with throw in default case
        var result = parser.parse("""
            class C {
                String test(Object o) {
                    return switch (o) {
                        default -> throw new IllegalStateException();
                    };
                }
            }
            """);
        assertTrue(result.isSuccess(), () -> "Failed: " + result);
    }

    @Test
    void parseSwitchExpressionSimple() {
        // Test simple switch expression without throw
        var result = parser.parse("""
            class C {
                String test(Object o) {
                    return switch (o) {
                        default -> "foo";
                    };
                }
            }
            """);
        assertTrue(result.isSuccess(), () -> "Failed: " + result);
    }

    @Test
    void parseGuardWithWhenKeyword() {
        // Test 'when' guard in switch - ensure word boundary works
        var result = parser.parse("""
            class C {
                String test(Object o) {
                    return switch (o) {
                        case String s when s.isEmpty() -> "empty";
                        default -> "other";
                    };
                }
            }
            """);
        assertTrue(result.isSuccess(), () -> "Failed: " + result);
    }

    @Test
    void parseIdentifierStartingWithKeyword() {
        // Test that identifiers starting with keywords are parsed correctly
        var result = parser.parse("""
            class C {
                void switchCase() {}
                void ifCondition() {}
                void whileLoop() {}
                void forLoop() {}
                void doWork() {}
                void tryAgain() {}
                void catchError() {}
                void finallyDone() {}
            }
            """);
        assertTrue(result.isSuccess(), () -> "Failed: " + result);
    }
}
