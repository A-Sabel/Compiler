package com.group3.compiler.backend.models;

/* SUBCLASS: Constant
Description: Represents fixed numeric values (integers or decimals).
Member Note: "Ensure the scanner continues reading digits until it hits a 
    non-numeric character or a delimiter." */

public class Constant extends Tokens {
    // Constructor
    public Constant(String lexeme, int line, int column) {
        super(lexeme, "CONSTANT", line, column);
    }
    
}
