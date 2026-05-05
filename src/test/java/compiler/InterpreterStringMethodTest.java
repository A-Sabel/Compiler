package compiler;

import compiler.vm.Interpreter;

public class InterpreterStringMethodTest {

    public static void main(String[] args) {
        System.out.println("=== Interpreter String method tests ===\n");

        testStringLength();
        testStringLengthLiteral();

        System.out.println("\n=== All string tests passed ===");
    }

    private static void testStringLength() {
        String source = "class Test8 { public static void main() { String word = \"Code\"; System.out.println(word.length()); } }";
        String result = runCompilerTest(source).trim();
        assertEqual(result, "4", "testStringLength: word.length() should print 4");
    }

    private static void testStringLengthLiteral() {
        String source = "class Test8 { public static void main() { System.out.println(\"Hello\".length()); } }";
        String result = runCompilerTest(source).trim();
        assertEqual(result, "5", "testStringLengthLiteral: \"Hello\".length() should print 5");
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
