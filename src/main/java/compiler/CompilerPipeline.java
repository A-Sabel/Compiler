package compiler;

import java.util.List;

import compiler.codegen.BytecodeGenerator;
import compiler.codegen.Instruction;
import compiler.lexer.Lexer;
import compiler.lexer.models.Tokens;
import compiler.optimizer.Optimizer;
import compiler.parser.Parser;
import compiler.parser.ast.ASTNode;
import compiler.semantics.SemanticAnalyzer;
import compiler.util.ErrorHandler;
import compiler.vm.Interpreter;

public final class CompilerPipeline {

    public static final class CompileResult {
        public final List<Tokens> tokens;
        public final ASTNode parsedAst;
        public final ASTNode optimizedAst;
        public final List<Instruction> instructions;
        public final List<String> errors;
        public final List<String> warnings;

        private CompileResult(List<Tokens> tokens,
                              ASTNode parsedAst,
                              ASTNode optimizedAst,
                              List<Instruction> instructions,
                              List<String> errors,
                              List<String> warnings) {
            this.tokens = tokens;
            this.parsedAst = parsedAst;
            this.optimizedAst = optimizedAst;
            this.instructions = instructions;
            this.errors = errors;
            this.warnings = warnings;
        }

        public boolean hasErrors() {
            return errors != null && !errors.isEmpty();
        }
    }

    public CompileResult compile(String source) {
        ErrorHandler.clear();

        Lexer lexer = new Lexer(source);
        List<Tokens> tokens = lexer.tokenize();

        Parser parser = new Parser(tokens);
        ASTNode parsedAst = parser.parse();

        SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer();
        semanticAnalyzer.analyze(parsedAst);

        Optimizer optimizer = new Optimizer();
        Optimizer.OptimizeResult optimizeResult = optimizer.optimize(parsedAst);
        ASTNode optimizedAst = optimizeResult.node;

        BytecodeGenerator bytecodeGenerator = new BytecodeGenerator();
        List<Instruction> instructions = bytecodeGenerator.generate(optimizedAst);

        return new CompileResult(
                tokens,
                parsedAst,
                optimizedAst,
                instructions,
                ErrorHandler.getErrors(),
                ErrorHandler.getWarnings());
    }

    public void compileAndRun(String source) {
        CompileResult result = compile(source);
        if (result.hasErrors()) {
            for (String error : result.errors) {
                System.err.println(error);
            }
            return;
        }

        if (result.warnings != null && !result.warnings.isEmpty()) {
            for (String warning : result.warnings) {
                System.err.println(warning);
            }
        }

        Interpreter interpreter = new Interpreter();
        interpreter.execute(result.instructions);
    }
}