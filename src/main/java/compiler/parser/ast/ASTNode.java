package compiler.parser.ast;

import java.util.ArrayList;
import java.util.List;

/**
 * BASE CLASS: ASTNode
 * Every node in the Abstract Syntax Tree extends this.
 * Each node has a type label, an optional value, and a list of children.
 */
public class ASTNode {
    private final String type;      // e.g., "ASSIGN", "IF", "BINARY_OP"
    private final String value;     // e.g., "=", "+", "x", "5"
    private final List<ASTNode> children;
    private final int line;
    private final int column;

    public ASTNode(String type, String value, int line, int column) {
        this.type = type;
        this.value = value;
        this.line = line;
        this.column = column;
        this.children = new ArrayList<>();
    }

    public ASTNode(String type, int line, int column) {
        this(type, null, line, column);
    }

    // ── Child Management ───────────────────────────────────────────────────────
    public void addChild(ASTNode child) {
        if (child != null) children.add(child);
    }

    // ── Getters ────────────────────────────────────────────────────────────────
    public String       getType()     { return type; }
    public String       getValue()    { return value; }
    public int getLine() { return line; }
    public int getColumn() { return column; }
    public List<ASTNode> getChildren() { return children; }

    // ── Pretty-print (used by ASTExporter) ────────────────────────────────────
    public String toDisplayString() {
        return value != null ? type + "(" + value + ")" : type;
    }

    // Updated Factory Helpers
    public static ASTNode of(String type, int line, int column) {
        return new ASTNode(type, line, column);
    }
    public static ASTNode of(String type, String value, int line, int column) {
        return new ASTNode(type, value, line, column);
    }
    public static ASTNode of(String type, String value) {
        return new ASTNode(type, value, -1, -1);
    }
    // Fallback for root nodes
    public static ASTNode of(String type) {
        return new ASTNode(type, -1, -1);
    }
}
