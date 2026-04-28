package com.group3.compiler.backend.models;

/* SUBCLASS: Operator
Description: Represents mathematical or logical symbols (+, -, *, /, ==, !=).
Member Note: "Be careful with multi-character operators! Use a 'peek' function 
    in the Lexer to see if a single '=' is actually an '=='." */

public class Operator extends Tokens {
    // Constructor
    public Operator(String lexeme, int line, int column) {
        super(lexeme, "OPERATOR", line, column);
    }
    
}
