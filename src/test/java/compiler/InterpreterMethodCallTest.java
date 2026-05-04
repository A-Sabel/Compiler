package compiler;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import compiler.vm.Interpreter;

/**
 * Regression tests for method calls, including nested method calls.
 * Ensures that instance methods are called with the correct receiver ('this'),
 * parameters are passed to the correct slots, and return values propagate correctly.
 */
public class InterpreterMethodCallTest {

    public static void main(String[] args) {
        System.out.println("=== Interpreter Method Call Tests ===\n");

        testSimpleMethodCall();
        testMultipleParameters();
        testNestedMethodCall();
        testMultipleNestedCalls();
        testMethodReturnValue();

        System.out.println("\n=== All tests passed ===");
    }

    private static void testSimpleMethodCall() {
        String source = "class Test { int getValue() { return 42; } void main() { print(getValue()); } }";
        String result = runCompilerTest(source);
        assertEqual(result.trim(), "42", "testSimpleMethodCall: getValue should return 42");
    }

    private static void testMultipleParameters() {
        String source = "class Test { int add(int a, int b) { return a + b; } void main() { print(add(3, 4)); } }";
        String result = runCompilerTest(source);
        assertEqual(result.trim(), "7", "testMultipleParameters: add(3, 4) should return 7");
    }

    private static void testNestedMethodCall() {
        String source = "class Test { int add(int a, int b) { return a + b; } int multiply(int x, int y) { return x * y; } void main() { print(multiply(2, add(3, 4))); } }";
        String result = runCompilerTest(source);
        assertEqual(result.trim(), "14", "testNestedMethodCall: multiply(2, add(3, 4)) should return 14");
    }

    private static void testMultipleNestedCalls() {
        String source = "class Test { int add(int a, int b) { return a + b; } void main() { int x = add(1, 2); int y = add(x, 3); print(y); } }";
        String result = runCompilerTest(source);
        assertEqual(result.trim(), "6", "testMultipleNestedCalls: add(add(1, 2), 3) should return 6");
    }

    private static void testMethodReturnValue() {
        String source = "class Test { int square(int n) { return n * n; } void main() { int result = square(5); print(result); } }";
        String result = runCompilerTest(source);
        assertEqual(result.trim(), "25", "testMethodReturnValue: square(5) should return 25");
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
