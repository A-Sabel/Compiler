package compiler.parser.ast;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * UTILITY CLASS: ASTExporter
 *
 * Walks an ASTNode tree and writes it as indented text to a file.
 *
 * Example output for:  x = 5 + 3;
 *
 *   PROGRAM
 *   └── EXPR_STMT
 *       └── ASSIGN(=)
 *           ├── IDENTIFIER(x)
 *           └── BINARY_OP(+)
 *               ├── NUMBER(5)
 *               └── NUMBER(3)
 *
 * Usage:
 *   ASTExporter.export(rootNode, "ast_output.txt");
 */
public class ASTExporter {

    private ASTExporter() {} // utility class — no instances

    /**
     * Exports the AST rooted at {@code root} to the given file path.
     *
     * @param root     the PROGRAM node returned by Parser.parse()
     * @param filePath destination file (e.g. "ast_output.txt")
     * @throws IOException if the file cannot be written
     */
    public static void export(ASTNode root, String filePath) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("═══════════════════════════════════════════════════");
            writer.println("  Abstract Syntax Tree (AST) — Group 3 Compiler");
            writer.println("═══════════════════════════════════════════════════");
            writer.println();
            printNode(writer, root, "", true);
            writer.println();
            writer.println("═══════════════════════════════════════════════════");
            writer.println("  End of AST");
            writer.println("═══════════════════════════════════════════════════");
        }
    }

    // ── Recursive tree printer ─────────────────────────────────────────────────

    private static void printNode(PrintWriter writer, ASTNode node,
                                  String prefix, boolean isLast) {
        String connector = isLast ? "└── " : "├── ";
        writer.println(prefix + connector + node.toDisplayString());

        String childPrefix = prefix + (isLast ? "    " : "│   ");
        List<ASTNode> children = node.getChildren();
        for (int i = 0; i < children.size(); i++) {
            printNode(writer, children.get(i), childPrefix, i == children.size() - 1);
        }
    }
}
