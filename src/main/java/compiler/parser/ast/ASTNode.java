package compiler.parser.ast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BASE CLASS: ASTNode
 * Every node in the Abstract Syntax Tree extends this.
 * Each node has a type label, an optional value, and a list of children.
 */
public class ASTNode {
    private final String type;      // e.g., "ASSIGN", "IF", "BINARY_OP"
    private final String value;     // e.g., "=", "+", "x", "5"
    private final List<ASTNode> children;
    private final Map<String, String> attributes;
    private final int line;
    private final int column;

    public ASTNode(String type, String value, int line, int column) {
        this.type = type;
        this.value = value;
        this.line = line;
        this.column = column;
        this.children = new ArrayList<>();
        this.attributes = new HashMap<>();
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

    public void setChildren(List<ASTNode> newChildren) {
        children.clear();
        if (newChildren != null) {
            children.addAll(newChildren);
        }
    }

    public void setAttribute(String name, String value) {
        if (name != null) {
            if (value == null) {
                attributes.remove(name);
            } else {
                attributes.put(name, value);
            }
        }
    }

    public String getAttribute(String name) {
        return name == null ? null : attributes.get(name);
    }

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
