    package com.group3.compiler.backend;

    import java.util.ArrayList;
    import java.util.List;

    import com.group3.compiler.backend.models.Tokens;
    import com.group3.compiler.utils.CharMatcher;
    import com.group3.compiler.utils.ErrorHandler;

    /* MAIN CLASS: Lexical Analyzer
    Description: The engine that scans source code character-by-character to generate tokens.
    Member Note: "This holds the 'pointer' (index) for the string. It calls the 
        TokenFactory and ErrorHandler based on what CharMatcher returns." */

    /* HELPER METHOD (private): Lookahead (Peek)
    Description: Allows the Lexer to see the next character in the stream without advancing the pointer.
    Member Note: "Essential for multi-character operators like '==' or '!='. Always verify 
        if (index + 1) < input.length() before peeking to avoid crashes." */

    /* HELPER METHOD (private): Comment & Whitespace Stripper
    Description: A pre-processor that skips over non-token elements (spaces, tabs, newlines, and comments).
    Member Note: "Call this at the start of every 'getNextToken()' cycle. It cleans the 
        path so the scanner only lands on valid, categorizable characters." */

    public class Lexer {
        private final String sourceCode;
        private int pos;
        private int line;
        private int col;
        private final List<Tokens> tokens;

        // Constructor
        public Lexer(String sourceCode) {
            this.sourceCode = sourceCode;
            this.pos = 0;
            this.line = 1;
            this.col = 1;
            this.tokens = new ArrayList<>();
        }

        public List<Tokens> tokenize() {
            ErrorHandler.clear();
            SymbolTable.getInstance().reset();
            while (pos < sourceCode.length()) {
                char current = sourceCode.charAt(pos);

                // Handle Comments
                if (current == '/' && peek() == '/') {
                    skipSingleLineComment();
                    continue;
                } 
                else if (current == '/' && peek() == '*') {
                    skipMultiLineComment();
                    continue;
                }

                // State S: Handle Whitespace & Newlines
                if(CharMatcher.isWhitespace(current)) {
                    handleWhitespace(current);
                    continue;
                }

                // DFA Branches for Token Categories
                if (CharMatcher.isAlpha(current)) { processAlpha(); } // State 1, 2, 3
                else if (CharMatcher.isDigit(current)) { processNumeric(); } // State 4, 6, 7
                else if (CharMatcher.isQuote(current)) { processLiteral(); } // State 8, 9
                else if (CharMatcher.isOperator(current)) { processOperator(); } // State 10, 11, 14
                else if (CharMatcher.isPunctuation(current)) { processPunctuation(); } // State 13
                else if (CharMatcher.isSpecialSymbol(current)) {
                    tokens.add(Tokenfactory.createToken(Character.toString(current), "SPECIAL_CHAR", line, col));
                    advance();
                }
                else {
                    // State Error: Unrecognized Character
                    ErrorHandler.report("Unrecognized character: '" + current + "'", line, col);
                    advance();
                }
            }
            return tokens;
        }

        private void handleWhitespace(char c) {
            if (c == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
            pos++;
        }

        // Handles: // This is a comment
        private void skipSingleLineComment() {
            while (pos < sourceCode.length() && sourceCode.charAt(pos) != '\n') {
                advance();
            }
        }

        // Handles: /* This is a multi-line comment */
        private void skipMultiLineComment() {
            advance(); // Consume the '/'
            advance(); // Consume the '*'
            while (pos < sourceCode.length()) {
                char current = sourceCode.charAt(pos);
                // Check for the closing "*/"
                if (current == '*' && peek() == '/') {
                    advance(); // Consume the '*'
                    advance(); // Consume the '/'
                    return;
                }
                // We must manually track newlines inside block comments
                if (current == '\n') {
                    line++;
                    col = 1;
                    pos++;
                } else {
                    advance();
                }
            }
            ErrorHandler.report("Unterminated multi-line comment", line, col);
        }

        private void advance() {
            pos++;
            col++;
        }

        private char peek() {
            if (pos + 1 >= sourceCode.length()) return '\0';
            return sourceCode.charAt(pos + 1);
        }

        private void processAlpha() {
            StringBuilder sb = new StringBuilder();
            int startCol = col;
            while (pos < sourceCode.length() && CharMatcher.isAlphaNumeric(sourceCode.charAt(pos))) {
                sb.append(sourceCode.charAt(pos));
                advance();
            }
            String lexeme = sb.toString();
            String category = SymbolTable.getInstance().isKeyword(lexeme) ? "KEYWORD" : "IDENTIFIER";
            tokens.add(Tokenfactory.createToken(lexeme, category, line, startCol));
            if (category.equals("IDENTIFIER")) {
                SymbolTable.getInstance().registerIdentifier(lexeme);
            }
        }

        private void processNumeric() {
            StringBuilder sb = new StringBuilder();
            int startCol = col;
            boolean hasDecimal = false;
            while (pos < sourceCode.length()) {
                char c = sourceCode.charAt(pos);
                if (CharMatcher.isDigit(c)) {
                    sb.append(c);
                    advance();
                } 
                // Only accept a dot IF we haven't seen one yet AND the next char is a digit
                else if (c == '.' && !hasDecimal && CharMatcher.isDigit(peek())) {
                    hasDecimal = true;
                    sb.append(c);
                    advance();
                } 
                // If it's a second dot, or a dot not followed by a digit, stop collecting.
                else {
                    break; 
                }
            }
            tokens.add(Tokenfactory.createToken(sb.toString(), "CONSTANT", line, startCol));
        }

        private void processOperator() {
            int startCol = col;
            char current = sourceCode.charAt(pos);
            char next = peek();
            String lexeme = Character.toString(current);
            // State 11:
            if (
                (current == '=' && next == '=') ||
                (current == '!' && next == '=') ||
                (current == '<' && (next == '=' || next == '<')) ||
                (current == '>' && (next == '=' || next == '>')) ||
                (current == '+' && (next == '+' || next == '=')) ||
                (current == '-' && (next == '-' || next == '=')) ||
                (current == '&' && next == '&') ||
                (current == '|' && next == '|') ||
                (current == '*' && next == '=') ||
                (current == '/' && next == '=') ||
                (current == ':' && next == ':') // Method Reference
            ) {
                lexeme += next; // Combine them (e.g., "+" + "+" = "++")
                advance();
            }
            advance();
            tokens.add(Tokenfactory.createToken(lexeme, "OPERATOR", line, startCol));
        }

        private void processPunctuation() {
            int startCol = col;
            char current = sourceCode.charAt(pos);
            // State 14: Check for VarArgs "..."
            if (current == '.') {
                // Safely check if the next TWO characters are also dots
                if (pos + 2 < sourceCode.length() && 
                    sourceCode.charAt(pos + 1) == '.' && 
                    sourceCode.charAt(pos + 2) == '.') {
                    advance(); // Consume 1st dot
                    advance(); // Consume 2nd dot
                    advance(); // Consume 3rd dot
                    tokens.add(Tokenfactory.createToken("...", "OPERATOR", line, startCol));
                    return;
                }
            }
            // State 13: Normal Punctuation ( ; , . )
            advance();
            tokens.add(Tokenfactory.createToken(Character.toString(current), "PUNCTUATION", line, startCol));
        }

        private void processLiteral() {
            int startCol = col;
            int startLine = line;
            char quoteType = sourceCode.charAt(pos); // Stores either " or '
            StringBuilder sb = new StringBuilder();
            sb.append(quoteType);
            advance();
            boolean isClosed = false;
            // Loop until we find the closing quote or hit the end of the file
            while (pos < sourceCode.length()) {
                char current = sourceCode.charAt(pos);
                // State 9: Escape Sequence Trigger
                if (current == '\\') {
                    sb.append(current);
                    advance();
                    if (pos < sourceCode.length()) {
                        sb.append(sourceCode.charAt(pos));
                        advance();
                    }
                    continue;
                }
                // State 8: Normal String Character
                sb.append(current);
                advance();
                if (current == quoteType) {
                    isClosed = true;
                    break;
                }
                if (current == '\n') {
                    break; 
                }
            }
            // Error Handling for Unterminated Strings
            if (!isClosed) {
                ErrorHandler.report("Unterminated string or character literal", startLine, startCol);
            }
            tokens.add(Tokenfactory.createToken(sb.toString(), "LITERAL", startLine, startCol));
        }
    }
