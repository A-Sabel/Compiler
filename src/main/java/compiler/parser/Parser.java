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
 *   assignment     → identifier (= | += | -= | *= | /= | %=) assignment | ternary
 *   ternary        → logicalOr ( ? expr : expr )?
 *   logicalOr      → logicalAnd ( || logicalAnd )*
 *   logicalAnd     → equality  ( && equality  )*
 *   equality       → relational ( (== | !=) relational )*
 *   relational     → additive  ( (< | > | <= | >=) additive )*
 *   additive       → multiplicative ( (+ | -) multiplicative )*
 *   multiplicative → unary ( (* | / | %) unary )*
 *   unary          → (! | - | ++ | -- | ~) unary | (Type) unary | postfix
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
            // 2. Check for Method Declaration
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

        // 2. Parse Return Type (supports arrays like int[])
        StringBuilder returnTypeSB = new StringBuilder(currentLexeme());
        advance(); // consume base return type
        if (check("SPECIAL_CHAR", "[")) {
            returnTypeSB.append(currentLexeme()); advance(); // consume '['
            if (check("SPECIAL_CHAR", "]")) {
                returnTypeSB.append(currentLexeme()); advance(); // consume ']'
            }
        }
        String returnType = returnTypeSB.toString();

        // 3. Parse Method Name
        String methodName = currentLexeme();
        advance();

        ASTNode methodNode = ASTNode.of("METHOD_DECL", methodName);

        if (!modifiersNode.getChildren().isEmpty()) {
            methodNode.addChild(modifiersNode);
        }

        methodNode.addChild(ASTNode.of("RETURN_TYPE", returnType));

        consume("SPECIAL_CHAR", "(");
        ASTNode paramsNode = ASTNode.of("PARAMS");

        // 4. Parse Parameters
        if (!check("SPECIAL_CHAR", ")")) {
            do {
                if (isTypeKeyword() || "IDENTIFIER".equals(currentType())) {
                    StringBuilder fullType = new StringBuilder(currentLexeme());
                    advance(); // Consume base type

                    // Array support: String[]
                    if (check("SPECIAL_CHAR", "[")) {
                        fullType.append(currentLexeme()); advance(); // '['
                        if (check("SPECIAL_CHAR", "]")) {
                            fullType.append(currentLexeme()); advance(); // ']'
                        }
                    }

                    if ("IDENTIFIER".equals(currentType())) {
                        String paramName = currentLexeme();
                        advance();
                        ASTNode param = ASTNode.of("PARAM");
                        param.addChild(ASTNode.of("TYPE", fullType.toString()));
                        param.addChild(ASTNode.of("NAME", paramName));
                        paramsNode.addChild(param);
                    } else {
                        ErrorHandler.report("Expected parameter name after type",
                            tokens.get(pos).getLine(), tokens.get(pos).getColumn());
                        recover();
                        break;
                    }
                } else {
                    ErrorHandler.report("Expected parameter declaration (Type Identifier)",
                        tokens.get(pos).getLine(), tokens.get(pos).getColumn());
                    recover();
                    break;
                }
            } while (matchAndAdvance("PUNCTUATION", ","));
        }
        consume("SPECIAL_CHAR", ")");

        methodNode.addChild(paramsNode);

        // 5. Parse the method body
        ASTNode body = parseBlock();
        ASTNode bodyWrapper = ASTNode.of("BODY");
        bodyWrapper.addChild(body);
        methodNode.addChild(bodyWrapper);

        return methodNode;
    }

    // ── Helper: peek ahead by offset ──────────────────────────────────────────
    /**
     * FIX: Added null-guard on lexeme to prevent NullPointerException when
     * callers pass null as the lexeme (e.g. checkPeek(2, "IDENTIFIER", null)).
     * When lexeme is null only the type is checked.
     */
    private boolean checkPeek(int offset, String type, String lexeme) {
        Tokens t = peek(offset);
        if (t == null) return false;
        if (!t.getType().equals(type)) return false;
        // FIX: if lexeme is null, only type match is required
        return lexeme == null || lexeme.equals(t.getLexeme());
    }

    // ── Helper: check-and-consume in one step ─────────────────────────────────
    private boolean matchAndAdvance(String type, String lexeme) {
        if (check(type, lexeme)) { advance(); return true; }
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
        return lex.equals("public") || lex.equals("private")
            || lex.equals("static") || lex.equals("protected");
    }

    private boolean peekIsClass() {
        Tokens t1 = peek(1);
        return t1 != null && "KEYWORD".equals(t1.getType()) && t1.getLexeme().equals("class");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  STATEMENT PARSING
    // ═════════════════════════════════════════════════════════════════════════

    private ASTNode parseStatement() {
        // 1. Skip stray semicolons
        if (check("PUNCTUATION", ";")) { advance(); return null; }

        // 2. Structural Blocks
        if (check("SPECIAL_CHAR", "{")) return parseBlock();

        // 3. Declaration Enforcer
        if (isTypeKeyword() || isPotentialTypeTypo()) {
            if (!isTypeKeyword()) {
                handleTypeTypo();
                return null;
            }
            ASTNode decl = parseVarDecl();
            if (decl == null) recover();
            return decl;
        }

        // 4. Keywords (Control Flow)
        if (check("KEYWORD", "if"))       return parseIf();
        if (check("KEYWORD", "while"))    return parseWhile();
        if (check("KEYWORD", "do"))       return parseDoWhile();
        if (check("KEYWORD", "for"))      return parseFor();
        if (check("KEYWORD", "break"))    return parseBreak();
        if (check("KEYWORD", "continue")) return parseContinue();
        if (check("KEYWORD", "return"))   return parseReturn();
        if (check("KEYWORD", "switch"))   return parseSwitch();

        // 5. System calls
        if (isPrintStatement()) return parsePrint();

        // 6. Fallback: Expression Statements
        ASTNode expr = parseExpression();
        consumePunctuation(";");
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

        ASTNode thenBranch = check("SPECIAL_CHAR", "{") ? parseBlock() : parseStatement();

        ASTNode ifNode = ASTNode.of("IF_STMT", t.getLine(), t.getColumn());
        ifNode.addChild(wrapAs("CONDITION", condition));
        ifNode.addChild(wrapAs("THEN", thenBranch));

        if (check("KEYWORD", "else")) {
            advance();
            ASTNode elseBranch = check("SPECIAL_CHAR", "{") ? parseBlock() : parseStatement();
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

        ASTNode body = check("SPECIAL_CHAR", "{") ? parseBlock() : parseStatement();

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

        if (isTypeKeyword()) {
            // FIX: null-check the result of parseVarDeclNoSemiCheck before using it
            ASTNode varDecl = parseVarDeclNoSemiCheck();

            // Enhanced for-each: for (Type var : collection)
            if (check("PUNCTUATION", ":") || check("OPERATOR", ":")) {
                advance(); // consume ':'
                ASTNode iterable = parseExpression();
                consume("SPECIAL_CHAR", ")");

                ASTNode forEachNode = ASTNode.of("FOR_EACH", t.getLine(), t.getColumn());
                // Wrap safely even if varDecl is null (error already reported)
                forEachNode.addChild(wrapAs("ITERATOR", varDecl));
                forEachNode.addChild(wrapAs("COLLECTION", iterable));
                ASTNode body = check("SPECIAL_CHAR", "{") ? parseBlock() : parseStatement();
                forEachNode.addChild(wrapAs("BODY", body));
                return forEachNode;
            }

            // Standard for loop (init was a declaration)
            consumePunctuation(";");
            ASTNode forNode = ASTNode.of("FOR", t.getLine(), t.getColumn());
            // FIX: wrapAs handles null child safely
            forNode.addChild(wrapAs("INIT", varDecl));

            ASTNode condition = parseExpression();
            consumePunctuation(";");
            forNode.addChild(wrapAs("CONDITION", condition));

            ASTNode update = parseExpression();
            forNode.addChild(wrapAs("UPDATE", update));
            consume("SPECIAL_CHAR", ")");

            ASTNode body = check("SPECIAL_CHAR", "{") ? parseBlock() : parseStatement();
            forNode.addChild(wrapAs("BODY", body));
            return forNode;
        }

        // Standard for loop (init is an existing variable expression)
        ASTNode forNode = ASTNode.of("FOR", t.getLine(), t.getColumn());
        ASTNode init = parseExpression();
        consumePunctuation(";");
        forNode.addChild(wrapAs("INIT", init));

        ASTNode condition = parseExpression();
        consumePunctuation(";");
        forNode.addChild(wrapAs("CONDITION", condition));

        ASTNode update = parseExpression();
        forNode.addChild(wrapAs("UPDATE", update));
        consume("SPECIAL_CHAR", ")");

        ASTNode body = check("SPECIAL_CHAR", "{") ? parseBlock() : parseStatement();
        forNode.addChild(wrapAs("BODY", body));
        return forNode;
    }

    // ── Return Statement ───────────────────────────────────────────────────────
    private ASTNode parseReturn() {
        Tokens t = current();
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
        Tokens t = current();
        consume("KEYWORD", "break");
        consumePunctuation(";");
        return ASTNode.of("BREAK", "", t.getLine(), t.getColumn());
    }

    // ── continue ───────────────────────────────────────────────────────────────
    private ASTNode parseContinue() {
        Tokens t = current();
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
                ASTNode caseBody = ASTNode.of("CASE_BODY");
                while (!isAtEnd() && !check("SPECIAL_CHAR", "}")
                       && !check("KEYWORD", "case") && !check("KEYWORD", "default")) {
                    ASTNode stmt = parseStatement();
                    if (stmt != null) caseBody.addChild(stmt);
                }
                caseNode.addChild(caseBody);
                casesWrapper.addChild(caseNode);
            } else if (check("KEYWORD", "default")) {
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
            } else {
                advance(); // skip unexpected tokens
            }
        }

        consume("SPECIAL_CHAR", "}");
        switchNode.addChild(casesWrapper);
        return switchNode;
    }

    // ── System.out.println(expr); ─────────────────────────────────────────────
    private ASTNode parsePrint() {
        Tokens t = current();
        String method;

        if (t.getLexeme().equals("System")) {
            advance();                   // System
            consume("PUNCTUATION", "."); // .
            advance();                   // out
            consume("PUNCTUATION", "."); // .
            method = currentLexeme();
            advance();                   // println / print
        } else {
            method = t.getLexeme();
            advance();
        }

        consume("SPECIAL_CHAR", "(");
        ASTNode arg = parseExpression();
        consume("SPECIAL_CHAR", ")");
        consumePunctuation(";");

        ASTNode printNode = ASTNode.of("PRINT_STMT", method, t.getLine(), t.getColumn());
        printNode.addChild(arg);
        return printNode;
    }

    // ── Array Literal ──────────────────────────────────────────────────────────
    private ASTNode parseArrayLiteral() {
        Tokens startToken = current();
        consume("SPECIAL_CHAR", "{");
        ASTNode literalNode = ASTNode.of("ARRAY_LITERAL", "", startToken.getLine(), startToken.getColumn());
        if (!check("SPECIAL_CHAR", "}")) {
            do {
                literalNode.addChild(parseExpression());
            } while (matchAndAdvance("PUNCTUATION", ","));
        }
        consume("SPECIAL_CHAR", "}");
        return literalNode;
    }

    // ── Variable Declaration ───────────────────────────────────────────────────
    private ASTNode parseVarDecl() {
        ASTNode node = parseVarDeclNoSemiCheck();
        consumePunctuation(";");
        return node;
    }

    private ASTNode parseVarDeclNoSemiCheck() {
        Tokens typeToken = current();
        StringBuilder fullType = new StringBuilder(currentLexeme());
        advance();

        // Array type: String[]
        if (check("SPECIAL_CHAR", "[")) {
            fullType.append(currentLexeme()); advance(); // '['
            if (check("SPECIAL_CHAR", "]")) {
                fullType.append(currentLexeme()); advance(); // ']'
            }
        }

        if (currentType() != null && currentType().equals("CONSTANT")) {
            ErrorHandler.report("Syntax Error: A number cannot be used as a variable name.",
                current().getLine(), current().getColumn());
            recover();
            return null;
        }

        String typeName = fullType.toString();
        ASTNode groupNode = ASTNode.of("VAR_DECL_GROUP", typeToken.getLine(), typeToken.getColumn());

        do {
            Tokens varToken = current();
            String varName = currentLexeme();
            advance();

            ASTNode declNode = ASTNode.of("VAR_DECL", typeName + " " + varName,
                varToken.getLine(), varToken.getColumn());
            declNode.addChild(ASTNode.of("TYPE", typeName, typeToken.getLine(), typeToken.getColumn()));
            declNode.addChild(ASTNode.of("NAME", varName, varToken.getLine(), varToken.getColumn()));

            if (check("OPERATOR", "=")) {
                advance();
                ASTNode init = parseExpression();
                declNode.addChild(wrapAs("INIT_VALUE", init));
            }

            groupNode.addChild(declNode);
        } while (matchAndAdvance("PUNCTUATION", ","));

        return groupNode;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  EXPRESSION PARSING  (recursive-descent by precedence)
    // ═════════════════════════════════════════════════════════════════════════

    private ASTNode parseExpression() {
        return parseAssignment();
    }

    private ASTNode parseAssignment() {
        ASTNode left = parseTernary();

        if (currentType() != null && currentType().equals("OPERATOR")
                && isAssignmentOp(currentLexeme())) {
            Tokens opToken = current();
            String op = currentLexeme();
            advance();
            ASTNode right = parseAssignment();

            int line = (left.getLine() > 0)   ? left.getLine()   : opToken.getLine();
            int col  = (left.getColumn() > 0) ? left.getColumn() : opToken.getColumn();

            ASTNode assign = ASTNode.of("ASSIGN", op, line, col);
            assign.addChild(left);
            assign.addChild(right);
            return assign;
        }
        return left;
    }

    private ASTNode parseTernary() {
        ASTNode condition = parseLogicalOr();

        if (check("OPERATOR", "?")) {
            Tokens t = current();
            advance(); // consume '?'

            ASTNode thenExpr = parseExpression();

            // FIX: The ternary ':' may be classified as OPERATOR or PUNCTUATION
            // depending on the lexer. Accept both to be safe.
            if (check("OPERATOR", ":")) {
                advance();
            } else if (check("PUNCTUATION", ":")) {
                advance();
            } else {
                ErrorHandler.report("Expected ':' in ternary expression",
                    current() != null ? current().getLine() : -1,
                    current() != null ? current().getColumn() : -1);
            }

            ASTNode elseExpr = parseExpression();

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
            Tokens t = current();
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
        while (currentType() != null && currentType().equals("OPERATOR")
               && (currentLexeme().equals("*") || currentLexeme().equals("/")
                   || currentLexeme().equals("%"))) {
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
        // FIX: Type cast — was incorrectly checking for "?" instead of "(".
        // Correct heuristic: ( TypeKeyword ) followed by an expression.
        // Save position so we can backtrack if this turns out not to be a cast.
        if (check("SPECIAL_CHAR", "(")) {
            int savedPos = pos;
            advance(); // consume '('
            if (isTypeKeyword()) {
                String type = currentLexeme();
                advance(); // consume type name
                // Optional array brackets in cast: (int[])
                StringBuilder castType = new StringBuilder(type);
                if (check("SPECIAL_CHAR", "[")) {
                    castType.append(currentLexeme()); advance();
                    if (check("SPECIAL_CHAR", "]")) {
                        castType.append(currentLexeme()); advance();
                    }
                }
                if (check("SPECIAL_CHAR", ")")) {
                    advance(); // consume ')'
                    ASTNode castExpr = parseUnary();
                    ASTNode castNode = ASTNode.of("CAST", castType.toString());
                    castNode.addChild(castExpr);
                    return castNode;
                }
            }
            // Not a cast — backtrack and let parsePrimary handle the '('
            pos = savedPos;
        }

        // 'new' operator: new ClassName(...) or new type[size]
        if (check("KEYWORD", "new")) {
            Tokens startToken = current();
            advance(); // consume 'new'
            String typeName = currentLexeme();
            advance(); // consume type / class name

            // Array instantiation: new int[5]
            if (check("SPECIAL_CHAR", "[")) {
                advance(); // consume '['
                ASTNode sizeExpr = parseExpression();
                consume("SPECIAL_CHAR", "]");
                ASTNode newArrayNode = ASTNode.of("NEW_ARRAY", typeName + "[]",
                    startToken.getLine(), startToken.getColumn());
                newArrayNode.addChild(sizeExpr);
                return newArrayNode;
            }

            // Standard object construction: new ClassName(args)
            ASTNode newNode = ASTNode.of("NEW", typeName,
                startToken.getLine(), startToken.getColumn());
            if (check("SPECIAL_CHAR", "(")) {
                advance(); // consume '('
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

        // Prefix unary operators: !, -, ++, --, ~
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

        // FIX: Removed the duplicate array-access branch that was unreachable.
        // The loop now handles: postfix ++/--, array access [], member access ., method calls.
        while (true) {
            // Postfix ++ and --
            if (currentType() != null && currentType().equals("OPERATOR")
                && (currentLexeme().equals("++") || currentLexeme().equals("--"))) {
                String op = currentLexeme(); advance();
                ASTNode postfix = ASTNode.of("POSTFIX_OP", op);
                postfix.addChild(node);
                node = postfix;
            }
            // Array access: expr[index]
            else if (check("SPECIAL_CHAR", "[")) {
                Tokens bracketToken = current();
                advance(); // consume '['
                ASTNode index = parseExpression();
                consume("SPECIAL_CHAR", "]");
                ASTNode access = ASTNode.of("ARRAY_ACCESS", "",
                    bracketToken.getLine(), bracketToken.getColumn());
                access.addChild(node);
                access.addChild(index);
                node = access;
            }
            // Member access: expr.member or expr.method(args)
            else if (check("PUNCTUATION", ".")) {
                advance(); // consume '.'
                String member = currentLexeme();
                advance(); // consume member name

                if (check("SPECIAL_CHAR", "(")) {
                    // Method call on object
                    advance(); // consume '('
                    ASTNode methodCall = ASTNode.of("METHOD_CALL", member);
                    methodCall.addChild(node); // receiver object
                    ASTNode args = ASTNode.of("ARGS");
                    while (!isAtEnd() && !check("SPECIAL_CHAR", ")")) {
                        args.addChild(parseExpression());
                        if (check("PUNCTUATION", ",")) advance();
                    }
                    consume("SPECIAL_CHAR", ")");
                    methodCall.addChild(args);
                    node = methodCall;
                } else {
                    // Field access
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
        Tokens t = current();
        if (t == null || isAtEnd()) {
            ErrorHandler.report("Unexpected end of input in expression", -1, -1);
            return ASTNode.of("ERROR");
        }

        // Array literal: { expr, expr, ... }
        if (check("SPECIAL_CHAR", "{")) {
            return parseArrayLiteral();
        }

        String type = t.getType();

        // Grouped expression: ( expr )
        if (type.equals("SPECIAL_CHAR") && t.getLexeme().equals("(")) {
            advance();
            ASTNode inner = parseExpression();
            consume("SPECIAL_CHAR", ")");
            return inner;
        }

        // Identifier or direct method call
        if (type.equals("IDENTIFIER")) {
            String name = currentLexeme();
            advance();
            if (check("SPECIAL_CHAR", "(")) {
                return parseMethodCall(name, null);
            }
            return ASTNode.of("IDENTIFIER", name, t.getLine(), t.getColumn());
        }

        // Number constant
        if (type.equals("CONSTANT")) {
            String val = currentLexeme(); advance();
            return ASTNode.of("NUMBER", val, t.getLine(), t.getColumn());
        }

        // String / char literals
        if (type.equals("LITERAL")) {
            String val = currentLexeme(); advance();
            return ASTNode.of("LITERAL", val, t.getLine(), t.getColumn());
        }

        // Boolean / null keywords
        if (type.equals("KEYWORD") && (t.getLexeme().equals("true")
                || t.getLexeme().equals("false") || t.getLexeme().equals("null"))) {
            String val = currentLexeme(); advance();
            return ASTNode.of("LITERAL", val, t.getLine(), t.getColumn());
        }

        // Fallback error
        String bad = currentLexeme();
        ErrorHandler.report("Unexpected token in expression: " + bad, t.getLine(), t.getColumn());
        advance();
        return ASTNode.of("ERROR", bad, t.getLine(), t.getColumn());
    }

    // ── Class Declaration ──────────────────────────────────────────────────────
    private ASTNode parseClassDecl() {
        Tokens startToken = current();

        ASTNode modifiers = ASTNode.of("MODIFIERS");
        while (isModifier()) {
            modifiers.addChild(ASTNode.of(currentLexeme()));
            advance();
        }

        consume("KEYWORD", "class");
        String className = currentLexeme();
        consume("IDENTIFIER", className);

        ASTNode classNode = ASTNode.of("CLASS_DECL", className,
            startToken.getLine(), startToken.getColumn());
        if (!modifiers.getChildren().isEmpty()) classNode.addChild(modifiers);

        consume("SPECIAL_CHAR", "{");
        ASTNode body = ASTNode.of("CLASS_BODY");
        while (!isAtEnd() && !check("SPECIAL_CHAR", "}")) {
            if (isMethodStart()) {
                body.addChild(parseMethodDecl());
            } else {
                ASTNode field = parseStatement();
                if (field != null) body.addChild(field);
            }
        }
        consume("SPECIAL_CHAR", "}");

        classNode.addChild(body);
        return classNode;
    }

    // ── Method Invocation ──────────────────────────────────────────────────────
    private ASTNode parseMethodCall(String methodName, ASTNode receiver) {
        Tokens t = current();
        ASTNode methodCallNode = ASTNode.of("METHOD_CALL", methodName, t.getLine(), t.getColumn());

        if (receiver != null) {
            ASTNode receiverNode = ASTNode.of("RECEIVER", t.getLine(), t.getColumn());
            receiverNode.addChild(receiver);
            methodCallNode.addChild(receiverNode);
        }

        consume("SPECIAL_CHAR", "(");
        ASTNode argsNode = ASTNode.of("ARGS", t.getLine(), t.getColumn());

        if (!check("SPECIAL_CHAR", ")")) {
            do {
                ASTNode argExpr = parseExpression();
                if (argExpr != null) argsNode.addChild(argExpr);
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
        // Case 1: starts with a modifier (public, private, static, protected)
        if (isModifier()) {
            if (peekIsType() && checkPeek(2, "IDENTIFIER", null) && checkPeek(3, "SPECIAL_CHAR", "(")) {
                return true;
            }
            if (peekIsModifier()) {
                return true;
            }
        }
        // Case 2: starts directly with a return type: int calculate(
        else if (isTypeKeyword()) {
            // FIX: Use getType() via checkPeek instead of the broken reflection-based peekIsIdentifier()
            if (checkPeek(1, "IDENTIFIER", null) && checkPeek(2, "SPECIAL_CHAR", "(")) {
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
        recover();
    }

    /**
     * Panic-mode recovery: skip tokens until a safe synchronisation point.
     */
    private void recover() {
        while (!isAtEnd()) {
            if (check("PUNCTUATION", ";")) { advance(); return; }
            if (check("SPECIAL_CHAR", "}")) { return; }
            if (currentType() != null && currentType().equals("KEYWORD")) {
                String lex = currentLexeme();
                if (lex.equals("if") || lex.equals("for") || lex.equals("while")
                        || lex.equals("int") || lex.equals("String") || lex.equals("return")) {
                    return;
                }
            }
            advance();
        }
    }

    private void consumePunctuation(String lexeme) {
        consume("PUNCTUATION", lexeme);
    }

    /**
     * FIX: Added %= to the set of recognised compound-assignment operators.
     */
    private boolean isAssignmentOp(String op) {
        return op.equals("=")  || op.equals("+=") || op.equals("-=")
            || op.equals("*=") || op.equals("/=") || op.equals("%=");
    }

    private boolean isRelationalOp(String op) {
        return op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">=");
    }

    /**
     * Suggests type names similar to a misspelled identifier using Levenshtein distance.
     */
    private String suggestTypeTypos(String input) {
        String[] validTypes = {
            "int", "double", "float", "boolean", "char",
            "void", "String", "var", "long", "short", "byte"
        };
        java.util.List<String> suggestions = new java.util.ArrayList<>();
        for (String validType : validTypes) {
            if (editDistance(input, validType) <= 1) suggestions.add(validType);
        }
        return suggestions.isEmpty() ? null : String.join(", ", suggestions);
    }

    /**
     * Levenshtein distance between two strings.
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
        String lex  = currentLexeme();

        if ("KEYWORD".equals(type)) {
            return lex.equals("int") || lex.equals("double") || lex.equals("float")
                || lex.equals("boolean") || lex.equals("char") || lex.equals("void")
                || lex.equals("var") || lex.equals("String");
        }

        // Uppercase-initial IDENTIFIER treated as a reference type (e.g. MyClass, String[])
        if ("IDENTIFIER".equals(type) && !lex.isEmpty() && Character.isUpperCase(lex.charAt(0))) {
            if (lex.equals("System")) return false;
            return true;
        }

        return false;
    }

    private boolean isModifier() {
        String lex = currentLexeme();
        return lex.equals("public") || lex.equals("private")
            || lex.equals("static") || lex.equals("protected");
    }

    private boolean isPrintStatement() {
        Tokens t0 = peek(0);
        if (t0 != null && (t0.getLexeme().equals("print")
                || t0.getLexeme().equals("println")
                || t0.getLexeme().equals("printf"))) {
            return true;
        }
        Tokens t1 = peek(1), t2 = peek(2), t3 = peek(3), t4 = peek(4);
        if (t0 == null || !t0.getLexeme().equals("System")) return false;
        if (t1 == null || !t1.getLexeme().equals("."))      return false;
        if (t2 == null || !t2.getLexeme().equals("out"))    return false;
        if (t3 == null || !t3.getLexeme().equals("."))      return false;
        if (t4 == null) return false;
        return t4.getLexeme().equals("println") || t4.getLexeme().equals("print");
    }

    /**
     * Wraps an existing node inside a labelled wrapper (e.g. CONDITION, THEN, BODY).
     * FIX: Handles null child gracefully — returns an empty wrapper instead of NPE-ing.
     */
    private ASTNode wrapAs(String label, ASTNode child) {
        if (child == null) return ASTNode.of(label);
        ASTNode wrapper = ASTNode.of(label, child.getLine(), child.getColumn());
        wrapper.addChild(child);
        return wrapper;
    }

    /**
     * Returns true when two consecutive IDENTIFIERs appear, suggesting a misspelled type.
     */
    private boolean isPotentialTypeTypo() {
        Tokens current = current();
        Tokens next    = peek(1);
        return current != null && current.getType().equals("IDENTIFIER")
            && next    != null && "IDENTIFIER".equals(next.getType());
    }

    /**
     * Reports a helpful error for a misspelled type and skips the bad tokens.
     */
    private void handleTypeTypo() {
        String possibleType = currentLexeme();
        String suggestions  = suggestTypeTypos(possibleType);
        Tokens t = current();

        if (suggestions != null && !suggestions.isEmpty()) {
            ErrorHandler.report(
                "Syntax Error: Unknown type '" + possibleType + "'. Did you mean: " + suggestions + "?",
                t.getLine(), t.getColumn());
        } else {
            ErrorHandler.report(
                "Syntax Error: Unexpected identifier '" + possibleType + "'.",
                t.getLine(), t.getColumn());
        }

        advance(); // skip misspelled type
        if (currentType() != null && currentType().equals("IDENTIFIER")) {
            advance(); // skip variable name
        }
    }
}