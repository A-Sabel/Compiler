package compiler.semantics;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SymbolTable {
    // Stack of scopes for variables: Maps variable names to their data types
    private final Deque<Map<String, String>> scopes;
    
    // Global registry for methods: MethodName -> List of overloads
    private final Map<String, List<MethodSignature>> methods;

    // Helper class to store method metadata
    public static class MethodSignature {
        public String returnType;
        public List<String> parameterTypes;
        
        public MethodSignature(String returnType, List<String> parameterTypes) {
            this.returnType = returnType;
            this.parameterTypes = parameterTypes;
        }
    }

    public SymbolTable() {
        this.scopes = new ArrayDeque<>();
        this.methods = new HashMap<>();
        this.scopes.push(new HashMap<>()); // Initialize the global scope
    }

    // --- Variable Management ---
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

    public boolean isDeclaredInCurrentScope(String name) {
        if (scopes.isEmpty()) return false;
        return scopes.peek().containsKey(name); // Checks ONLY the current block
    }

    // --- Method Management (OVERLOADING SUPPORT) ---
    public void defineMethod(String name, String returnType, List<String> paramTypes) {
        // If the list doesn't exist for this name, create it
        methods.computeIfAbsent(name, k -> new java.util.ArrayList<>())
                .add(new MethodSignature(returnType, paramTypes));
    }

    // Returns ALL overloads for a given method name
    public List<MethodSignature> lookupMethods(String name) {
        return methods.get(name);
    }
}