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

    public ASTNode(String type, String value) {
        this.type     = type;
        this.value    = value;
        this.children = new ArrayList<>();
    }

    public ASTNode(String type) {
        this(type, null);
    }

    // ── Child Management ───────────────────────────────────────────────────────
    public void addChild(ASTNode child) {
        if (child != null) children.add(child);
    }

    // ── Getters ────────────────────────────────────────────────────────────────
    public String       getType()     { return type; }
    public String       getValue()    { return value; }
    public List<ASTNode> getChildren() { return children; }

    // ── Pretty-print (used by ASTExporter) ────────────────────────────────────
    public String toDisplayString() {
        return value != null ? type + "(" + value + ")" : type;
    }

    // ── Node factory helpers (keeps Parser code readable) ─────────────────────
    public static ASTNode of(String type)                   { return new ASTNode(type); }
    public static ASTNode of(String type, String value)     { return new ASTNode(type, value); }
}
