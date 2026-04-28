package com.group3.compiler.backend.models;

/* SUBCLASS: Identifier
Description: Represents user-defined names for variables, classes, or methods.
Member Note: "Must start with a letter or underscore. Ensure this is cross-referenced 
    with the SymbolTable to update occurrence counts." */

public class Identifier extends Tokens {
    // Constructor
    public Identifier(String lexeme, int line, int column) {
        super(lexeme, "IDENTIFIER", line, column);
    }
    
}
