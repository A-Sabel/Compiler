package com.group3.compiler.backend.models;

/* SUBCLASS: Literal
Description: Represents string or character data usually enclosed in quotes.
Member Note: "The scanner should treat everything inside double quotes as a 
    single Literal token, regardless of spaces." */

public class Literal extends Tokens {
    // Constructor
    public Literal(String lexeme, int line, int column) {
        super(lexeme, "LITERAL", line, column);
    }
    
}
