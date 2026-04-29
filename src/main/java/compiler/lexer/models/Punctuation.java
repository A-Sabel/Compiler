package compiler.lexer.models;

/* SUBCLASS: Punctuation
Description: Represents structural code separators like semicolons (;) or commas (,).
Member Note: "These act as immediate delimiters that usually signal the end 
    of a previous lexeme." */

public class Punctuation extends Tokens {
    // Constructor
    public Punctuation(String lexeme, int line, int column) {
        super(lexeme, "PUNCTUATION", line, column);
    }
    
}
