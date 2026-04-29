package compiler.parser;

import java.util.List;

import compiler.lexer.models.Tokens;
import compiler.parser.ast.ASTNode;
import compiler.util.ErrorHandler;

/**
 * MAIN CLASS: Recursive Descent Parser
 *
 * Takes the token list produced by Lexer.tokenize() and builds an AST.
 *
 * Grammar handled (subset of Java):
 *
 *   program        → statement*
 *   statement      → ifStmt
 *                  | whileStmt
 *                  | forStmt
 *                  | block
 *                  | varDecl        (type identifier [= expr] ;)
 *                  | exprStmt       (expr ;)
 *                  | returnStmt     (return [expr] ;)
 *                  | printStmt      (System.out.println(expr) ;)
 *
 *   ifStmt         → if ( expr ) block [ else block ]
 *   whileStmt      → while ( expr ) block
 *   forStmt        → for ( forInit ; expr ; exprStmt ) block
 *   block          → { statement* }
 *
 *   expr           → assignment
 *   assignment     → identifier (= | += | -= | *= | /=) assignment | logicalOr
 *   logicalOr      → logicalAnd ( || logicalAnd )*
 *   logicalAnd     → equality  ( && equality  )*
 *   equality       → relational ( (== | !=) relational )*
 *   relational     → additive  ( (< | > | <= | >=) additive )*
 *   additive       → multiplicative ( (+ | -) multiplicative )*
 *   multiplicative → unary ( (* | /) unary )*
 *   unary          → (! | - | ++ | --) unary | postfix
 *   postfix        → primary (++ | --)?
 *   primary        → NUMBER | STRING | IDENTIFIER | ( expr )
 */
public class Parser {

    private final List<Tokens> tokens;
    private int pos;

    // ── Constructor ────────────────────────────────────────────────────────────
    public Parser(List<Tokens> tokens) {
        this.tokens = tokens;
        this.pos    = 0;
    }

    // ── Public Entry Point ─────────────────────────────────────────────────────
    /**
     * Parses the full token stream and returns the root PROGRAM node.
     * Parse errors are sent to ErrorHandler (same as Lexer errors).
     */
    public ASTNode parse() {
        ASTNode program = ASTNode.of("PROGRAM");
        while (!isAtEnd()) {
            ASTNode stmt = parseStatement();
            if (stmt != null) program.addChild(stmt);
        }
        return program;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  STATEMENT PARSING
    // ═════════════════════════════════════════════════════════════════════════

    private ASTNode parseStatement() {
        // Skip stray semicolons
        if (check("PUNCTUATION", ";")) { advance(); return null; }

        // Block
        if (check("SPECIAL_CHAR", "{")) return parseBlock();

        // if
        if (check("KEYWORD", "if")) return parseIf();

        // while
        if (check("KEYWORD", "while")) return parseWhile();

        // do-while
        if (check("KEYWORD", "do")) return parseDoWhile();

        // for
        if (check("KEYWORD", "for")) return parseFor();

        // break
        if (check("KEYWORD", "break")) return parseBreak();

        // continue
        if (check("KEYWORD", "continue")) return parseContinue();

        // return
        if (check("KEYWORD", "return")) return parseReturn();

        // switch
        if (check("KEYWORD", "switch")) return parseSwitch();

        // System.out.println / System.out.print
        if (isPrintStatement()) return parsePrint();

        // Variable declaration: type identifier ...
        if (isTypeKeyword() && peekIsIdentifier()) return parseVarDecl();

        // Expression statement
        ASTNode expr = parseExpression();
        consumePunctuation(";");
        ASTNode stmt = ASTNode.of("EXPR_STMT");
        stmt.addChild(expr);
        return stmt;
    }

    // ── Block ──────────────────────────────────────────────────────────────────
    private ASTNode parseBlock() {
        consume("SPECIAL_CHAR", "{");
        ASTNode block = ASTNode.of("BLOCK");
        while (!isAtEnd() && !check("SPECIAL_CHAR", "}")) {
            ASTNode stmt = parseStatement();
            if (stmt != null) block.addChild(stmt);
        }
        consume("SPECIAL_CHAR", "}");
        return block;
    }

    // ── if [ else ] ────────────────────────────────────────────────────────────
    private ASTNode parseIf() {
        consume("KEYWORD", "if");
        consume("SPECIAL_CHAR", "(");
        ASTNode condition = parseExpression();
        consume("SPECIAL_CHAR", ")");
        ASTNode thenBranch = parseBlock();

        ASTNode ifNode = ASTNode.of("IF");
        ifNode.addChild(wrapAs("CONDITION", condition));
        ifNode.addChild(wrapAs("THEN", thenBranch));

        if (check("KEYWORD", "else")) {
            advance();
            ASTNode elseBranch = check("KEYWORD", "if") ? parseIf() : parseBlock();
            ifNode.addChild(wrapAs("ELSE", elseBranch));
        }
        return ifNode;
    }

    // ── while ──────────────────────────────────────────────────────────────────
    private ASTNode parseWhile() {
        consume("KEYWORD", "while");
        consume("SPECIAL_CHAR", "(");
        ASTNode condition = parseExpression();
        consume("SPECIAL_CHAR", ")");
        ASTNode body = parseBlock();

        ASTNode whileNode = ASTNode.of("WHILE");
        whileNode.addChild(wrapAs("CONDITION", condition));
        whileNode.addChild(wrapAs("BODY", body));
        return whileNode;
    }

    // ── do-while ───────────────────────────────────────────────────────────────
    private ASTNode parseDoWhile() {
        consume("KEYWORD", "do");
        ASTNode body = parseBlock();
        consume("KEYWORD", "while");
        consume("SPECIAL_CHAR", "(");
        ASTNode condition = parseExpression();
        consume("SPECIAL_CHAR", ")");
        consumePunctuation(";");

        ASTNode doWhileNode = ASTNode.of("DO_WHILE");
        doWhileNode.addChild(wrapAs("BODY", body));
        doWhileNode.addChild(wrapAs("CONDITION", condition));
        return doWhileNode;
    }

    // ── for ───────────────────────────────────────────────────────────────────
    private ASTNode parseFor() {
        consume("KEYWORD", "for");
        consume("SPECIAL_CHAR", "(");

        ASTNode forNode = ASTNode.of("FOR");

        // Init: could be a var decl or expression
        ASTNode init;
        if (isTypeKeyword() && peekIsIdentifier()) {
            init = parseVarDeclNoSemiCheck(); // handles its own ;
        } else {
            init = parseExpression();
            consumePunctuation(";");
        }
        forNode.addChild(wrapAs("INIT", init));

        // Condition
        ASTNode condition = parseExpression();
        consumePunctuation(";");
        forNode.addChild(wrapAs("CONDITION", condition));

        // Update (expression only, no semicolon before ')')
        ASTNode update = parseExpression();
        forNode.addChild(wrapAs("UPDATE", update));

        consume("SPECIAL_CHAR", ")");
        ASTNode body = parseBlock();
        forNode.addChild(wrapAs("BODY", body));
        return forNode;
    }

    // ── return ────────────────────────────────────────────────────────────────
    private ASTNode parseReturn() {
        consume("KEYWORD", "return");
        ASTNode returnNode = ASTNode.of("RETURN");
        if (!check("PUNCTUATION", ";")) {
            returnNode.addChild(parseExpression());
        }
        consumePunctuation(";");
        return returnNode;
    }

    // ── break ──────────────────────────────────────────────────────────────────
    private ASTNode parseBreak() {
        consume("KEYWORD", "break");
        consumePunctuation(";");
        return ASTNode.of("BREAK");
    }

    // ── continue ───────────────────────────────────────────────────────────────
    private ASTNode parseContinue() {
        consume("KEYWORD", "continue");
        consumePunctuation(";");
        return ASTNode.of("CONTINUE");
    }

    // ── switch ─────────────────────────────────────────────────────────────────
    private ASTNode parseSwitch() {
        consume("KEYWORD", "switch");
        consume("SPECIAL_CHAR", "(");
        ASTNode expr = parseExpression();
        consume("SPECIAL_CHAR", ")");
        consume("SPECIAL_CHAR", "{");

        ASTNode switchNode = ASTNode.of("SWITCH");
        switchNode.addChild(wrapAs("EXPR", expr));
        
        ASTNode casesWrapper = ASTNode.of("CASES");
        
        while (!isAtEnd() && !check("SPECIAL_CHAR", "}")) {
            if (check("KEYWORD", "case")) {
                advance(); // consume 'case'
                ASTNode caseExpr = parseExpression();
                consume("PUNCTUATION", ":");
                
                ASTNode caseNode = ASTNode.of("CASE");
                caseNode.addChild(caseExpr);
                
                // Parse statements until next case/default/closing brace
                ASTNode caseBody = ASTNode.of("CASE_BODY");
                while (!isAtEnd() && !check("SPECIAL_CHAR", "}") && 
                       !check("KEYWORD", "case") && !check("KEYWORD", "default")) {
                    ASTNode stmt = parseStatement();
                    if (stmt != null) caseBody.addChild(stmt);
                }
                caseNode.addChild(caseBody);
                casesWrapper.addChild(caseNode);
            } 
            else if (check("KEYWORD", "default")) {
                advance(); // consume 'default'
                consume("PUNCTUATION", ":");
                
                ASTNode defaultNode = ASTNode.of("DEFAULT");
                ASTNode defaultBody = ASTNode.of("CASE_BODY");
                
                while (!isAtEnd() && !check("SPECIAL_CHAR", "}")) {
                    ASTNode stmt = parseStatement();
                    if (stmt != null) defaultBody.addChild(stmt);
                }
                defaultNode.addChild(defaultBody);
                casesWrapper.addChild(defaultNode);
            }
            else {
                // Skip unexpected tokens in switch
                advance();
            }
        }
        
        consume("SPECIAL_CHAR", "}");
        switchNode.addChild(casesWrapper);
        return switchNode;
    }

    // ── System.out.println(expr); ─────────────────────────────────────────────
    private ASTNode parsePrint() {
        // Consume: System . out . println / print
        advance(); // System
        consume("PUNCTUATION", ".");
        advance(); // out
        consume("PUNCTUATION", ".");
        String method = currentLexeme(); advance(); // println or print
        consume("SPECIAL_CHAR", "(");
        ASTNode arg = parseExpression();
        consume("SPECIAL_CHAR", ")");
        consumePunctuation(";");

        ASTNode printNode = ASTNode.of("PRINT_STMT", method);
        printNode.addChild(arg);
        return printNode;
    }

    // ── Variable declaration ───────────────────────────────────────────────────
    private ASTNode parseVarDecl() {
        ASTNode node = parseVarDeclNoSemiCheck();
        consumePunctuation(";");
        return node;
    }

    private ASTNode parseVarDeclNoSemiCheck() {
        String typeName = currentLexeme(); advance(); // consume type keyword
        String varName  = currentLexeme(); advance(); // consume identifier

        ASTNode declNode = ASTNode.of("VAR_DECL", typeName + " " + varName);
        declNode.addChild(ASTNode.of("TYPE", typeName));
        declNode.addChild(ASTNode.of("NAME", varName));

        if (check("OPERATOR", "=")) {
            advance(); // consume '='
            ASTNode init = parseExpression();
            declNode.addChild(wrapAs("INIT_VALUE", init));
        }
        return declNode;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  EXPRESSION PARSING  (Pratt / recursive-descent by precedence)
    // ═════════════════════════════════════════════════════════════════════════

    private ASTNode parseExpression() {
        return parseTernary();
    }

    private ASTNode parseTernary() {
        ASTNode condition = parseAssignment();
        
        if (check("SPECIAL_CHAR", "?")) {
            advance(); // consume '?'
            ASTNode thenExpr = parseExpression();
            consume("SPECIAL_CHAR", ":");
            ASTNode elseExpr = parseExpression();
            
            ASTNode ternary = ASTNode.of("TERNARY", "?");
            ternary.addChild(wrapAs("CONDITION", condition));
            ternary.addChild(wrapAs("THEN", thenExpr));
            ternary.addChild(wrapAs("ELSE", elseExpr));
            return ternary;
        }
        return condition;
    }

    private ASTNode parseAssignment() {
        ASTNode left = parseLogicalOr();

        if (currentType() != null && currentType().equals("OPERATOR")
            && isAssignmentOp(currentLexeme())) {
            String op = currentLexeme(); advance();
            ASTNode right = parseAssignment();
            ASTNode assign = ASTNode.of("ASSIGN", op);
            assign.addChild(left);
            assign.addChild(right);
            return assign;
        }
        return left;
    }

    private ASTNode parseLogicalOr() {
        ASTNode left = parseLogicalAnd();
        while (check("OPERATOR", "||")) {
            String op = currentLexeme(); advance();
            ASTNode right = parseLogicalAnd();
            ASTNode node = ASTNode.of("BINARY_OP", op);
            node.addChild(left); node.addChild(right);
            left = node;
        }
        return left;
    }

    private ASTNode parseLogicalAnd() {
        ASTNode left = parseEquality();
        while (check("OPERATOR", "&&")) {
            String op = currentLexeme(); advance();
            ASTNode right = parseEquality();
            ASTNode node = ASTNode.of("BINARY_OP", op);
            node.addChild(left); node.addChild(right);
            left = node;
        }
        return left;
    }

    private ASTNode parseEquality() {
        ASTNode left = parseRelational();
        while (currentType() != null && currentType().equals("OPERATOR")
               && (currentLexeme().equals("==") || currentLexeme().equals("!="))) {
            String op = currentLexeme(); advance();
            ASTNode right = parseRelational();
            ASTNode node = ASTNode.of("BINARY_OP", op);
            node.addChild(left); node.addChild(right);
            left = node;
        }
        return left;
    }

    private ASTNode parseRelational() {
        ASTNode left = parseAdditive();
        while (currentType() != null && currentType().equals("OPERATOR")
               && isRelationalOp(currentLexeme())) {
            String op = currentLexeme(); advance();
            ASTNode right = parseAdditive();
            ASTNode node = ASTNode.of("BINARY_OP", op);
            node.addChild(left); node.addChild(right);
            left = node;
        }
        return left;
    }

    private ASTNode parseAdditive() {
        ASTNode left = parseMultiplicative();
        while (currentType() != null && currentType().equals("OPERATOR")
               && (currentLexeme().equals("+") || currentLexeme().equals("-"))) {
            String op = currentLexeme(); advance();
            ASTNode right = parseMultiplicative();
            ASTNode node = ASTNode.of("BINARY_OP", op);
            node.addChild(left); node.addChild(right);
            left = node;
        }
        return left;
    }

    private ASTNode parseMultiplicative() {
        ASTNode left = parseUnary();
        while (currentType() != null && currentType().equals("OPERATOR")
               && (currentLexeme().equals("*") || currentLexeme().equals("/"))) {
            String op = currentLexeme(); advance();
            ASTNode right = parseUnary();
            ASTNode node = ASTNode.of("BINARY_OP", op);
            node.addChild(left); node.addChild(right);
            left = node;
        }
        return left;
    }

    private ASTNode parseUnary() {
        // Type cast: (Type) expr
        if (check("SPECIAL_CHAR", "(")) {
            int savePos = pos;
            advance(); // consume '('
            
            // Try to parse as type
            if (isTypeKeyword()) {
                String type = currentLexeme();
                advance();
                if (check("SPECIAL_CHAR", ")")) {
                    advance(); // consume ')'
                    ASTNode castExpr = parseUnary();
                    ASTNode castNode = ASTNode.of("CAST", type);
                    castNode.addChild(castExpr);
                    return castNode;
                }
            }
            
            // Not a cast, restore position and parse as normal expression
            pos = savePos;
        }
        
        // new operator: new ClassName(...)
        if (check("KEYWORD", "new")) {
            advance(); // consume 'new'
            String className = currentLexeme();
            advance(); // consume class name (must be identifier)
            
            ASTNode newNode = ASTNode.of("NEW", className);
            
            // Optional constructor arguments
            if (check("SPECIAL_CHAR", "(")) {
                advance();
                ASTNode args = ASTNode.of("ARGS");
                while (!isAtEnd() && !check("SPECIAL_CHAR", ")")) {
                    args.addChild(parseExpression());
                    if (check("PUNCTUATION", ",")) advance();
                }
                consume("SPECIAL_CHAR", ")");
                newNode.addChild(args);
            }
            
            return newNode;
        }
        
        // Unary operators: !, -, ++, --
        if (currentType() != null && currentType().equals("OPERATOR")
            && (currentLexeme().equals("!") || currentLexeme().equals("-")
                || currentLexeme().equals("++") || currentLexeme().equals("--")
                || currentLexeme().equals("~"))) {
            String op = currentLexeme(); advance();
            ASTNode node = ASTNode.of("UNARY_OP", op);
            node.addChild(parseUnary());
            return node;
        }
        return parsePostfix();
    }

    
    private ASTNode parsePostfix() {
        ASTNode node = parsePrimary();
        
        // Handle postfix operators and access operations (loops to handle chaining: arr[i][j], obj.method().arr[i], etc.)
        while (true) {
            // Postfix ++ and --
            if (currentType() != null && currentType().equals("OPERATOR")
                && (currentLexeme().equals("++") || currentLexeme().equals("--"))) {
                String op = currentLexeme(); advance();
                ASTNode postfix = ASTNode.of("POSTFIX_OP", op);
                postfix.addChild(node);
                node = postfix;
            }
            // Array access: [expr]
            else if (check("SPECIAL_CHAR", "[")) {
                advance(); // consume '['
                ASTNode index = parseExpression();
                consume("SPECIAL_CHAR", "]");
                ASTNode access = ASTNode.of("ARRAY_ACCESS");
                access.addChild(node);
                access.addChild(index);
                node = access;
            }
            // Member access: .member or .method()
            else if (check("PUNCTUATION", ".")) {
                advance(); // consume '.'
                String member = currentLexeme();
                advance(); // consume member name
                
                // Check if it's a method call
                if (check("SPECIAL_CHAR", "(")) {
                    advance(); // consume '('
                    ASTNode methodCall = ASTNode.of("METHOD_CALL", member);
                    methodCall.addChild(node); // Add the object/instance
                    
                    ASTNode args = ASTNode.of("ARGS");
                    while (!isAtEnd() && !check("SPECIAL_CHAR", ")")) {
                        args.addChild(parseExpression());
                        if (check("PUNCTUATION", ",")) advance();
                    }
                    consume("SPECIAL_CHAR", ")");
                    methodCall.addChild(args);
                    node = methodCall;
                } else {
                    // Member field access
                    ASTNode fieldAccess = ASTNode.of("FIELD_ACCESS", member);
                    fieldAccess.addChild(node);
                    node = fieldAccess;
                }
            }
            else {
                break;
            }
        }
        
        return node;
    }

    private ASTNode parsePrimary() {
        if (isAtEnd()) {
            ErrorHandler.report("Unexpected end of input in expression", -1, -1);
            return ASTNode.of("ERROR");
        }

        // Grouped expression: ( expr )
        if (check("SPECIAL_CHAR", "(")) {
            advance();
            ASTNode inner = parseExpression();
            consume("SPECIAL_CHAR", ")");
            return inner;
        }

        // Number constant
        if (currentType() != null && currentType().equals("CONSTANT")) {
            String val = currentLexeme(); advance();
            return ASTNode.of("NUMBER", val);
        }

        // String / char literal
        if (currentType() != null && currentType().equals("LITERAL")) {
            String val = currentLexeme(); advance();
            return ASTNode.of("LITERAL", val);
        }

        // true / false / null
        if (currentType() != null && currentType().equals("KEYWORD")
            && (currentLexeme().equals("true") || currentLexeme().equals("false")
                || currentLexeme().equals("null"))) {
            String val = currentLexeme(); advance();
            return ASTNode.of("LITERAL", val);
        }

        // Identifier (possibly followed by method call)
        if (currentType() != null && currentType().equals("IDENTIFIER")) {
            String name = currentLexeme(); advance();

            // Method call: name(args)
            if (check("SPECIAL_CHAR", "(")) {
                advance();
                ASTNode call = ASTNode.of("METHOD_CALL", name);
                while (!isAtEnd() && !check("SPECIAL_CHAR", ")")) {
                    call.addChild(parseExpression());
                    if (check("PUNCTUATION", ",")) advance();
                }
                consume("SPECIAL_CHAR", ")");
                return call;
            }

            return ASTNode.of("IDENTIFIER", name);
        }

        // Fallback — skip unknown token to avoid infinite loop
        String bad = currentLexeme();
        ErrorHandler.report("Unexpected token in expression: " + bad,
            tokens.get(pos).getLine(), tokens.get(pos).getColumn());
        advance();
        return ASTNode.of("ERROR", bad);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  HELPER / UTILITY METHODS
    // ═════════════════════════════════════════════════════════════════════════

    private boolean isAtEnd() { return pos >= tokens.size(); }

    private Tokens current()  { return isAtEnd() ? null : tokens.get(pos); }
    private Tokens peek(int offset) {
        int idx = pos + offset;
        return (idx >= tokens.size()) ? null : tokens.get(idx);
    }

    private String currentType() {
        Tokens t = current();
        return t == null ? null : t.getClass().getSimpleName().toUpperCase();
    }

    private String currentLexeme() {
        Tokens t = current();
        return t == null ? "" : t.getLexeme();
    }

    private void advance() { if (!isAtEnd()) pos++; }

    private boolean check(String type, String lexeme) {
        return type.equals(currentType()) && lexeme.equals(currentLexeme());
    }

    private void consume(String type, String lexeme) {
        if (check(type, lexeme)) { advance(); return; }
        Tokens t = current();
        int line = t != null ? t.getLine() : -1;
        int col  = t != null ? t.getColumn() : -1;
        ErrorHandler.report(
            "Expected '" + lexeme + "' but found '" + currentLexeme() + "'", line, col);
        
        // Error recovery: skip to next safe token
        recover();
    }
    
    /**
     * Panic mode recovery: skip tokens until reaching a synchronization point.
     * Safe synchronization points: semicolon, closing brace, closing paren.
     */
    private void recover() {
        while (!isAtEnd()) {
            // Synchronization points where parsing can resume
            if (check("PUNCTUATION", ";")) {
                advance();
                return;
            }
            if (check("SPECIAL_CHAR", "}") || check("SPECIAL_CHAR", ")")) {
                return; // Don't consume; let parent context handle it
            }
            if (check("SPECIAL_CHAR", "{")) {
                return; // Let parent handle block
            }
            
            // Skip this token and continue
            advance();
        }
    }

    private void consumePunctuation(String lexeme) {
        consume("PUNCTUATION", lexeme);
    }

    private boolean isAssignmentOp(String op) {
        return op.equals("=") || op.equals("+=") || op.equals("-=")
            || op.equals("*=") || op.equals("/=");
    }

    private boolean isRelationalOp(String op) {
        return op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">=");
    }

    private boolean isTypeKeyword() {
        if (!"KEYWORD".equals(currentType())) return false;
        String lex = currentLexeme();
        return lex.equals("int") || lex.equals("double") || lex.equals("float")
            || lex.equals("boolean") || lex.equals("String") || lex.equals("char")
            || lex.equals("long") || lex.equals("byte") || lex.equals("short")
            || lex.equals("var");
    }

    private boolean peekIsIdentifier() {
        Tokens next = peek(1);
        return next != null && next.getClass().getSimpleName().equalsIgnoreCase("IDENTIFIER");
    }

    private boolean isPrintStatement() {
        // Heuristic: IDENTIFIER "System" followed by "." "out" "." "println"/"print"
        Tokens t0 = peek(0), t1 = peek(1), t2 = peek(2), t3 = peek(3), t4 = peek(4);
        if (t0 == null || !t0.getLexeme().equals("System")) return false;
        if (t1 == null || !t1.getLexeme().equals("."))      return false;
        if (t2 == null || !t2.getLexeme().equals("out"))    return false;
        if (t3 == null || !t3.getLexeme().equals("."))      return false;
        if (t4 == null) return false;
        return t4.getLexeme().equals("println") || t4.getLexeme().equals("print");
    }

    /** Wraps an existing node inside a labeled wrapper (e.g. CONDITION, THEN, BODY). */
    private ASTNode wrapAs(String label, ASTNode child) {
        ASTNode wrapper = ASTNode.of(label);
        wrapper.addChild(child);
        return wrapper;
    }
}
