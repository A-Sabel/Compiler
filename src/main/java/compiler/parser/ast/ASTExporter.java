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

    private ASTExporter() {}

    public static void export(ASTNode root, String filePath) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("═══════════════════════════════════════════════════");
            writer.println("  Abstract Syntax Tree (AST) — Group 3 Compiler");
            writer.println("═══════════════════════════════════════════════════");
            writer.println();

            // FIX: print root label directly without a connector,
            // then recurse into children with an empty base prefix.
            writer.println(root.toDisplayString());
            List<ASTNode> topChildren = root.getChildren();
            for (int i = 0; i < topChildren.size(); i++) {
                printNode(writer, topChildren.get(i), "", i == topChildren.size() - 1);
            }

            writer.println();
            writer.println("═══════════════════════════════════════════════════");
            writer.println("  End of AST");
            writer.println("═══════════════════════════════════════════════════");
        }
    }

    private static void printNode(PrintWriter writer, ASTNode node,
                                String prefix, boolean isLast) {
        String connector   = isLast ? "└── " : "├── ";
        writer.println(prefix + connector + node.toDisplayString());

        // FIX: isLast=true  → no more siblings, so children use blank padding "    "
        //      isLast=false → siblings follow below, so children use pipe    "│   "
        String childPrefix = prefix + (isLast ? "    " : "│   ");
        List<ASTNode> children = node.getChildren();
        for (int i = 0; i < children.size(); i++) {
            printNode(writer, children.get(i), childPrefix, i == children.size() - 1);
        }
    }
}