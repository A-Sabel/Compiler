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
            // 1. Check for Class Declaration (e.g., public class Main)
            if (check("KEYWORD", "class") || (isModifier() && peekIsClass())) {
                program.addChild(parseClassDecl());
            } 
            // 2. Check for Method Declaration (using our new helper)
            else if (isMethodStart()) {
                program.addChild(parseMethodDecl());
            } 
            // 3. Fallback to standalone statements (like print or assignments)
            else {
                ASTNode stmt = parseStatement();
                if (stmt != null) program.addChild(stmt);
            }
        }
        return program;
    }

    // ── Method Declaration ─────────────────────────────────────────────────────
    private ASTNode parseMethodDecl() {
        // 1. Parse Modifiers (Optional)
        ASTNode modifiersNode = ASTNode.of("MODIFIERS");
        while (check("KEYWORD", "public") || check("KEYWORD", "private") || 
            check("KEYWORD", "protected") || check("KEYWORD", "static")) {
            modifiersNode.addChild(ASTNode.of(currentLexeme()));
            advance();
        }

        // 2. Parse Return Type and Name
        // Note: If return types can also be arrays, apply the same bracket logic here
        String returnType = currentLexeme(); advance(); 
        String methodName = currentLexeme(); advance(); 

        ASTNode methodNode = ASTNode.of("METHOD_DECL", methodName);
        
        if (!modifiersNode.getChildren().isEmpty()) {
            methodNode.addChild(modifiersNode);
        }

        methodNode.addChild(ASTNode.of("RETURN_TYPE", returnType));

        consume("SPECIAL_CHAR", "(");
        ASTNode paramsNode = ASTNode.of("PARAMS");
        
        // 3. Parse Parameters
        if (!check("SPECIAL_CHAR", ")")) {
            do {
                if (isTypeKeyword() || "IDENTIFIER".equals(currentType())) {
                    // --- THE FIX: Array Support for Parameters ---
                    StringBuilder fullType = new StringBuilder(currentLexeme());
                    advance(); // Consume base type (e.g., "String")

                    // If brackets follow the type, consume them to form the full type name
                    if (check("SPECIAL_CHAR", "[")) {
                        fullType.append(currentLexeme()); advance(); // Consume "["
                        if (check("SPECIAL_CHAR", "]")) {
                            fullType.append(currentLexeme()); advance(); // Consume "]"
                        }
                    }

                    // Now the next token must be the identifier (name)
                    if ("IDENTIFIER".equals(currentType())) {
                        String paramName = currentLexeme(); advance();
                        
                        ASTNode param = ASTNode.of("PARAM");
                        param.addChild(ASTNode.of("TYPE", fullType.toString()));
                        param.addChild(ASTNode.of("NAME", paramName));
                        paramsNode.addChild(param);
                    } else {
                        ErrorHandler.report("Expected parameter name after type", tokens.get(pos).getLine(), tokens.get(pos).getColumn());
                        recover();
                        break;
                    }
                } else {
                    ErrorHandler.report("Expected parameter declaration (Type Identifier)", tokens.get(pos).getLine(), tokens.get(pos).getColumn());
                    recover();
                    break;
                }
            } while (matchAndAdvance("PUNCTUATION", ","));
        }
        consume("SPECIAL_CHAR", ")");
        
        methodNode.addChild(paramsNode);
        
        // 4. Parse the method body
        ASTNode body = parseBlock();
        ASTNode bodyWrapper = ASTNode.of("BODY");
        bodyWrapper.addChild(body);
        methodNode.addChild(bodyWrapper);
        
        return methodNode;
    }

    // Helper method to safely peek 2 or more tokens ahead
    private boolean checkPeek(int offset, String type, String lexeme) {
        Tokens t = peek(offset);
        if (t == null) return false;
        return t.getType().equals(type) && t.getLexeme().equals(lexeme);
    }
    
    // Helper to check and consume in one step (useful for loops/params)
    private boolean matchAndAdvance(String type, String lexeme) {
        if (check(type, lexeme)) {
            advance();
            return true;
        }
        return false;
    }

    private boolean peekIsType() {
        Tokens t = peek(1);
        if (t == null || !"KEYWORD".equals(t.getType())) return false;
        String lex = t.getLexeme();
        return lex.equals("int") || lex.equals("double") || lex.equals("float")
            || lex.equals("boolean") || lex.equals("String") || lex.equals("char")
            || lex.equals("void") || lex.equals("var");
    }

    private boolean peekIsModifier() {
        Tokens t = peek(1);
        if (t == null || !"KEYWORD".equals(t.getType())) return false;
        String lex = t.getLexeme();
        return lex.equals("public") || lex.equals("private") || lex.equals("static") || lex.equals("protected");
    }

    private boolean peekIsClass() {
        Tokens t1 = peek(1);
        // If current is modifier, check if next is 'class' (e.g., public class)
        return t1 != null && "KEYWORD".equals(t1.getType()) && t1.getLexeme().equals("class");
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
        
        // TYPO CHECK: Detect likely type typos (lowercase identifiers that look like types)
        if (currentType() != null && currentType().equals("IDENTIFIER") && peekIsIdentifier()) {
            String possibleType = currentLexeme();
            String suggestions = suggestTypeTypos(possibleType);
            if (suggestions != null && !suggestions.isEmpty()) {
                Tokens t = current();
                ErrorHandler.report(
                    "Syntax Error: Unknown type '" + possibleType + "'. Did you mean: " + suggestions + "?",
                    t.getLine(), t.getColumn());
                
                // Error recovery: skip through the declaration/statement
                advance(); // Skip the misspelled type (e.g., "nt")
                if (currentType() != null && currentType().equals("IDENTIFIER")) {
                    advance(); // Skip the variable name (e.g., "a")
                }
                if (check("OPERATOR", "=")) {
                    advance(); // Skip '='
                    // Skip the initializer expression
                    while (!isAtEnd() && !check("PUNCTUATION", ";")) {
                        advance();
                    }
                }
                if (check("PUNCTUATION", ";")) {
                    advance(); // Skip the semicolon
                }
                return null; // Treat as error recovery
            }
        }

        // Expression statement
        ASTNode expr = parseExpression();
        consumePunctuation(";");
        // Use expression's coordinates (expr has them from parseExpression)
        ASTNode stmt = ASTNode.of("EXPR_STMT", "", expr.getLine(), expr.getColumn());
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
        Tokens t = current();
        consume("KEYWORD", "if");
        consume("SPECIAL_CHAR", "(");
        ASTNode condition = parseExpression();
        consume("SPECIAL_CHAR", ")");

        ASTNode thenBranch;
        // Check if the next token is a brace
        if (check("SPECIAL_CHAR", "{")) {
            thenBranch = parseBlock(); // Parses everything between { and }
        } else {
            thenBranch = parseStatement(); // Parses just the next single statement (e.g., return n;)
        }

        ASTNode ifNode = ASTNode.of("IF_STMT", t.getLine(), t.getColumn());
        ifNode.addChild(wrapAs("CONDITION", condition));
        ifNode.addChild(wrapAs("THEN", thenBranch));

        // Optional: Handle 'else' with the same logic
        if (check("KEYWORD", "else")) {
            advance();
            ASTNode elseBranch;
            if (check("SPECIAL_CHAR", "{")) {
                elseBranch = parseBlock();
            } else {
                elseBranch = parseStatement();
            }
            ifNode.addChild(wrapAs("ELSE", elseBranch));
        }

        return ifNode;
    }

    // ── while ──────────────────────────────────────────────────────────────────
    private ASTNode parseWhile() {
        Tokens t = current();
        consume("KEYWORD", "while");
        consume("SPECIAL_CHAR", "(");
        ASTNode condition = parseExpression();
        consume("SPECIAL_CHAR", ")");

        // --- THE FIX: Brace Check Logic ---
        ASTNode body;
        if (check("SPECIAL_CHAR", "{")) {
            body = parseBlock();
        } else {
            body = parseStatement();
        }

        ASTNode whileNode = ASTNode.of("WHILE_STMT", t.getLine(), t.getColumn());
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
        Tokens t = current();
        consume("KEYWORD", "for");
        consume("SPECIAL_CHAR", "(");

        ASTNode forNode = ASTNode.of("FOR", t.getLine(), t.getColumn());

        // Initialization
        ASTNode init;
        if (isTypeKeyword() && peekIsIdentifier()) {
            init = parseVarDeclNoSemiCheck(); 
        } else {
            init = parseExpression();
        }
        consumePunctuation(";");
        forNode.addChild(wrapAs("INIT", init));

        // Condition
        ASTNode condition = parseExpression();
        consumePunctuation(";");
        forNode.addChild(wrapAs("CONDITION", condition));

        // Update
        ASTNode update = parseExpression();
        forNode.addChild(wrapAs("UPDATE", update));

        consume("SPECIAL_CHAR", ")");

        // --- THE FIX: Brace Check Logic ---
        ASTNode body;
        if (check("SPECIAL_CHAR", "{")) {
            body = parseBlock(); // Parses everything between { and }
        } else {
            body = parseStatement(); // Parses exactly one statement
        }
        forNode.addChild(wrapAs("BODY", body));

        return forNode;
    }

    // ── Return Statement ───────────────────────────────────────────────────────
    private ASTNode parseReturn() {
        Tokens t = current(); // Coordinates of the 'return' keyword
        consume("KEYWORD", "return");
        
        ASTNode returnNode = ASTNode.of("RETURN", t.getLine(), t.getColumn());
        
        if (!check("PUNCTUATION", ";")) {
            ASTNode expr = parseExpression();
            if (expr != null) returnNode.addChild(expr);
        }
        
        consumePunctuation(";");
        return returnNode;
    }

    // ── break ──────────────────────────────────────────────────────────────────
    private ASTNode parseBreak() {
        Tokens t = current(); // Capture 'break' keyword coordinates
        consume("KEYWORD", "break");
        consumePunctuation(";");
        return ASTNode.of("BREAK", "", t.getLine(), t.getColumn());
    }

    // ── continue ───────────────────────────────────────────────────────────────
    private ASTNode parseContinue() {
        Tokens t = current(); // Capture 'continue' keyword coordinates
        consume("KEYWORD", "continue");
        consumePunctuation(";");
        return ASTNode.of("CONTINUE", "", t.getLine(), t.getColumn());
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
        Tokens t = current(); // 1. Capture the start token for coordinates
        String method;

        if (t.getLexeme().equals("System")) {
            // Handle standard: System.out.println(...)
            advance(); // System
            consume("PUNCTUATION", ".");
            advance(); // out
            consume("PUNCTUATION", ".");
            method = currentLexeme(); 
            advance(); // println or print
        } else {
            // 2. Handle simplified: print(...) or println(...)
            method = t.getLexeme();
            advance();
        }

        consume("SPECIAL_CHAR", "(");
        ASTNode arg = parseExpression();
        consume("SPECIAL_CHAR", ")");
        consumePunctuation(";");

        // 3. FIX: Pass the captured token's line and col to the factory
        ASTNode printNode = ASTNode.of("PRINT_STMT", method, t.getLine(), t.getColumn());
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
        Tokens typeToken = current(); // 1. Capture the start token (e.g., 'int')
        String typeName = currentLexeme(); advance(); 

        // Parent node to house all variables in this declaration line
        ASTNode groupNode = ASTNode.of("VAR_DECL_GROUP", typeToken.getLine(), typeToken.getColumn());

        do {
            Tokens varToken = current(); // 2. Capture coordinates for THIS specific variable name
            String varName = currentLexeme(); advance();

            // Create a specific declaration node for this identifier
            ASTNode declNode = ASTNode.of("VAR_DECL", typeName + " " + varName, varToken.getLine(), varToken.getColumn());
            
            // Attach the type and name children with precise coordinates
            declNode.addChild(ASTNode.of("TYPE", typeName, typeToken.getLine(), typeToken.getColumn()));
            declNode.addChild(ASTNode.of("NAME", varName, varToken.getLine(), varToken.getColumn()));

            // Handle optional initialization for EACH variable (e.g., int x = 5, y = 10;)
            if (check("OPERATOR", "=")) {
                advance(); 
                ASTNode init = parseExpression();
                declNode.addChild(wrapAs("INIT_VALUE", init));
            }
            
            groupNode.addChild(declNode);

        } while (matchAndAdvance("PUNCTUATION", ",")); // 3. Continue if there is a comma

        return groupNode;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  EXPRESSION PARSING  (Pratt / recursive-descent by precedence)
    // ═════════════════════════════════════════════════════════════════════════
    // 1. Entry point for all expressions
    private ASTNode parseExpression() {
        return parseAssignment(); 
    }

    // 2. Assignment must call Ternary
    private ASTNode parseAssignment() {
        ASTNode left = parseTernary(); // CHANGED: Now calls ternary instead of logicalOr

        if (currentType() != null && currentType().equals("OPERATOR") && isAssignmentOp(currentLexeme())) {
            Tokens opToken = current(); // Grab '=' coordinates
            String op = currentLexeme(); 
            advance();
            ASTNode right = parseAssignment();
            
            // Capture the LHS coordinates (variable name) for better error reporting
            Tokens lhsToken = (left.getType().equals("IDENTIFIER") && left.getLine() > 0) ? 
                                null : opToken; // Use left's own coords if available, else operator coords
            int line = (left.getLine() > 0) ? left.getLine() : opToken.getLine();
            int col = (left.getColumn() > 0) ? left.getColumn() : opToken.getColumn();
            
            ASTNode assign = ASTNode.of("ASSIGN", op, line, col);
            assign.addChild(left);
            assign.addChild(right);
            return assign;
        }
        return left;
    }

    private ASTNode parseTernary() {
        ASTNode condition = parseLogicalOr(); // The 'a > b' part

        if (check("OPERATOR", "?")) {
            Tokens t = current(); // Grab the 'OPERATOR' token
            advance(); 
            
            ASTNode thenExpr = parseExpression();
            consume("OPERATOR", ":"); // Matching the lexer classification
            ASTNode elseExpr = parseExpression();
            
            // Propagate the coordinates to the TERNARY node
            ASTNode ternary = ASTNode.of("TERNARY", "?", t.getLine(), t.getColumn());
            ternary.addChild(wrapAs("CONDITION", condition));
            ternary.addChild(wrapAs("THEN", thenExpr));
            ternary.addChild(wrapAs("ELSE", elseExpr));
            return ternary;
        }
        return condition;
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
            Tokens t = current(); // Capture operator coordinates
            String op = currentLexeme(); advance();
            ASTNode right = parseMultiplicative();
            ASTNode node = ASTNode.of("BINARY_OP", op, t.getLine(), t.getColumn());
            node.addChild(left); node.addChild(right);
            left = node;
        }
        return left;
    }

    private ASTNode parseMultiplicative() {
        ASTNode left = parseUnary();
        // Added % here to fix the "Expected ')' but found '%'" error
        while (currentType() != null && currentType().equals("OPERATOR")
               && (currentLexeme().equals("*") || currentLexeme().equals("/") || currentLexeme().equals("%"))) {
            Tokens t = current();
            String op = currentLexeme(); advance();
            ASTNode right = parseUnary();
            ASTNode node = ASTNode.of("BINARY_OP", op, t.getLine(), t.getColumn());
            node.addChild(left); node.addChild(right);
            left = node;
        }
        return left;
    }

    private ASTNode parseUnary() {
        // Type cast: (Type) expr
        if (check("OPERATOR", "?")) {
            int savePos = pos;
            advance(); // consume '('
            consume("OPERATOR", ":");
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
        Tokens t = current(); // 1. Capture token immediately for line/col tracking
        if (t == null || isAtEnd()) {
            ErrorHandler.report("Unexpected end of input in expression", -1, -1);
            return ASTNode.of("ERROR");
        }

        String type = t.getType(); // Use getType() to avoid reflection/SPECIALCHAR bug

        // ── Grouped Expression: ( expr ) ──
        if (type.equals("SPECIAL_CHAR") && t.getLexeme().equals("(")) {
            advance();
            ASTNode inner = parseExpression();
            consume("SPECIAL_CHAR", ")");
            return inner;
        }

        // ── Identifier or Method Call ──
        if (type.equals("IDENTIFIER")) {
            String name = currentLexeme();
            advance();
            
            // Check for direct method call: name(args)
            if (check("SPECIAL_CHAR", "(")) {
                return parseMethodCall(name, null); 
            }
            
            return ASTNode.of("IDENTIFIER", name, t.getLine(), t.getColumn());
        }

        // ── Number Constant ──
        if (type.equals("CONSTANT")) {
            String val = currentLexeme();
            advance();
            return ASTNode.of("NUMBER", val, t.getLine(), t.getColumn());
        }

        // ── String / Char Literals ──
        if (type.equals("LITERAL")) {
            String val = currentLexeme();
            advance();
            return ASTNode.of("LITERAL", val, t.getLine(), t.getColumn());
        }

        // ── Boolean / Null Keywords ──
        if (type.equals("KEYWORD") && (t.getLexeme().equals("true") || 
            t.getLexeme().equals("false") || t.getLexeme().equals("null"))) {
            String val = currentLexeme();
            advance();
            return ASTNode.of("LITERAL", val, t.getLine(), t.getColumn());
        }

        // ── Fallback: Error Handling ──
        String bad = currentLexeme();
        ErrorHandler.report("Unexpected token in expression: " + bad, t.getLine(), t.getColumn());
        advance();
        return ASTNode.of("ERROR", bad, t.getLine(), t.getColumn());
    }

    private ASTNode parseClassDecl() {
        Tokens startToken = current();
        
        // 1. Parse Modifiers (public, static, etc.)
        ASTNode modifiers = ASTNode.of("MODIFIERS");
        while (isModifier()) {
            modifiers.addChild(ASTNode.of(currentLexeme()));
            advance();
        }

        // 2. Consume 'class' and the Class Name
        consume("KEYWORD", "class");
        String className = currentLexeme();
        consume("IDENTIFIER", className);

        ASTNode classNode = ASTNode.of("CLASS_DECL", className, startToken.getLine(), startToken.getColumn());
        if (!modifiers.getChildren().isEmpty()) classNode.addChild(modifiers);

        // 3. Parse the Class Body { ... }
        consume("SPECIAL_CHAR", "{");
        ASTNode body = ASTNode.of("CLASS_BODY");
        while (!isAtEnd() && !check("SPECIAL_CHAR", "}")) {
            // Inside a class, we expect methods or field declarations
            if (isMethodStart()) {
                body.addChild(parseMethodDecl());
            } else {
                ASTNode field = parseStatement(); // Handles fields like 'int x = 10;'
                if (field != null) body.addChild(field);
            }
        }
        consume("SPECIAL_CHAR", "}");
        
        classNode.addChild(body);
        return classNode;
    }

    // ── Method Invocation ──────────────────────────────────────────────────────
    private ASTNode parseMethodCall(String methodName, ASTNode receiver) {
        // 1. Capture the current coordinate (the '(' token)
        Tokens t = current(); 
        
        // 2. Pass coordinates to the factory
        ASTNode methodCallNode = ASTNode.of("METHOD_CALL", methodName, t.getLine(), t.getColumn());
        
        // Add RECEIVER node if it's a member call (e.g., math.max)
        if (receiver != null) {
            ASTNode receiverNode = ASTNode.of("RECEIVER", t.getLine(), t.getColumn());
            receiverNode.addChild(receiver);
            methodCallNode.addChild(receiverNode);
        }

        consume("SPECIAL_CHAR", "(");
        
        // Ensure the ARGS wrapper also has a "birth certificate"
        ASTNode argsNode = ASTNode.of("ARGS", t.getLine(), t.getColumn());
        
        // Parse arguments if parenthesis is not immediately closed
        if (!check("SPECIAL_CHAR", ")")) {
            do {
                ASTNode argExpr = parseExpression(); // Calls your main expression parser
                if (argExpr != null) {
                    argsNode.addChild(argExpr);
                }
            } while (matchAndAdvance("PUNCTUATION", ","));
        }
        consume("SPECIAL_CHAR", ")");
        
        methodCallNode.addChild(argsNode);
        return methodCallNode;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  HELPER / UTILITY METHODS
    // ═════════════════════════════════════════════════════════════════════════

    private boolean isMethodStart() {
    // Case 1: Starts with a modifier (public, private, static, etc.)
    if (isModifier()) {
        // Look ahead to see if it's followed by a type and name: Modifier Type Name (
        if (peekIsType() && checkPeek(2, "IDENTIFIER", null) && checkPeek(3, "SPECIAL_CHAR", "(")) {
            return true;
        }
        // Handles cases with multiple modifiers: public static ...
        if (peekIsModifier()) {
            return true; 
        }
    } 
    // Case 2: Starts directly with a type: int calculate(
    else if (isTypeKeyword()) {
        if (peekIsIdentifier() && checkPeek(2, "SPECIAL_CHAR", "(")) {
            return true;
        }
    }
    return false;
}

    private boolean isAtEnd() { return pos >= tokens.size(); }

    private Tokens current()  { return isAtEnd() ? null : tokens.get(pos); }
    private Tokens peek(int offset) {
        int idx = pos + offset;
        return (idx >= tokens.size()) ? null : tokens.get(idx);
    }

    private String currentType() {
        Tokens t = current();
        return t == null ? null : t.getType(); 
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

    /**
     * Suggests type names similar to a misspelled identifier using edit distance.
     * Returns comma-separated suggestions or null if no close matches found.
     */
    private String suggestTypeTypos(String input) {
        String[] validTypes = {"int", "double", "float", "boolean", "char", "void", "String", "var", "long", "short", "byte"};
        java.util.List<String> suggestions = new java.util.ArrayList<>();
        
        for (String validType : validTypes) {
            if (editDistance(input, validType) <= 1) {
                suggestions.add(validType);
            }
        }
        
        return suggestions.isEmpty() ? null : String.join(", ", suggestions);
    }
    
    /**
     * Levenshtein distance: measures the minimum edits to transform one string to another.
     */
    private int editDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(Math.min(dp[i - 1][j], dp[i][j - 1]), dp[i - 1][j - 1]);
                }
            }
        }
        return dp[a.length()][b.length()];
    }

    private boolean isTypeKeyword() {
        String type = currentType();
        String lex = currentLexeme();

        boolean isBaseType = false;
        
        // 1. Check if it's a primitive keyword (int, double, void, etc.) or reference type (String)
        if ("KEYWORD".equals(type)) {
            return lex.equals("int") || lex.equals("double") || lex.equals("float")
                || lex.equals("boolean") || lex.equals("char") || lex.equals("void") 
                || lex.equals("var") || lex.equals("String");
        }
        
        // 2. DYNAMIC CHECK: If it's an Identifier starting with an Uppercase letter,
        // we treat it as a Reference Type (like MyClass, etc.)
        if ("IDENTIFIER".equals(type) && !lex.isEmpty() && Character.isUpperCase(lex.charAt(0))) {
            isBaseType = true;
        }

        if (isBaseType) {
                Tokens next = peek(1);
                Tokens next2 = peek(2);
                if (next != null && next.getLexeme().equals("[") && 
                    next2 != null && next2.getLexeme().equals("]")) {
                    return true; // It's an array type like String[]
                }
                return true; // It's a standard type
            }
    return false;
    }

    private boolean isModifier() {
        String lex = currentLexeme();
        return lex.equals("public") || lex.equals("private") || lex.equals("static") || lex.equals("protected");
    }

    private boolean peekIsIdentifier() {
        Tokens next = peek(1);
        return next != null && next.getClass().getSimpleName().equalsIgnoreCase("IDENTIFIER");
    }

    private boolean isPrintStatement() {
        // Heuristic: Check for standalone 'print', 'println', or 'printf'
        Tokens t0 = peek(0);
        if (t0 != null && (t0.getLexeme().equals("print") || 
            t0.getLexeme().equals("println") || 
            t0.getLexeme().equals("printf"))) {
            return true;
        }

        // Heuristic: IDENTIFIER "System" followed by "." "out" "." "println"/"print"
        Tokens t1 = peek(1), t2 = peek(2), t3 = peek(3), t4 = peek(4);

        if (t0 == null || !t0.getLexeme().equals("System")) return false;
        if (t1 == null || !t1.getLexeme().equals("."))      return false;
        if (t2 == null || !t2.getLexeme().equals("out"))    return false;
        if (t3 == null || !t3.getLexeme().equals("."))      return false;
        if (t4 == null) return false;
        
        return t4.getLexeme().equals("println") || t4.getLexeme().equals("print");
    }

    /** Wraps an existing node inside a labeled wrapper (e.g. CONDITION, THEN, BODY). */
    private ASTNode wrapAs(String label, ASTNode child) {
        if (child == null) return ASTNode.of(label);
        // FORCE the wrapper to take the child's identity
        int line = child.getLine();
        int col = child.getColumn();
        
        ASTNode wrapper = ASTNode.of(label, line, col);
        wrapper.addChild(child);
        return wrapper;
    }
}
