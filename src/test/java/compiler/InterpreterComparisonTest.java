package compiler;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import compiler.vm.Interpreter;

/**
 * Regression tests for comparison operators in conditional branches.
 * Ensures that EQUAL, NOT_EQUAL, LESS_THAN, LESS_EQUAL, GREATER_THAN,
 * and GREATER_EQUAL opcodes are properly evaluated in if/else statements.
 */
public class InterpreterComparisonTest {

    public static void main(String[] args) {
        System.out.println("=== Interpreter Comparison Tests ===\n");

        testGreaterThanFalse();
        testGreaterThanTrue();
        testLessThanFalse();
        testLessThanTrue();
        testEqualTrue();
        testEqualFalse();
        testNotEqualTrue();
        testNotEqualFalse();
        testGreaterEqualTrue();
        testLessEqualTrue();

        System.out.println("\n=== All tests passed ===");
    }

    private static void testGreaterThanFalse() {
        String source = "class LogicTest { void main() { int x = 50; int y = 100; if (x > y) { print(1); } else { print(0); } } }";
        String result = runCompilerTest(source);
        assertEqual(result.trim(), "0", "testGreaterThanFalse: 50 > 100 should be false");
    }

    private static void testGreaterThanTrue() {
        String source = "class LogicTest { void main() { int x = 100; int y = 50; if (x > y) { print(1); } else { print(0); } } }";
        String result = runCompilerTest(source);
        assertEqual(result.trim(), "1", "testGreaterThanTrue: 100 > 50 should be true");
    }

    private static void testLessThanFalse() {
        String source = "class LogicTest { void main() { int x = 100; int y = 50; if (x < y) { print(1); } else { print(0); } } }";
        String result = runCompilerTest(source);
        assertEqual(result.trim(), "0", "testLessThanFalse: 100 < 50 should be false");
    }

    private static void testLessThanTrue() {
        String source = "class LogicTest { void main() { int x = 50; int y = 100; if (x < y) { print(1); } else { print(0); } } }";
        String result = runCompilerTest(source);
        assertEqual(result.trim(), "1", "testLessThanTrue: 50 < 100 should be true");
    }

    private static void testEqualTrue() {
        String source = "class LogicTest { void main() { int x = 42; int y = 42; if (x == y) { print(1); } else { print(0); } } }";
        String result = runCompilerTest(source);
        assertEqual(result.trim(), "1", "testEqualTrue: 42 == 42 should be true");
    }

    private static void testEqualFalse() {
        String source = "class LogicTest { void main() { int x = 42; int y = 43; if (x == y) { print(1); } else { print(0); } } }";
        String result = runCompilerTest(source);
        assertEqual(result.trim(), "0", "testEqualFalse: 42 == 43 should be false");
    }

    private static void testNotEqualTrue() {
        String source = "class LogicTest { void main() { int x = 42; int y = 43; if (x != y) { print(1); } else { print(0); } } }";
        String result = runCompilerTest(source);
        assertEqual(result.trim(), "1", "testNotEqualTrue: 42 != 43 should be true");
    }

    private static void testNotEqualFalse() {
        String source = "class LogicTest { void main() { int x = 42; int y = 42; if (x != y) { print(1); } else { print(0); } } }";
        String result = runCompilerTest(source);
        assertEqual(result.trim(), "0", "testNotEqualFalse: 42 != 42 should be false");
    }

    private static void testGreaterEqualTrue() {
        String source = "class LogicTest { void main() { int x = 100; int y = 100; if (x >= y) { print(1); } else { print(0); } } }";
        String result = runCompilerTest(source);
        assertEqual(result.trim(), "1", "testGreaterEqualTrue: 100 >= 100 should be true");
    }

    private static void testLessEqualTrue() {
        String source = "class LogicTest { void main() { int x = 50; int y = 100; if (x <= y) { print(1); } else { print(0); } } }";
        String result = runCompilerTest(source);
        assertEqual(result.trim(), "1", "testLessEqualTrue: 50 <= 100 should be true");
    }

    private static String runCompilerTest(String source) {
        try {
            CompilerPipeline pipeline = new CompilerPipeline();
            CompilerPipeline.CompileResult result = pipeline.compile(source);

            if (!result.errors.isEmpty()) {
                System.err.println("Compilation errors: " + result.errors);
                return "";
            }

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            PrintStream originalOut = System.out;
            try {
                System.setOut(new PrintStream(buffer));
                Interpreter interpreter = new Interpreter();
                interpreter.execute(result.instructions);
            } finally {
                System.setOut(originalOut);
            }

            return buffer.toString();
        } catch (Exception ex) {
            ex.printStackTrace();
            return "";
        }
    }

    private static void assertEqual(String actual, String expected, String message) {
        if (actual.equals(expected)) {
            System.out.println("✓ " + message);
        } else {
            System.err.println("✗ " + message + " (got: '" + actual + "', expected: '" + expected + "')");
            System.exit(1);
        }
    }
}
