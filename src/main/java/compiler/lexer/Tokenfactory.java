package compiler.lexer;

import compiler.lexer.models.Constant;
import compiler.lexer.models.Identifier;
import compiler.lexer.models.Keyword;
import compiler.lexer.models.Literal;
import compiler.lexer.models.Operator;
import compiler.lexer.models.Punctuation;
import compiler.lexer.models.SpecialChar;
import compiler.lexer.models.Tokens;

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
