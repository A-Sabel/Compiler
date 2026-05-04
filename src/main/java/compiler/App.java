package compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class App {

    public static void main(String[] args) {
        CompilerPipeline pipeline = new CompilerPipeline();

        if (args.length == 0) {
            printUsage();
            return;
        }

        try {
            if ("--source".equals(args[0]) && args.length >= 2) {
                pipeline.compileAndRun(args[1]);
                return;
            }

            if ("--file".equals(args[0]) && args.length >= 2) {
                Path path = Paths.get(args[1]);
                String source = Files.readString(path);
                pipeline.compileAndRun(source);
                return;
            }

            if (Files.exists(Paths.get(args[0]))) {
                String source = Files.readString(Paths.get(args[0]));
                pipeline.compileAndRun(source);
                return;
            }

            System.err.println("Unknown arguments.");
            printUsage();
        } catch (IOException ex) {
            System.err.println("Could not read source: " + ex.getMessage());
        }
    }

    private static void printUsage() {
        System.out.println("Compiler launcher");
        System.out.println("Usage:");
        System.out.println("  java compiler.App --file path/to/source.txt");
        System.out.println("  java compiler.App --source \"class Main { ... }\"");
        System.out.println();
        System.out.println("The launcher compiles source through lexer -> parser -> semantics -> optimizer -> bytecode -> interpreter.");
    }
}
