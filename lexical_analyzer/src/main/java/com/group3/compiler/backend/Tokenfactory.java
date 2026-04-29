package com.group3.compiler.backend;

import com.group3.compiler.backend.models.Constant;
import com.group3.compiler.backend.models.Identifier;
import com.group3.compiler.backend.models.Keyword;
import com.group3.compiler.backend.models.Literal;
import com.group3.compiler.backend.models.Operator;
import com.group3.compiler.backend.models.Punctuation;
import com.group3.compiler.backend.models.SpecialChar;
import com.group3.compiler.backend.models.Tokens;

/* DESIGN PATTERN: Factory
Description: Centralizes the creation of specific token subclasses.
Member Note: "Instead of calling 'new Keyword()', call 'TokenFactory.create()'. 
    This keeps the Lexer class clean and organized." */

public class Tokenfactory {
    public static Tokens createToken(String lexeme, String category, int line, int col) {
        switch (category.toUpperCase()) {
            case "KEYWORD" -> {
                return new Keyword(lexeme, line, col);
            }
            case "IDENTIFIER" -> {
                return new Identifier(lexeme, line, col);
            }
            case "CONSTANT" -> {
                return new Constant(lexeme, line, col); // Needed for processNumeric
            }
            case "LITERAL" -> {
                return new Literal(lexeme, line, col);
            }
            case "OPERATOR" -> {
                return new Operator(lexeme, line, col);
            }
            case "PUNCTUATION" -> {
                return new Punctuation(lexeme, line, col);
            }
            case "SPECIAL_CHAR" -> {
                return new SpecialChar(lexeme, line, col); // Needed for special symbols
            }
            default -> throw new IllegalArgumentException("Unknown category: " + category);
        }
    }
}
