package com.group3.compiler.backend.models;

/* ABSTRACT CLASS
Description: The base blueprint for every piece of code identified.
Member Note: "Do not instantiate this directly. All new token types must extend this.
    Contains common fields: lexeme, type, line, and column." */

public abstract class Tokens {
    protected String lexeme; // The actual text of the token
    protected String type;   // The category/type of the token (e.g., IDENTIFIER, KEYWORD)
    protected int line;      // Line number where the token was found
    protected int column;    // Column number where the token starts

    // Constructor
    public Tokens(String lexeme, String type, int line, int column) {
        this.lexeme = lexeme;
        this.type   = type;
        this.line   = line;
        this.column = column;
    }

    // Getters
    public String getLexeme() { return lexeme; }
    public String getType()   { return type; }
    public int    getLine()   { return line; }
    public int    getColumn() { return column; }

    @Override
    public String toString() {
        return String.format("[%s] '%s' | Line: %d, Col: %d", type, lexeme, line, column);
    }
}