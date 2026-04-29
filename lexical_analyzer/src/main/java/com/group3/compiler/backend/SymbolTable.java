package com.group3.compiler.backend;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

/* SINGLETON CLASS: Symbol Table Manager
Description: A centralized repository (HashMap) for identifiers and their occurrences.
Member Note: "Use this to store 'lexeme' as the key and 'count' as the value.
    Required for the GUI requirement of tracking how many times a variable appears." */

public class SymbolTable {
    private static final HashSet<String> KEYWORDS = new HashSet<>(Arrays.asList(
        // Based on JAVA 17 Keywords (https://docs.oracle.com/javase/tutorial/java/nutsandbolts/_keywords.html)
        // 1. ACCESS MODIFIERS
        "public", "private", "protected",
        // 2. CLASS / INTERFACE DECLARATIONS
        "class", "interface", "enum", "record", "extends", "implements", "package", "import",
        // 3. PROPERTY & METHOD MODIFIERS
        "static", "final", "abstract", "synchronized", "native", "strictfp", "transient", "volatile",
        // 4. DATA TYPES (Primitives)
        "boolean", "byte", "char", "short", "int", "long", "float", "double", "void",
        // 5. CONTROL FLOW & LOOPS
        "if", "else", "switch", "case", "default", "while", "do", "for", "break", "continue", "return",
        // 6. ERROR HANDLING
        "try", "catch", "finally", "throw", "throws", "assert",
        // 7. OBJECT & VARIABLE RELATED
        "new", "this", "super", "instanceof", "var", "yield", "when",
        // 8. MODULE RELATED (Contextual)
        "module", "exports", "opens", "provides", "requires", "to", "transitive", "uses", "with", "open",
        // 9. MODERN JAVA CONSTRUCTS
        "sealed", "non-sealed", "permits", "scoped",
        // 10. RESERVED BUT UNUSED (Legacy)
        "goto", "const", "_",
        // 11. LITERALS (Behave like keywords)
        "true", "false", "null"
    ));

    // Key = Lexeme (Identifier), Value = Count of Occurrences
    private final HashMap<String, Integer> identifierCounts;

    // Private Constructor
    private SymbolTable() {
        identifierCounts = new HashMap<>();
    }

    private static final SymbolTable instance = new SymbolTable();

    public static SymbolTable getInstance() {
        return instance;
    }

    public boolean isKeyword(String lexeme)    { return KEYWORDS.contains(lexeme); }

    public void registerIdentifier(String lexeme) {
        if (!isKeyword(lexeme)) {
            identifierCounts.put(lexeme, identifierCounts.getOrDefault(lexeme, 0) + 1);
        }
    }

    public int getIdentifierCount(String lexeme) {
        return identifierCounts.getOrDefault(lexeme, 0);
    }

    public void reset()                              { identifierCounts.clear(); }
    public HashMap<String, Integer> getAllIdentifiers() { return identifierCounts; }
}