package com.group3.compiler.backend.models;

/* SUBCLASS: Keyword
Description: Represents reserved words inherent to the language (e.g., int, if, while).
Member Note: "Compare lexemes against the predefined keyword array in SymbolTable 
    before assigning this type." */

public class Keyword extends Tokens {
    // Constructor
    public Keyword(String lexeme, int line, int column) {
        super(lexeme, "KEYWORD", line, column);
    }
    
}
