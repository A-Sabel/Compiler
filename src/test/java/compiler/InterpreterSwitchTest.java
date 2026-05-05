package compiler;

import compiler.vm.Interpreter;

public class InterpreterSwitchTest {

    public static void main(String[] args) {
        System.out.println("=== Interpreter Switch Tests ===\n");

        testSwitchCaseWithBreak();
        testSwitchCaseFallthrough();
        testSwitchDefault();

        System.out.println("\n=== All tests passed ===");
    }

    private static void testSwitchCaseWithBreak() {
        String source = "class SwitchTest { void main() { int x = 2; switch (x) { case 1: print(10); break; case 2: print(20); break; default: print(30); } } }";
        String result = runCompilerTest(source);
        assertEqual(result.trim(), "20", "testSwitchCaseWithBreak: switch(2) should print 20");
    }

    private static void testSwitchCaseFallthrough() {
        String source = "class SwitchTest { void main() { int x = 1; switch (x) { case 1: print(10); case 2: print(20); break; default: print(30); } } }";
        String result = runCompilerTest(source);
        assertEqual(result.trim(), "1020", "testSwitchCaseFallthrough: switch(1) should fall through to case 2 and print 1020");
    }

    private static void testSwitchDefault() {
        String source = "class SwitchTest { void main() { int x = 5; switch (x) { case 1: print(10); break; case 2: print(20); break; default: print(30); } } }";
        String result = runCompilerTest(source);
        assertEqual(result.trim(), "30", "testSwitchDefault: switch(5) should print default 30");
    }

    private static String runCompilerTest(String source) {
        try {
            CompilerPipeline pipeline = new CompilerPipeline();
            CompilerPipeline.CompileResult result = pipeline.compile(source);

            if (!result.errors.isEmpty()) {
                System.err.println("Compilation errors: " + result.errors);
                return "";
            }

            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            java.io.PrintStream originalOut = System.out;
            try {
                System.setOut(new java.io.PrintStream(buffer));
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
