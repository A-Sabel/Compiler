package compiler.semantics;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public final class SymbolTable {
    // Stack of scopes: Maps variable names to their data types
    private final Deque<Map<String, String>> scopes;

    public SymbolTable() {
        this.scopes = new ArrayDeque<>();
        this.scopes.push(new HashMap<>()); // Initialize the global scope
    }

    public void enterScope() {
        scopes.push(new HashMap<>());
    }

    public void exitScope() {
        if (scopes.size() > 1) {
            scopes.pop();
        }
    }

    public void defineVariable(String name, String type) {
        scopes.peek().put(name, type);
    }

    // Searches from the innermost scope outward
    public String lookupVariableType(String name) {
        for (Map<String, String> scope : scopes) {
            if (scope.containsKey(name)) {
                return scope.get(name);
            }
        }
        return null; // Variable not found
    }

    public boolean isDefinedInCurrentScope(String name) {
        return scopes.peek().containsKey(name);
    }
}
