package compiler.lexer;

import java.util.ArrayList;
import java.util.List;

import compiler.lexer.models.Tokens;
import compiler.util.CharMatcher;
import compiler.util.ErrorHandler;

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

        // Peek at character at offset (pos + offset)
        private char peek(int offset) {
            int target = pos + offset;
            if (target >= sourceCode.length()) return '\0';
            return sourceCode.charAt(target);
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
            
            // Check for hex (0x), octal (0), or binary (0b) prefix
            if (sourceCode.charAt(pos) == '0') {
                sb.append('0');
                advance();
                
                if (pos < sourceCode.length()) {
                    char prefix = sourceCode.charAt(pos);
                    
                    // Hexadecimal (0x or 0X)
                    if (prefix == 'x' || prefix == 'X') {
                        sb.append(prefix);
                        advance();
                        boolean hexHasDigits = false;
                        while (pos < sourceCode.length() && isHexDigit(sourceCode.charAt(pos))) {
                            sb.append(sourceCode.charAt(pos));
                            advance();
                            hexHasDigits = true;
                        }
                        if (!hexHasDigits) {
                            ErrorHandler.report("Invalid hex literal", line, startCol);
                        }
                        tokens.add(Tokenfactory.createToken(sb.toString(), "CONSTANT", line, startCol));
                        return;
                    }
                    
                    // Binary (0b or 0B)
                    if (prefix == 'b' || prefix == 'B') {
                        sb.append(prefix);
                        advance();
                        boolean binHasDigits = false;
                        while (pos < sourceCode.length() && (sourceCode.charAt(pos) == '0' || sourceCode.charAt(pos) == '1')) {
                            sb.append(sourceCode.charAt(pos));
                            advance();
                            binHasDigits = true;
                        }
                        if (!binHasDigits) {
                            ErrorHandler.report("Invalid binary literal: no digits after 0b", line, startCol);
                        }
                        tokens.add(Tokenfactory.createToken(sb.toString(), "CONSTANT", line, startCol));
                        return;
                    }
                    
                    // Octal (0-7 prefix, but NOT 0. for float)
                    if (CharMatcher.isDigit(prefix) && prefix != '8' && prefix != '9') {
                        while (pos < sourceCode.length()) {
                            char c = sourceCode.charAt(pos);
                            if (c >= '0' && c <= '7') {
                                sb.append(c);
                                advance();
                            } else if (c == '8' || c == '9') {
                                ErrorHandler.report("Invalid octal literal: digit " + c + " is not allowed in octal", line, startCol);
                                advance();
                            } else {
                                break;
                            }
                        }
                        tokens.add(Tokenfactory.createToken(sb.toString(), "CONSTANT", line, startCol));
                        return;
                    }
                    // Otherwise continue to decimal/float processing (handles 0.5, 0e10, etc.)
                }
            }
            
            // Standard decimal number processing
            boolean hasDecimal = false;
            boolean hasExponent = false;
            
            // Consume rest of integer part
            while (pos < sourceCode.length()) {
                char c = sourceCode.charAt(pos);
                if (CharMatcher.isDigit(c)) {
                    sb.append(c);
                    advance();
                } 
                // Decimal point
                else if (c == '.' && !hasDecimal && !hasExponent && CharMatcher.isDigit(peek())) {
                    hasDecimal = true;
                    sb.append(c);
                    advance();
                } 
                // Exponent notation (e or E)
                else if ((c == 'e' || c == 'E') && !hasExponent && sb.length() > 0) {
                    hasExponent = true;
                    sb.append(c);
                    advance();
                    
                    // Optional sign after exponent
                    char next = peek();
                    if (next == '+' || next == '-') {
                        sb.append(next);
                        advance();
                    }
                    
                    // At least one digit must follow exponent
                    if (pos >= sourceCode.length() || !CharMatcher.isDigit(sourceCode.charAt(pos))) {
                        ErrorHandler.report("Invalid number format: exponent requires at least one digit", line, startCol);
                        break;
                    }
                } 
                else {
                    break; 
                }
            }
            
            String numberStr = sb.toString();

            // Rule E.3: Integer Overflow Prevention
            // Only test bounds if it's a raw integer (no decimal or scientific exponent)
            if (!hasDecimal && !hasExponent) {
                try {
                    long value = Long.parseLong(numberStr);
                    if (value > Integer.MAX_VALUE) { 
                        ErrorHandler.report("Lexical Error: Integer Number Too Large", line, startCol);
                    }
                } catch (NumberFormatException e) {
                    ErrorHandler.report("Lexical Error: Integer Number Too Large", line, startCol);
                }
            }

            tokens.add(Tokenfactory.createToken(numberStr, "CONSTANT", line, startCol));
        }
        
        private boolean isHexDigit(char c) {
            return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
        }

        private void processOperator() {
            int startCol = col;
            char current = sourceCode.charAt(pos);
            char next = peek();
            char next2 = peek(2);
            String lexeme = Character.toString(current);
            
            // Check for 3-character operators first
            String threeChar = "" + current + next + next2;
            if (threeChar.equals(">>>") || threeChar.equals(">>=") || threeChar.equals("<<=") || threeChar.equals(">>>")) {
                lexeme = threeChar;
                advance();
                advance();
                advance();
            }
            // Check for 2-character operators
            else if (
                (current == '=' && next == '=') ||
                (current == '!' && next == '=') ||
                (current == '<' && (next == '=' || next == '<')) ||
                (current == '>' && (next == '=' || next == '>')) ||
                (current == '+' && (next == '+' || next == '=')) ||
                (current == '-' && (next == '-' || next == '=')) ||
                (current == '&' && (next == '&' || next == '=')) ||
                (current == '|' && (next == '|' || next == '=')) ||
                (current == '^' && next == '=') ||
                (current == '%' && next == '=') ||
                (current == '*' && next == '=') ||
                (current == '/' && next == '=') ||
                (current == ':' && next == ':') // Method Reference
            ) {
                lexeme += next;
                advance();
                advance();
            }
            // Single character operators (including ~, %, ^, &, |)
            else {
                advance();
            }
            
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
            String literalType = quoteType == '"' ? "STRING" : "CHAR";
            StringBuilder sb = new StringBuilder();
            sb.append(quoteType);
            advance();
            boolean isClosed = false;
            
            // Loop until we find the closing quote or hit the end of the file
            while (pos < sourceCode.length()) {
                char current = sourceCode.charAt(pos);
                
                // Newline in literal is an error (Java doesn't allow multiline strings without escape)
                if (current == '\n') {
                    ErrorHandler.report("Unterminated " + literalType.toLowerCase() + " literal (newline encountered)", startLine, startCol);
                    line++;
                    col = 1;
                    pos++;
                    return; // Don't add token
                }
                
                // State 9: Escape Sequence Trigger
                if (current == '\\') {
                    sb.append(current);
                    advance();
                    if (pos < sourceCode.length()) {
                        char escaped = sourceCode.charAt(pos);
                        
                        // Validate escape sequence
                        if (!isValidEscapeSequence(escaped)) {
                            ErrorHandler.report("Invalid escape sequence: \\" + escaped, line, col);
                        }
                        
                        sb.append(escaped);
                        if (escaped == '\n') {
                            line++;
                            col = 1;
                        } else {
                            col++;
                        }
                        pos++;
                    } else {
                        ErrorHandler.report("Incomplete escape sequence at end of input", line, col);
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
            }
            
            // Error Handling for Unterminated Strings (reached EOF)
            if (!isClosed) {
                ErrorHandler.report("Unterminated " + literalType.toLowerCase() + " literal (reached end of file)", startLine, startCol);
                return; // Don't add malformed token
            }
            
            // Only add token if properly closed
            tokens.add(Tokenfactory.createToken(sb.toString(), "LITERAL", startLine, startCol));
        }

        private boolean isValidEscapeSequence(char escaped) {
            // Java valid escape sequences
            switch (escaped) {
                case 'b':  // Backspace
                case 't':  // Tab
                case 'n':  // Newline
                case 'f':  // Form feed
                case 'r':  // Carriage return
                case '\"': // Double quote
                case '\'': // Single quote
                case '\\': // Backslash
                    return true;
                default:
                    // Octal escape: \0-\7 (single digit), \00-\77 (two digits), \000-\377 (three digits)
                    if (escaped >= '0' && escaped <= '7') {
                        return true;
                    }
                    return false;
            }
        }
    }
