package com.group3.compiler.backend.models;

/* SUBCLASS: SpecialCharacter
Description: Represents grouping symbols or special notations ( (, ), {, }, [, ] ).
Member Note: "These help define code blocks. Categorize them separately from 
    math operators for cleaner parsing later." */

public class SpecialChar extends Tokens {
    // Constructor
    public SpecialChar(String lexeme, int line, int column) {
        super(lexeme, "SPECIAL_CHAR", line, column);
    }
    
}
