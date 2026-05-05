package compiler.semantics;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import compiler.parser.ast.ASTNode;
import compiler.util.ErrorHandler;

public class SemanticAnalyzer {
    private final SymbolTable symbolTable;

    private String currentMethodReturnType = null;
    private int lambdaDepth = 0;
    private String currentClassName = null;

    private int loopDepth = 0;
    private boolean isReachable = true;

    // Track variable declarations and usage for unused variable detection
    private static class VarInfo {
        String type;
        int line;
        int column;

        VarInfo(String type, int line, int column) {
            this.type = type;
            this.line = line;
            this.column = column;
        }
    }

    private final Deque<Map<String, VarInfo>> varDeclarations = new ArrayDeque<>();
    private final Deque<Set<String>> varUsage = new ArrayDeque<>();

    // Track exceptions caught in the current try-catch scopes
    private final Deque<Set<String>> currentCatchBlocks = new ArrayDeque<>();

    // Track exceptions declared in the current method's throws clause
    private final Set<String> currentMethodThrows = new HashSet<>();

    // Track class fields so field access can be type-checked by receiver type.
    private final Map<String, Map<String, String>> classFieldTypes = new HashMap<>();

    public SemanticAnalyzer() {
        this.symbolTable = new SymbolTable();
        // Initialize with global scope
        varDeclarations.push(new HashMap<>());
        varUsage.push(new HashSet<>());
    }

    public void analyze(ASTNode node) {
        if (node == null)
            return;

        String nodeType = node.getType();

        switch (nodeType) {
            case "PROGRAM":
                analyzeChildren(node);
                break;

            case "CLASS_DECL":
                currentClassName = node.getValue();
                symbolTable.enterScope();
                analyzeChildren(node);
                symbolTable.exitScope();
                currentClassName = null;
                break;

            case "METHOD_DECL":
                validateMethodDeclaration(node);
                return;

            case "BLOCK":
                analyzeBlock(node);
                break;

            // FIX: Added VAR_DECL_GROUP so multi-variable declarations (int x=1, y=2;)
            // are dispatched correctly instead of silently falling to analyzeChildren().
            case "VAR_DECL_GROUP":
                for (ASTNode child : node.getChildren()) {
                    if (child.getType().equals("VAR_DECL")) {
                        validateVariableDeclaration(child);
                    }
                }
                break;

            case "VAR_DECL":
                validateVariableDeclaration(node);
                break;

            case "ASSIGN":
                validateAssignment(node);
                break;

            // FIX: Case label corrected from "EXPRESSION_STMT" to "EXPR_STMT" to match
            // the token the parser actually emits. Previously this was dead code.
            // FIX 2: Route through analyze() instead of inferExpressionType() so that
            // ASSIGN children (e.g. standalone "cycle = 10;") reach validateAssignment()
            // and out-of-scope variable errors are correctly reported.
            case "EXPR_STMT":
                if (!node.getChildren().isEmpty()) {
                    ASTNode expr = node.getChildren().get(0);
                    analyze(expr);

                    // Warn about unused expressions (expressions without side effects)
                    if (isUselessExpression(expr)) {
                        ErrorHandler.reportWarning(
                                "Warning: Statement has no effect. This expression does nothing.",
                                expr.getLine(), expr.getColumn());
                    }
                }
                break;

            case "METHOD_CALL":
                inferExpressionType(node);
                break;

            // FIX: Removed the redundant validateBinaryOperation() call path.
            // inferExpressionType() already handles operator validation and
            // division-by-zero
            // checks. Calling validateBinaryOperation() + analyzeChildren() on top caused
            // every binary-op error to be reported 2-3x. Now we just type-check the node.
            case "BINARY_OP":
                inferExpressionType(node);
                break;

            case "RETURN":
                validateReturnStatement(node);
                break;

            case "TRY_STMT":
                analyzeTryStatement(node);
                break;

            case "THROW":
                analyzeThrowStatement(node);
                break;

            case "IF_STMT":
                validateBooleanCondition(node);
                analyzeChildren(node);
                break;

            case "WHILE_STMT":
            case "DO_WHILE":
                loopDepth++;
                validateBooleanCondition(node);
                analyzeChildren(node);
                loopDepth--;
                break;

            case "FOR":
                analyzeForStatement(node);
                break;

            case "FOR_EACH":
                analyzeForEach(node);
                break;

            case "BREAK":
            case "CONTINUE":
                if (loopDepth <= 0) {
                    ErrorHandler.report(
                            "Semantic Error: '" + nodeType.toLowerCase() + "' statement used outside of a loop.",
                            node.getLine(), node.getColumn());
                }
                break;

            case "SWITCH":
                analyzeSwitchStatement(node);
                break;

            case "PRINT_STMT":
                if (!node.getChildren().isEmpty()) {
                    inferExpressionType(node.getChildren().get(0));
                }
                break;

            default:
                analyzeChildren(node);
                break;
        }
    }

    // ── Try-Catch & Exceptions ─────────────────────────────────────────────────
    private void analyzeTryStatement(ASTNode node) {
        ASTNode body = null;
        List<ASTNode> catchNodes = new java.util.ArrayList<>();

        for (ASTNode child : node.getChildren()) {
            if (child.getType().equals("BODY"))
                body = child;
            else if (child.getType().equals("CATCH"))
                catchNodes.add(child);
        }

        Set<String> caughtHere = new HashSet<>();
        for (ASTNode c : catchNodes) {
            for (ASTNode cc : c.getChildren()) {
                if (cc.getType().equals("TYPE")) {
                    caughtHere.add(cc.getValue());
                }
            }
        }

        currentCatchBlocks.push(caughtHere);
        if (body != null) {
            analyze(body);
        }
        currentCatchBlocks.pop();

        for (ASTNode c : catchNodes) {
            symbolTable.enterScope();
            String name = null;
            String type = null;
            ASTNode catchBody = null;

            for (ASTNode cc : c.getChildren()) {
                if (cc.getType().equals("TYPE"))
                    type = cc.getValue();
                if (cc.getType().equals("NAME"))
                    name = cc.getValue();
                if (cc.getType().equals("BODY"))
                    catchBody = cc;
            }

            if (name != null && type != null) {
                symbolTable.defineVariable(name, type);
                if (!varDeclarations.isEmpty()) {
                    varDeclarations.peek().put(name, new VarInfo(type, c.getLine(), c.getColumn()));
                }

                if (isOverbroadExceptionType(type)) {
                    ErrorHandler.reportWarning(
                            "Warning: Catching broad exception type '" + type + "' may hide bugs.",
                            c.getLine(), c.getColumn());
                }
            }

            if (isEmptyCatchBody(catchBody)) {
                ErrorHandler.reportWarning(
                        "Warning: Empty catch block. Consider handling or rethrowing the exception.",
                        c.getLine(), c.getColumn());
            }

            if (catchBody != null) {
                analyze(catchBody);
            }
            symbolTable.exitScope();
        }
    }

    private void analyzeSwitchStatement(ASTNode node) {
        loopDepth++;

        ASTNode casesWrapper = null;
        for (ASTNode child : node.getChildren()) {
            if (child.getType().equals("EXPR") && !child.getChildren().isEmpty()) {
                inferExpressionType(child.getChildren().get(0));
            } else if (child.getType().equals("CASES")) {
                casesWrapper = child;
                for (ASTNode c : child.getChildren()) {
                    if (c.getType().equals("CASE") && !c.getChildren().isEmpty()) {
                        inferExpressionType(c.getChildren().get(0));
                    }
                }
            }
        }

        analyzeChildren(node);

        if (casesWrapper != null) {
            warnAboutSwitchFallthrough(casesWrapper);
        }

        loopDepth--;
    }

    private void warnAboutSwitchFallthrough(ASTNode casesWrapper) {
        List<ASTNode> cases = casesWrapper.getChildren();
        for (int i = 0; i < cases.size(); i++) {
            ASTNode current = cases.get(i);
            if (!("CASE".equals(current.getType()) || "DEFAULT".equals(current.getType()))) {
                continue;
            }

            ASTNode body = null;
            for (ASTNode child : current.getChildren()) {
                if ("CASE_BODY".equals(child.getType())) {
                    body = child;
                    break;
                }
            }

            if (body == null || body.getChildren().isEmpty()) {
                continue;
            }

            ASTNode lastStmt = body.getChildren().get(body.getChildren().size() - 1);
            if (isFallthroughTerminator(lastStmt)) {
                continue;
            }

            boolean hasNextBranch = false;
            for (int j = i + 1; j < cases.size(); j++) {
                ASTNode next = cases.get(j);
                if ("CASE".equals(next.getType()) || "DEFAULT".equals(next.getType())) {
                    hasNextBranch = true;
                    break;
                }
            }

            if (hasNextBranch) {
                ErrorHandler.reportWarning(
                        "Warning: Possible switch fallthrough. Add break, return, or throw if intentional.",
                        lastStmt.getLine(), lastStmt.getColumn());
            }
        }
    }

    private void analyzeThrowStatement(ASTNode node) {
        if (node.getChildren().isEmpty())
            return;
        ASTNode expr = node.getChildren().get(0);
        String exType = inferExpressionType(expr);
        if (exType == null || exType.equals("unknown") || exType.equals("type_error")
                || exType.equals("RuntimeException")) {
            return;
        }
        boolean isCaught = false;
        for (Set<String> caught : currentCatchBlocks) {
            if (caught.contains(exType) || caught.contains("Exception")) {
                isCaught = true;
                break;
            }
        }
        if (isCaught || isExceptionLikeType(exType)) {
            return;
        }
        if (!currentMethodThrows.contains(exType) && !currentMethodThrows.contains("Exception")) {
            ErrorHandler.report("Semantic Error: Unhandled exception type '" + exType
                    + "'. Must be caught or declared to be thrown.", node.getLine(), node.getColumn());
        }
    }

    // ── For-Each ───────────────────────────────────────────────────────────────
    /**
     * FIX: Extracted FOR_EACH logic into its own method and added bounds-checking
     * on every .get(N) access to avoid IndexOutOfBoundsException on malformed AST.
     */
    private void analyzeForEach(ASTNode node) {
        symbolTable.enterScope();
        loopDepth++;

        List<ASTNode> topChildren = node.getChildren();

        // Safely unpack ITERATOR wrapper → VAR_DECL_GROUP → VAR_DECL
        ASTNode varDecl = null;
        if (topChildren.size() > 0) {
            ASTNode iterWrapper = topChildren.get(0);
            if (!iterWrapper.getChildren().isEmpty()) {
                ASTNode varGroup = iterWrapper.getChildren().get(0);
                if (!varGroup.getChildren().isEmpty()) {
                    varDecl = varGroup.getChildren().get(0);
                }
            }
        }

        // Safely unpack COLLECTION wrapper → expression
        ASTNode collectionNode = null;
        if (topChildren.size() > 1) {
            ASTNode collWrapper = topChildren.get(1);
            if (!collWrapper.getChildren().isEmpty()) {
                collectionNode = collWrapper.getChildren().get(0);
            }
        }

        // Safely unpack BODY wrapper → body node
        ASTNode bodyNode = null;
        if (topChildren.size() > 2) {
            ASTNode bodyWrapper = topChildren.get(2);
            if (!bodyWrapper.getChildren().isEmpty()) {
                bodyNode = bodyWrapper.getChildren().get(0);
            }
        }

        // Validate collection is an array type
        String collectionType = collectionNode != null ? inferExpressionType(collectionNode) : null;
        if (collectionType != null && !collectionType.equals("type_error")
                && !collectionType.endsWith("[]")) {
            ErrorHandler.report(
                    "Semantic Error: For-each loop requires an array, found " + collectionType + ".",
                    collectionNode.getLine(), collectionNode.getColumn());
        }

        // Register iterator variable
        if (varDecl != null) {
            validateVariableDeclaration(varDecl);

            // Check iterator type matches array element type
            String expectedElemType = (collectionType != null && collectionType.endsWith("[]"))
                    ? collectionType.substring(0, collectionType.length() - 2)
                    : null;

            if (expectedElemType != null && !varDecl.getChildren().isEmpty()) {
                String iteratorType = varDecl.getChildren().get(0).getValue(); // TYPE child
                if (!isTypeCompatible(iteratorType, expectedElemType)) {
                    ErrorHandler.report(
                            "Semantic Error: Incompatible types in for-each loop. Expected "
                                    + expectedElemType + " but got " + iteratorType + ".",
                            varDecl.getLine(), varDecl.getColumn());
                }
            }
        }

        // Analyze loop body
        if (bodyNode != null) {
            analyze(bodyNode);
        }

        loopDepth--;
        symbolTable.exitScope();
    }

    /**
     * Analyzes a standard for-loop in source order so the initializer is in scope
     * before the condition and update expressions are checked.
     */
    private void analyzeForStatement(ASTNode node) {
        symbolTable.enterScope();
        loopDepth++;

        ASTNode initNode = null;
        ASTNode conditionNode = null;
        ASTNode updateNode = null;
        ASTNode bodyNode = null;

        for (ASTNode child : node.getChildren()) {
            switch (child.getType()) {
                case "INIT":
                    initNode = child;
                    break;
                case "CONDITION":
                    conditionNode = child;
                    break;
                case "UPDATE":
                    updateNode = child;
                    break;
                case "BODY":
                    bodyNode = child;
                    break;
            }
        }

        if (initNode != null && !initNode.getChildren().isEmpty()) {
            analyze(initNode.getChildren().get(0));
        }

        if (conditionNode != null && !conditionNode.getChildren().isEmpty()) {
            validateBooleanCondition(node);
        }

        if (updateNode != null && !updateNode.getChildren().isEmpty()) {
            ASTNode updateList = updateNode.getChildren().get(0);
            if (updateList != null && updateList.getType().equals("UPDATE_LIST")) {
                for (ASTNode expr : updateList.getChildren()) {
                    inferExpressionType(expr);
                }
            }
        }

        if (bodyNode != null && !bodyNode.getChildren().isEmpty()) {
            analyze(bodyNode.getChildren().get(0));
        }

        loopDepth--;
        symbolTable.exitScope();
    }

    // ── Variable Declaration ───────────────────────────────────────────────────
    /**
     * Validates a single VAR_DECL node.
     * Structure: VAR_DECL → [TYPE, NAME, optional INIT_VALUE]
     */
    private void validateVariableDeclaration(ASTNode node) {
        List<ASTNode> children = node.getChildren();

        if (children.size() < 2) {
            ErrorHandler.report("Semantic Error: Invalid variable declaration structure.",
                    node.getLine(), node.getColumn());
            return;
        }

        ASTNode typeNode = children.get(0);
        ASTNode nameNode = children.get(1);
        String type = typeNode.getValue();
        String name = nameNode.getValue();

        // Duplicate definition check
        if (symbolTable.isDeclaredInCurrentScope(name)) {
            ErrorHandler.report("Semantic Error: Variable '" + name + "' is already defined in this scope.",
                    nameNode.getLine(), nameNode.getColumn());
            return;
        }

        // Variable shadowing check (warning only, not an error)
        if (symbolTable.isDeclaredInOuterScope(name)) {
            ErrorHandler.reportWarning(
                    "Warning: Variable '" + name + "' shadows a variable from an outer scope.",
                    nameNode.getLine(), nameNode.getColumn());
        }

        // Initializer type check
        if (children.size() >= 3) {
            ASTNode initWrapper = children.get(2);
            ASTNode initExpr = initWrapper.getChildren().isEmpty()
                    ? initWrapper
                    : initWrapper.getChildren().get(0);

            String initType = inferExpressionType(initExpr);

            // 'var' is inferred from its initializer.
            if ("var".equals(type) && initType != null && !"type_error".equals(initType)) {
                type = initType;
            }

            if (initType != null && !initType.equals("type_error")
                    && !"var".equals(typeNode.getValue())
                    && !isTypeCompatible(type, initType)) {
                ErrorHandler.report(
                        "Semantic Error: Type mismatch in initialization. Variable '" + name
                                + "' is " + type + " but initialized with " + initType + ".",
                        initExpr.getLine(), initExpr.getColumn());
            }
        } else if ("var".equals(type)) {
            ErrorHandler.report(
                    "Semantic Error: 'var' declarations must have an initializer.",
                    node.getLine(), node.getColumn());
        }

        symbolTable.defineVariable(name, type);

        // Record class fields when we are in class scope but not inside a method.
        if (currentClassName != null && currentMethodReturnType == null) {
            classFieldTypes.computeIfAbsent(currentClassName, k -> new HashMap<>())
                    .put(name, type);
        }

        // Track variable declaration for unused variable detection
        if (!varDeclarations.isEmpty()) {
            varDeclarations.peek().put(name, new VarInfo(type, nameNode.getLine(), nameNode.getColumn()));
        }
    }

    // ── Method Declaration ─────────────────────────────────────────────────────
    /**
     * Validates method declarations and sets up parameter scoping.
     * Structure: METHOD_DECL(name) → [MODIFIERS?, RETURN_TYPE, PARAMS, BODY]
     */
    private void validateMethodDeclaration(ASTNode node) {
        String methodName = node.getValue();

        String returnType = "void";
        ASTNode paramsNode = null;
        ASTNode bodyNode = null;
        ASTNode throwsNode = null;

        for (ASTNode child : node.getChildren()) {
            switch (child.getType()) {
                case "RETURN_TYPE":
                    returnType = child.getValue();
                    break;
                case "PARAMS":
                    paramsNode = child;
                    break;
                case "THROWS":
                    throwsNode = child;
                    break;
                case "BODY":
                    bodyNode = child;
                    break;
            }
        }

        List<String> paramTypes = new java.util.ArrayList<>();
        List<String> paramNames = new java.util.ArrayList<>();

        if (paramsNode != null) {
            for (ASTNode param : paramsNode.getChildren()) {
                if (param.getChildren().size() >= 2) {
                    paramTypes.add(param.getChildren().get(0).getValue());
                    paramNames.add(param.getChildren().get(1).getValue());
                }
            }
        }

        // Duplicate signature check
        List<SymbolTable.MethodSignature> overloads = symbolTable.lookupMethods(methodName);
        boolean isDuplicate = false;
        if (overloads != null) {
            for (SymbolTable.MethodSignature sig : overloads) {
                if (sig.parameterTypes.equals(paramTypes)) {
                    isDuplicate = true;
                    break;
                }
            }
        }

        if (isDuplicate) {
            // FIX: Use node.getLine()/getColumn() instead of -1,-1 for useful diagnostics.
            ErrorHandler.report(
                    "Semantic Error: Duplicate method signature for '" + methodName + "'.",
                    node.getLine(), node.getColumn());
        } else {
            symbolTable.defineMethod(methodName, returnType, paramTypes);
        }

        currentMethodThrows.clear();
        if (throwsNode != null) {
            for (ASTNode exNode : throwsNode.getChildren()) {
                currentMethodThrows.add(exNode.getValue());
            }
        }

        // Set up scope and analyze body
        currentMethodReturnType = returnType;
        symbolTable.enterScope();

        // Track variable declarations and usage in the method scope
        varDeclarations.push(new HashMap<>());
        varUsage.push(new HashSet<>());

        for (int i = 0; i < paramNames.size(); i++) {
            symbolTable.defineVariable(paramNames.get(i), paramTypes.get(i));
            // Track parameters as declared variables
            varDeclarations.peek().put(paramNames.get(i),
                    new VarInfo(paramTypes.get(i), node.getLine(), node.getColumn()));
        }

        if (bodyNode != null) {
            analyze(bodyNode);
        }

        // Check for unused variables in the method scope
        checkUnusedVariables();

        varUsage.pop();
        varDeclarations.pop();
        symbolTable.exitScope();
        currentMethodReturnType = null;

        // Rule 10: Missing return statement check
        if (!returnType.equals("void") && !allPathsReturn(bodyNode)) {
            ErrorHandler.report("Semantic Error: Missing return statement in method '" + methodName + "'.",
                    node.getLine(), node.getColumn());
        }
    }

    // ── Return Statement ───────────────────────────────────────────────────────
    private void validateReturnStatement(ASTNode node) {
        if (currentMethodReturnType == null) {
            ErrorHandler.report("Semantic Error: return statement outside of method.",
                    node.getLine(), node.getColumn());
            return;
        }

        // Return statements inside lambda bodies are checked structurally, not
        // against the enclosing method's return type.
        if (lambdaDepth > 0) {
            if (!node.getChildren().isEmpty()) {
                inferExpressionType(node.getChildren().get(0));
            }
            return;
        }

        boolean hasReturnValue = !node.getChildren().isEmpty();

        if (currentMethodReturnType.equals("void") && hasReturnValue) {
            ErrorHandler.report("Semantic Error: void method cannot return a value.",
                    node.getLine(), node.getColumn());
        } else if (!currentMethodReturnType.equals("void") && !hasReturnValue) {
            ErrorHandler.report(
                    "Semantic Error: Missing return value for method expecting '"
                            + currentMethodReturnType + "'.",
                    node.getLine(), node.getColumn());
        } else if (hasReturnValue) {
            String actualType = inferExpressionType(node.getChildren().get(0));
            if (!isTypeCompatible(currentMethodReturnType, actualType)) {
                ErrorHandler.report(
                        "Semantic Error: Incompatible return type. Expected '"
                                + currentMethodReturnType + "' but got '" + actualType + "'.",
                        node.getLine(), node.getColumn());
            }
        }
    }

    // ── Assignment ─────────────────────────────────────────────────────────────
    /**
     * Validates assignment operations.
     * Structure: ASSIGN → [lhs, rhs]
     */
    private void validateAssignment(ASTNode node) {
        List<ASTNode> children = node.getChildren();
        if (children.size() < 2)
            return;

        ASTNode lhs = children.get(0);
        ASTNode rhs = children.get(1);

        // 1. Resolve the base variable name and validate the target type
        String varName;
        String lhsType = lhs.getType();
        String expectedType = null;

        if (lhsType.equals("ARRAY_ACCESS")) {
            // For array access, the name is in the first child (the identifier)
            varName = lhs.getChildren().get(0).getValue();
            String baseVarType = symbolTable.lookupVariableType(varName);
            if (baseVarType == null) {
                ErrorHandler.report("Semantic Error: Variable '" + varName + "' used in assignment before declaration.",
                        node.getLine(), node.getColumn());
                return;
            }
            expectedType = baseVarType.endsWith("[]")
                    ? baseVarType.substring(0, baseVarType.length() - 2)
                    : baseVarType;
        } else if (lhsType.equals("IDENTIFIER") || lhsType.equals("FIELD_ACCESS")) {
            varName = lhs.getValue();
            if (lhsType.equals("FIELD_ACCESS")) {
                expectedType = resolveFieldAccessType(lhs);
                if (expectedType == null) {
                    String receiverType = lhs.getChildren().isEmpty()
                            ? null
                            : inferExpressionType(lhs.getChildren().get(0));
                    if (reportUnknownField(lhs, receiverType, varName)) {
                        return;
                    }
                    ErrorHandler.report(
                            "Semantic Error: Variable '" + varName + "' used in assignment before declaration.",
                            node.getLine(), node.getColumn());
                    return;
                }
            }
        } else {
            ErrorHandler.report("Semantic Error: Invalid assignment target.", node.getLine(), node.getColumn());
            return;
        }

        // 2. Look up the variable in the Symbol Table (local/field declarations)
        if (expectedType == null) {
            String baseVarType = symbolTable.lookupVariableType(varName);
            if (baseVarType == null) {
                ErrorHandler.report("Semantic Error: Variable '" + varName + "' used in assignment before declaration.",
                        node.getLine(), node.getColumn());
                return;
            }
            expectedType = baseVarType;
        }

        if (lhsType.equals("FIELD_ACCESS") && lhs.getValue().equals("length")) {
            // Prevent assigning to read-only .length property
            ErrorHandler.report("Semantic Error: Cannot assign value to read-only property 'length'.",
                    lhs.getLine(), lhs.getColumn());
            return;
        }

        // 4. Validate RHS compatibility
        String rhsType = inferExpressionType(rhs);

        if (isSelfAssignment(lhs, rhs)) {
            ErrorHandler.reportWarning(
                    "Warning: Self-assignment has no effect.",
                    node.getLine(), node.getColumn());
        }

        if (rhsType != null && !rhsType.equals("type_error") && !isTypeCompatible(expectedType, rhsType)) {
            ErrorHandler.report("Semantic Error: Cannot assign " + rhsType + " to " + expectedType + ".",
                    node.getLine(), node.getColumn());
        }
    }

    // ── Expression Type Inference ──────────────────────────────────────────────
    /**
     * Infers the result type of an expression node.
     * This is the single authoritative place for type checking of expressions —
     * do NOT duplicate these checks in other methods.
     */
    private String inferExpressionType(ASTNode expr) {
        if (expr == null)
            return "void";

        String type = expr.getType();
        String value = expr.getValue();

        switch (type) {
            case "NUMBER":
                // Rule 12: Integer Overflow Prevention
                if (!value.contains(".") && !value.contains("e") && !value.contains("E")) {
                    return "int";
                }
                return "double";

            case "LITERAL":
                if (value.startsWith("\""))
                    return "String";
                if (value.startsWith("'"))
                    return "char";
                if (value.equals("true") || value.equals("false"))
                    return "boolean";
                if (value.equals("null"))
                    return "null";
                return "String";

            case "IDENTIFIER": {
                String declaredType = symbolTable.lookupVariableType(value);
                if (declaredType == null) {
                    ErrorHandler.report(
                            "Semantic Error: Identifier '" + value + "' used before declaration.",
                            expr.getLine(), expr.getColumn());
                    return "unknown";
                }

                // Track variable usage for unused variable detection
                if (!varUsage.isEmpty()) {
                    varUsage.peek().add(value);
                }

                return declaredType;
            }

            case "BINARY_OP": {
                String op = value;
                List<ASTNode> children = expr.getChildren();
                if (children.size() < 2)
                    return "unknown";

                ASTNode leftNode = children.get(0);
                ASTNode rightNode = children.get(1);
                String leftType = inferExpressionType(leftNode);
                String rightType = inferExpressionType(rightNode);

                if ((op.equals("==") || op.equals("!="))
                        && (isBooleanLiteralNode(leftNode) || isBooleanLiteralNode(rightNode))) {
                    if (expr.getAttribute("warned_bool_literal_compare") == null) {
                        expr.setAttribute("warned_bool_literal_compare", "true");
                        ErrorHandler.reportWarning(
                                "Warning: Comparison with a boolean literal may be redundant.",
                                getFallbackLine(expr, leftNode, rightNode),
                                getFallbackColumn(expr, leftNode, rightNode));
                    }
                }

                if ((op.equals("==") || op.equals("!="))
                        && (isNullLiteralNode(leftNode) || isNullLiteralNode(rightNode))) {
                    if (expr.getAttribute("warned_null_compare") == null) {
                        expr.setAttribute("warned_null_compare", "true");
                        ErrorHandler.reportWarning(
                                "Warning: Null comparison should be deliberate; consider an explicit null check.",
                                getFallbackLine(expr, leftNode, rightNode),
                                getFallbackColumn(expr, leftNode, rightNode));
                    }
                }

                // Division/modulo by literal zero
                if ((op.equals("/") || op.equals("%"))
                        && rightNode.getType().equals("NUMBER")
                        && (rightNode.getValue().equals("0") || rightNode.getValue().equals("0.0"))) {
                    ErrorHandler.report(
                            "Semantic Error: Arithmetic Exception: / by zero.",
                            rightNode.getLine(), rightNode.getColumn());
                }

                if ((op.equals("==") || op.equals("!=")) && isSameExpression(leftNode, rightNode)
                        && expr.getAttribute("warned_self_compare") == null) {
                    expr.setAttribute("warned_self_compare", "true");
                    ErrorHandler.reportWarning(
                            "Warning: Comparing an expression with itself is usually redundant.",
                            getFallbackLine(expr, leftNode, rightNode),
                            getFallbackColumn(expr, leftNode, rightNode));
                }

                if (leftType != null && rightType != null) {
                    validateOperatorTypes(op, leftType, rightType, leftNode, rightNode,
                            expr.getLine(), expr.getColumn());
                }

                // Determine result type
                if (op.equals("&&") || op.equals("||")
                        || op.equals("<") || op.equals(">") || op.equals("<=")
                        || op.equals(">=") || op.equals("==") || op.equals("!=")) {
                    return "boolean";
                }
                if (op.equals("+") && ("String".equals(leftType) || "String".equals(rightType))) {
                    return "String";
                }
                if ("type_error".equals(leftType) || "type_error".equals(rightType)) {
                    return "type_error";
                }
                if ("double".equals(leftType) || "double".equals(rightType))
                    return "double";
                if ("float".equals(leftType) || "float".equals(rightType))
                    return "float";
                if ("long".equals(leftType) || "long".equals(rightType))
                    return "long";
                return "int";
            }

            case "UNARY_OP":
                if (value.equals("!"))
                    return "boolean";
                if (value.equals("++") || value.equals("--")) {
                    if (!expr.getChildren().isEmpty()) {
                        validateLValue(expr.getChildren().get(0), value);
                    }
                }
                if (!expr.getChildren().isEmpty()) {
                    return inferExpressionType(expr.getChildren().get(0));
                }
                return "int";

            case "POSTFIX_OP":
                if (!expr.getChildren().isEmpty()) {
                    validateLValue(expr.getChildren().get(0), value);
                    return inferExpressionType(expr.getChildren().get(0));
                }
                return "int";

            case "METHOD_CALL": {
                String methodName = value;

                // Detect instance method calls on built-in String receiver types.
                ASTNode receiverNode = null;
                for (ASTNode child : expr.getChildren()) {
                    if ("RECEIVER".equals(child.getType())) {
                        receiverNode = child;
                        break;
                    }
                }
                if (receiverNode == null) {
                    for (ASTNode child : expr.getChildren()) {
                        if (!"ARGS".equals(child.getType()) && !"METHOD_DECL".equals(child.getType())) {
                            receiverNode = child;
                            break;
                        }
                    }
                }
                if (receiverNode != null) {
                    ASTNode receiverExpr = receiverNode;
                    if ("RECEIVER".equals(receiverNode.getType()) && !receiverNode.getChildren().isEmpty()) {
                        receiverExpr = receiverNode.getChildren().get(0);
                    }
                    String receiverType = inferExpressionType(receiverExpr);
                    if ("String".equals(receiverType) && "length".equals(methodName)) {
                        ASTNode argsNode = null;
                        for (ASTNode child : expr.getChildren()) {
                            if ("ARGS".equals(child.getType())) {
                                argsNode = child;
                                break;
                            }
                        }
                        int argCount = argsNode != null ? argsNode.getChildren().size() : 0;
                        if (argCount == 0) {
                            expr.setAttribute("resolved_return_type", "int");
                            return "int";
                        }
                    }
                }

                List<SymbolTable.MethodSignature> possibleMethods = symbolTable.lookupMethods(methodName);

                if (possibleMethods == null || possibleMethods.isEmpty()) {
                    // Check if it's a variable acting as a Lambda function pointer
                    String varType = symbolTable.lookupVariableType(methodName);
                    if (varType != null) {
                        if (!varUsage.isEmpty())
                            varUsage.peek().add(methodName);
                        expr.setAttribute("resolved_return_type", "Object");
                        return "Object";
                    }

                    ErrorHandler.report(
                            "Semantic Error: Call to undefined method '" + methodName + "'.",
                            expr.getLine(), expr.getColumn());
                    return "unknown";
                }

                // Collect argument types
                List<ASTNode> argNodes = new java.util.ArrayList<>();
                for (ASTNode child : expr.getChildren()) {
                    if (child.getType().equals("ARGS")) {
                        argNodes = child.getChildren();
                        break;
                    }
                }
                List<String> providedTypes = new java.util.ArrayList<>();
                for (ASTNode arg : argNodes) {
                    providedTypes.add(inferExpressionType(arg));
                }

                // Single overload: give specific arity + type errors
                if (possibleMethods.size() == 1) {
                    SymbolTable.MethodSignature sig = possibleMethods.get(0);
                    expr.setAttribute("resolved_return_type", sig.returnType);
                    if (sig.parameterTypes.size() != providedTypes.size()) {
                        ErrorHandler.report(
                                "Semantic Error: Method '" + methodName + "' expects "
                                        + sig.parameterTypes.size() + " argument(s), but got "
                                        + providedTypes.size() + ".",
                                expr.getLine(), expr.getColumn());
                        return sig.returnType;
                    }
                    for (int i = 0; i < providedTypes.size(); i++) {
                        String expected = sig.parameterTypes.get(i);
                        String provided = providedTypes.get(i);
                        if (provided != null && !provided.equals("unknown")
                                && !isTypeCompatible(expected, provided)) {
                            ASTNode badArg = argNodes.get(i);
                            ErrorHandler.report(
                                    "Semantic Error: Argument " + (i + 1) + " of '" + methodName
                                            + "' expects " + expected + " but got " + provided + ".",
                                    badArg.getLine(), badArg.getColumn());
                        }
                    }
                    return sig.returnType;
                }

                // Multiple overloads: find best match
                SymbolTable.MethodSignature matched = null;
                int matchCount = 0;
                for (SymbolTable.MethodSignature sig : possibleMethods) {
                    if (sig.parameterTypes.size() != providedTypes.size())
                        continue;
                    boolean ok = true;
                    for (int i = 0; i < providedTypes.size(); i++) {
                        String pt = providedTypes.get(i);
                        if (pt != null && !pt.equals("unknown")
                                && !isTypeCompatible(sig.parameterTypes.get(i), pt)) {
                            ok = false;
                            break;
                        }
                    }
                    if (ok) {
                        matched = sig;
                        matchCount++;
                    }
                }
                if (matchCount == 0) {
                    ErrorHandler.report(
                            "Semantic Error: No suitable method found for '" + methodName
                                    + "' matching arguments.",
                            expr.getLine(), expr.getColumn());
                    return "unknown";
                }
                if (matchCount > 1) {
                    ErrorHandler.report(
                            "Semantic Error: Ambiguous method call for '" + methodName + "'.",
                            expr.getLine(), expr.getColumn());
                    return "unknown";
                }
                if (matched == null) {
                    return "unknown";
                }
                expr.setAttribute("resolved_return_type", matched.returnType);
                return matched.returnType;
            }

            case "TERNARY": {
                // FIX: Guard against missing THEN/ELSE children from error-recovery ASTs.
                List<ASTNode> children = expr.getChildren();
                if (children.size() < 3)
                    return "unknown";

                ASTNode thenWrapper = children.get(1);
                ASTNode elseWrapper = children.get(2);
                if (thenWrapper.getChildren().isEmpty() || elseWrapper.getChildren().isEmpty()) {
                    return "unknown";
                }

                String thenType = inferExpressionType(thenWrapper.getChildren().get(0));
                String elseType = inferExpressionType(elseWrapper.getChildren().get(0));

                if (!isTypeCompatible(thenType, elseType) && !isTypeCompatible(elseType, thenType)) {
                    ErrorHandler.report(
                            "Semantic Error: Incompatible ternary branches ('"
                                    + thenType + "' and '" + elseType + "').",
                            expr.getLine(), expr.getColumn());
                    return "type_error";
                }

                if ("double".equals(thenType) || "double".equals(elseType))
                    return "double";
                if ("float".equals(thenType) || "float".equals(elseType))
                    return "float";
                if ("String".equals(thenType) || "String".equals(elseType))
                    return "String";
                return thenType;
            }

            case "CAST":
                String targetType = value;
                ASTNode castExpr = expr.getChildren().get(0);
                String actualType = inferExpressionType(castExpr);

                // Rule 9: Narrowing/Explicit Cast Requirement
                boolean numericToNumeric = isNumericType(targetType) && isNumericType(actualType);
                boolean compatible = isTypeCompatible(targetType, actualType)
                        || isTypeCompatible(actualType, targetType);

                if (!numericToNumeric && !compatible) {
                    ErrorHandler.report("Semantic Error: Inconvertible types; cannot cast '" +
                            actualType + "' to '" + targetType + "'.",
                            expr.getLine(), expr.getColumn());
                    return "type_error";
                }
                return targetType;

            case "LAMBDA": {
                symbolTable.enterScope();
                varDeclarations.push(new HashMap<>());
                varUsage.push(new HashSet<>());
                lambdaDepth++;

                ASTNode params = null;
                ASTNode body = null;
                for (ASTNode child : expr.getChildren()) {
                    if (child.getType().equals("PARAMS"))
                        params = child;
                    if (child.getType().equals("BODY"))
                        body = child;
                }

                if (params != null) {
                    for (ASTNode param : params.getChildren()) {
                        String pType = param.getChildren().get(0).getValue();
                        String pName = param.getChildren().get(1).getValue();
                        symbolTable.defineVariable(pName, pType);
                        varDeclarations.peek().put(pName, new VarInfo(pType, param.getLine(), param.getColumn()));
                    }
                }

                if (body != null) {
                    analyze(body);
                }

                checkUnusedVariables();
                Set<String> lambdaUsed = new HashSet<>(varUsage.peek());
                varDeclarations.pop();
                varUsage.pop();
                if (!varUsage.isEmpty()) {
                    varUsage.peek().addAll(lambdaUsed);
                }
                lambdaDepth--;
                symbolTable.exitScope();
                return "Lambda";
            }

            case "NEW":
                for (ASTNode child : expr.getChildren()) {
                    if (child.getType().equals("ARGS")) {
                        for (ASTNode arg : child.getChildren()) {
                            inferExpressionType(arg);
                        }
                    }
                }
                return value; // class name

            case "NEW_ARRAY": {
                if (!expr.getChildren().isEmpty()) {
                    ASTNode sizeNode = expr.getChildren().get(0);
                    String sizeType = inferExpressionType(sizeNode);
                    if (sizeType != null && !sizeType.equals("int")
                            && !sizeType.equals("type_error")) {
                        ErrorHandler.report(
                                "Semantic Error: Array size must be int, found " + sizeType + ".",
                                sizeNode.getLine(), sizeNode.getColumn());
                    }
                }
                return value; // e.g. "int[]"
            }

            case "ARRAY_LITERAL": {
                if (expr.getChildren().isEmpty())
                    return "Object[]";
                String elementType = null;
                for (ASTNode element : expr.getChildren()) {
                    String elemType = inferExpressionType(element);

                    // A leading null should not lock the entire array to null[];
                    // instead, use the first concrete element type as the array type.
                    if ("null".equals(elemType)) {
                        continue;
                    }

                    if (elementType == null) {
                        elementType = elemType;
                        continue;
                    }

                    if (!isTypeCompatible(elementType, elemType)) {
                        ErrorHandler.report(
                                "Semantic Error: Inconsistent types in array literal.",
                                element.getLine(), element.getColumn());
                    }
                }
                if (elementType == null) {
                    return "null[]";
                }
                return elementType + "[]";
            }

            case "ARRAY_ACCESS": {
                List<ASTNode> children = expr.getChildren();
                if (children.size() < 2)
                    return "type_error";

                String arrayType = inferExpressionType(children.get(0));
                String indexType = inferExpressionType(children.get(1));

                if (indexType != null && !indexType.equals("int")
                        && !indexType.equals("type_error")) {
                    ErrorHandler.report(
                            "Semantic Error: Array index must be int, found " + indexType + ".",
                            expr.getLine(), expr.getColumn());
                }
                if (arrayType != null && arrayType.endsWith("[]")) {
                    return arrayType.substring(0, arrayType.length() - 2);
                }
                if (arrayType != null && !arrayType.equals("type_error")) {
                    ErrorHandler.report(
                            "Semantic Error: The variable is not an array type.",
                            expr.getLine(), expr.getColumn());
                }
                return "type_error";
            }

            case "FIELD_ACCESS": {
                if (expr.getChildren().isEmpty())
                    return "Object";
                String receiverType = inferExpressionType(expr.getChildren().get(0));
                String fieldName = expr.getValue();
                // Array .length is always int
                if (receiverType != null && receiverType.endsWith("[]")
                        && fieldName.equals("length")) {
                    return "int";
                }
                String fieldType = resolveFieldType(receiverType, fieldName);
                if (fieldType != null) {
                    return fieldType;
                }
                reportUnknownField(expr, receiverType, fieldName);
                return "type_error";
            }

            // ASSIGN used as an expression (e.g. int x = (y = 5)) — validate and return RHS
            // type.
            case "ASSIGN": {
                validateAssignment(expr);
                List<ASTNode> children = expr.getChildren();
                if (children.size() >= 2)
                    return inferExpressionType(children.get(1));
                return "unknown";
            }

            case "ERROR":
                return "unknown";

            default:
                return null;
        }
    }

    // ── L-Value Validation ─────────────────────────────────────────────────────
    private void validateLValue(ASTNode node, String op) {
        if (node == null)
            return;
        String type = node.getType();
        if (!type.equals("IDENTIFIER") && !type.equals("ARRAY_ACCESS") && !type.equals("FIELD_ACCESS")) {
            ErrorHandler.report("Semantic Error: Invalid target for operator '" + op + "'. Variable expected.",
                    node.getLine(), node.getColumn());
        }
    }

    // ── Operator Validation ────────────────────────────────────────────────────
    /**
     * Checks that an operator is legal for its operand types.
     */
    private void validateOperatorTypes(String op, String leftType, String rightType,
            ASTNode leftNode, ASTNode rightNode, int line, int col) {
        switch (op) {
            case "+":
            case "-":
            case "*":
            case "/":
            case "%":
                if (op.equals("+")
                        && ("String".equals(leftType) || "String".equals(rightType))) {
                    return; // String concatenation is always legal
                }
                if (!isNumericType(leftType) || !isNumericType(rightType)) {
                    ErrorHandler.report(
                            "Semantic Error: Operator '" + op + "' cannot be applied to '"
                                    + leftType + "' and '" + rightType + "'.",
                            line, col);
                }
                break;

            case "<":
            case ">":
            case "<=":
            case ">=":
                if (!isNumericType(leftType) || !isNumericType(rightType)) {
                    ErrorHandler.report(
                            "Semantic Error: Comparison '" + op + "' requires numeric operands, got "
                                    + leftType + " and " + rightType + ".",
                            line, col);
                }
                break;

            case "==":
            case "!=":
                if (!isTypeCompatible(leftType, rightType)
                        && !isTypeCompatible(rightType, leftType)) {
                    ErrorHandler.report(
                            "Semantic Error: Cannot compare " + leftType + " with "
                                    + rightType + " using '" + op + "'.",
                            line, col);
                }
                break;

            case "&&":
            case "||":
                if (!leftType.equals("boolean")) {
                    ErrorHandler.report(
                            "Semantic Error: Operator '" + op
                                    + "' requires boolean operands, got " + leftType + ".",
                            line, col);
                }
                if (!rightType.equals("boolean")) {
                    ErrorHandler.report(
                            "Semantic Error: Operator '" + op
                                    + "' requires boolean operands, got " + rightType + ".",
                            line, col);
                }
                break;

            case "&":
            case "|":
            case "^":
            case "<<":
            case ">>":
            case ">>>":
                if (!isIntegralType(leftType) || !isIntegralType(rightType)) {
                    ErrorHandler.report(
                            "Semantic Error: Bitwise operator '" + op
                                    + "' requires integral operands, got "
                                    + leftType + " and " + rightType + ".",
                            line, col);
                }
                break;
        }
    }

    // ── Type Helpers ───────────────────────────────────────────────────────────

    /**
     * Returns true if the type can participate in arithmetic.
     * FIX: Added 'char' — Java allows arithmetic on char (it's a 16-bit integer).
     */
    private boolean isNumericType(String type) {
        switch (type) {
            case "int":
            case "double":
            case "float":
            case "long":
            case "byte":
            case "short":
            case "char":
                return true;
            default:
                return false;
        }
    }

    /**
     * Returns true if the type is integral (valid for bitwise operators).
     */
    private boolean isIntegralType(String type) {
        switch (type) {
            case "int":
            case "long":
            case "byte":
            case "short":
            case "char":
                return true;
            default:
                return false;
        }
    }

    /**
     * Returns true if actualType can be safely used where expectedType is required.
     * Handles: exact match, null assignment to reference types, numeric widening.
     * FIX: Uses "null" sentinel instead of "Object" for the null literal, so
     * null is compatible with reference types but not with primitives.
     */
    private boolean isTypeCompatible(String expectedType, String actualType) {
        if (expectedType == null || actualType == null)
            return false;
        if (expectedType.equals(actualType))
            return true;

        // null is compatible with any reference type (non-primitive)
        if ("null".equals(actualType)) {
            boolean isPrimitive = isNumericType(expectedType)
                    || expectedType.equals("boolean");
            return !isPrimitive;
        }

        // Arrays: only exact match (no covariance for this subset checker)
        if (expectedType.endsWith("[]") || actualType.endsWith("[]")) {
            if ("null[]".equals(actualType)) {
                String componentType = expectedType.endsWith("[]")
                        ? expectedType.substring(0, expectedType.length() - 2)
                        : expectedType;
                return expectedType.endsWith("[]") && !isNumericType(componentType)
                        && !"boolean".equals(componentType);
            }
            return expectedType.equals(actualType);
        }

        // Numeric widening: byte < short < int < long < float < double
        List<String> hierarchy = java.util.Arrays.asList(
                "byte", "short", "int", "long", "float", "double");
        int ei = hierarchy.indexOf(expectedType);
        int ai = hierarchy.indexOf(actualType);
        if (ei != -1 && ai != -1) {
            return ai <= ei;
        }

        return false;
    }

    // ── Block & Children ───────────────────────────────────────────────────────

    private void analyzeChildren(ASTNode node) {
        for (ASTNode child : node.getChildren()) {
            analyze(child);
        }
    }

    /**
     * Traverses a block, enforcing dead-code detection after return/break/continue.
     * Saves and restores isReachable so an inner block's terminal statement does
     * not
     * incorrectly mark the outer block's subsequent statements as unreachable.
     */
    private void analyzeBlock(ASTNode node) {
        boolean savedReachable = isReachable;
        symbolTable.enterScope();

        for (ASTNode child : node.getChildren()) {
            if (!isReachable) {
                ErrorHandler.report("Semantic Error: Unreachable statement.",
                        child.getLine(), child.getColumn());
                break; // Report only once per block
            }
            analyze(child);
            String childType = child.getType();
            if (childType.equals("RETURN") || childType.equals("BREAK")
                    || childType.equals("CONTINUE") || childType.equals("THROW")) {
                isReachable = false;
            }
        }

        symbolTable.exitScope();
        isReachable = savedReachable; // Restore for outer block
    }

    /**
     * Checks the current scope for declared variables that were never used.
     * Emits warnings via ErrorHandler for each unused variable.
     */
    private void checkUnusedVariables() {
        if (varDeclarations.isEmpty() || varUsage.isEmpty()) {
            return;
        }

        Map<String, VarInfo> declarations = varDeclarations.peek();
        Set<String> used = varUsage.peek();

        for (Map.Entry<String, VarInfo> entry : declarations.entrySet()) {
            String varName = entry.getKey();
            if (!used.contains(varName)) {
                VarInfo info = entry.getValue();
                ErrorHandler.reportWarning(
                        "Warning: Variable '" + varName + "' is declared but never used.",
                        info.line, info.column);
            }
        }
    }

    /**
     * Enforces that IF/WHILE/FOR conditions evaluate to boolean.
     * Also warns about suspicious assignments in conditions (e.g., if (x = 5)
     * instead of if (x == 5)).
     * FIX: Now uses the CONDITION node's own coordinates instead of -1, -1.
     */
    private void validateBooleanCondition(ASTNode loopOrIfNode) {
        for (ASTNode child : loopOrIfNode.getChildren()) {
            if (child.getType().equals("CONDITION")) {
                if (child.getChildren().isEmpty())
                    break;
                ASTNode expression = child.getChildren().get(0);

                // Check for suspicious assignment in condition
                if (expression.getType().equals("ASSIGN")) {
                    ErrorHandler.reportWarning(
                            "Warning: Assignment in condition. Did you mean to use == instead of =?",
                            expression.getLine(), expression.getColumn());
                }

                if (isConstantCondition(expression)) {
                    ErrorHandler.reportWarning(
                            "Warning: Condition is constant and may make the branch or loop unnecessary.",
                            expression.getLine(), expression.getColumn());
                }

                String condType = inferExpressionType(expression);
                if (!"boolean".equals(condType) && !"unknown".equals(condType)
                        && !"type_error".equals(condType)) {
                    ErrorHandler.report(
                            "Semantic Error: Condition must be boolean, found " + condType + ".",
                            child.getLine(), child.getColumn()); // FIX: real coordinates
                }
                break;
            }
        }
    }

    private boolean allPathsReturn(ASTNode node) {
        if (node == null)
            return false;
        String type = node.getType();

        switch (type) {
            case "RETURN":
            case "THROW":
                return true;
            case "BLOCK":
            case "BODY":
                for (ASTNode child : node.getChildren()) {
                    if (allPathsReturn(child))
                        return true;
                }
                return false;
            case "IF_STMT":
                // Both branches must return for the IF to guarantee a return
                ASTNode thenBranch = null, elseBranch = null;
                for (ASTNode child : node.getChildren()) {
                    if (child.getType().equals("THEN"))
                        thenBranch = child;
                    if (child.getType().equals("ELSE"))
                        elseBranch = child;
                }
                return allPathsReturn(thenBranch) && allPathsReturn(elseBranch);
            default:
                return false;
        }
    }

    /**
     * Determines if an expression is useless (has no side effects).
     * Expressions like literals, variables, and arithmetic without assignment
     * are considered useless when used as standalone statements.
     */
    private boolean isUselessExpression(ASTNode expr) {
        if (expr == null)
            return false;

        String type = expr.getType();

        // These have side effects or are not useless:
        if (type.equals("ASSIGN") || type.equals("METHOD_CALL") || type.equals("PRINT_STMT")
                || type.equals("PREFIX_OP") || type.equals("POSTFIX_OP")) {
            return false;
        }

        // Literals and identifiers alone are useless (except in specific contexts)
        if (type.equals("LITERAL") || type.equals("STRING_LITERAL") || type.equals("IDENTIFIER")
                || type.equals("BINARY_OP") || type.equals("UNARY_OP") || type.equals("TERNARY")
                || type.equals("ARRAY_ACCESS")) {
            return true;
        }

        return false;
    }

    private boolean isConstantCondition(ASTNode expr) {
        if (expr == null) {
            return false;
        }

        String type = expr.getType();
        String value = expr.getValue();

        if ("LITERAL".equals(type) || "BOOLEAN_LITERAL".equals(type)) {
            return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)
                    || value != null && value.matches("-?\\d+(\\.\\d+)?");
        }

        if ("UNARY_OP".equals(type) || "BINARY_OP".equals(type)) {
            return expr.getChildren().stream().allMatch(this::isConstantCondition);
        }

        return false;
    }

    private boolean isOverbroadExceptionType(String type) {
        return "Exception".equals(type) || "Throwable".equals(type) || "RuntimeException".equals(type);
    }

    private String resolveFieldAccessType(ASTNode fieldAccess) {
        if (fieldAccess == null || fieldAccess.getChildren().isEmpty()) {
            return null;
        }
        String receiverType = inferExpressionType(fieldAccess.getChildren().get(0));
        return resolveFieldType(receiverType, fieldAccess.getValue());
    }

    private String resolveFieldType(String receiverType, String fieldName) {
        if (receiverType == null || fieldName == null) {
            return null;
        }
        if (receiverType.endsWith("[]") && "length".equals(fieldName)) {
            return "int";
        }
        Map<String, String> fields = classFieldTypes.get(receiverType);
        if (fields != null && fields.containsKey(fieldName)) {
            return fields.get(fieldName);
        }
        return null;
    }

    private boolean reportUnknownField(ASTNode fieldAccess, String receiverType, String fieldName) {
        if (fieldAccess == null || receiverType == null || fieldName == null) {
            return false;
        }
        if (receiverType.endsWith("[]") && "length".equals(fieldName)) {
            return false;
        }
        ErrorHandler.report(
                "Semantic Error: Field '" + fieldName + "' not found on type '" + receiverType + "'.",
                fieldAccess.getLine(), fieldAccess.getColumn());
        return true;
    }

    /**
     * Returns true for exception-like types that this compiler routes dynamically.
     * This keeps the semantic checker from enforcing Java checked-exception rules
     * on runtime-routed throws such as `throw new Exception(...)`.
     */
    private boolean isExceptionLikeType(String type) {
        if (type == null || type.isBlank()) {
            return false;
        }
        if (isOverbroadExceptionType(type)) {
            return true;
        }
        return type.endsWith("Exception") || type.endsWith("Error") || type.endsWith("Throwable");
    }

    private boolean isEmptyCatchBody(ASTNode catchBody) {
        if (catchBody == null) {
            return true;
        }

        if (catchBody.getChildren().isEmpty()) {
            return true;
        }

        ASTNode inner = catchBody.getChildren().get(0);
        if (inner == null) {
            return true;
        }

        if ("BLOCK".equals(inner.getType()) || "BODY".equals(inner.getType())) {
            return inner.getChildren().isEmpty();
        }

        return false;
    }

    private boolean isBooleanLiteralNode(ASTNode node) {
        if (node == null) {
            return false;
        }

        String type = node.getType();
        String value = node.getValue();
        return ("LITERAL".equals(type) || "BOOLEAN_LITERAL".equals(type))
                && ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value));
    }

    private boolean isNullLiteralNode(ASTNode node) {
        if (node == null) {
            return false;
        }

        String type = node.getType();
        String value = node.getValue();
        return ("LITERAL".equals(type) || "NULL_LITERAL".equals(type))
                && "null".equalsIgnoreCase(value);
    }

    private boolean isSelfAssignment(ASTNode lhs, ASTNode rhs) {
        if (lhs == null || rhs == null) {
            return false;
        }

        if (!"IDENTIFIER".equals(lhs.getType()) || !"IDENTIFIER".equals(rhs.getType())) {
            return false;
        }

        return lhs.getValue() != null && lhs.getValue().equals(rhs.getValue());
    }

    private boolean isSameExpression(ASTNode leftNode, ASTNode rightNode) {
        if (leftNode == null || rightNode == null) {
            return false;
        }

        if (!leftNode.getType().equals(rightNode.getType())) {
            return false;
        }

        String leftValue = leftNode.getValue();
        String rightValue = rightNode.getValue();
        if (leftValue == null ? rightValue != null : !leftValue.equals(rightValue)) {
            return false;
        }

        List<ASTNode> leftChildren = leftNode.getChildren();
        List<ASTNode> rightChildren = rightNode.getChildren();
        if (leftChildren.size() != rightChildren.size()) {
            return false;
        }

        for (int i = 0; i < leftChildren.size(); i++) {
            if (!isSameExpression(leftChildren.get(i), rightChildren.get(i))) {
                return false;
            }
        }

        return true;
    }

    private boolean isFallthroughTerminator(ASTNode stmt) {
        if (stmt == null) {
            return false;
        }

        String type = stmt.getType();
        return "BREAK".equals(type) || "RETURN".equals(type) || "THROW".equals(type)
                || "CONTINUE".equals(type);
    }

    private int getFallbackLine(ASTNode expr, ASTNode leftNode, ASTNode rightNode) {
        if (expr != null && expr.getLine() > 0) {
            return expr.getLine();
        }
        if (leftNode != null && leftNode.getLine() > 0) {
            return leftNode.getLine();
        }
        if (rightNode != null && rightNode.getLine() > 0) {
            return rightNode.getLine();
        }
        return -1;
    }

    private int getFallbackColumn(ASTNode expr, ASTNode leftNode, ASTNode rightNode) {
        if (expr != null && expr.getColumn() > 0) {
            return expr.getColumn();
        }
        if (leftNode != null && leftNode.getColumn() > 0) {
            return leftNode.getColumn();
        }
        if (rightNode != null && rightNode.getColumn() > 0) {
            return rightNode.getColumn();
        }
        return -1;
    }
}