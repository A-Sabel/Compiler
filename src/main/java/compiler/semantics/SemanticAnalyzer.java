package compiler.semantics;

import java.util.List;

import compiler.parser.ast.ASTNode;
import compiler.util.ErrorHandler;

public class SemanticAnalyzer {
    private final SymbolTable symbolTable;

    private String currentMethodReturnType = null;

    private int loopDepth = 0;
    private boolean isReachable = true;

    public SemanticAnalyzer() {
        this.symbolTable = new SymbolTable();
    }

    public void analyze(ASTNode node) {
        if (node == null) return;

        String nodeType = node.getType();

        switch (nodeType) {
            case "PROGRAM":
                analyzeChildren(node);
                break;

            case "CLASS_DECL":
                symbolTable.enterScope();
                analyzeChildren(node);
                symbolTable.exitScope();
                break;

            case "METHOD_DECL":
                validateMethodDeclaration(node);
                break;

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
                    analyze(node.getChildren().get(0));
                }
                break;

            case "METHOD_CALL":
                inferExpressionType(node);
                break;

            // FIX: Removed the redundant validateBinaryOperation() call path.
            // inferExpressionType() already handles operator validation and division-by-zero
            // checks. Calling validateBinaryOperation() + analyzeChildren() on top caused
            // every binary-op error to be reported 2-3x. Now we just type-check the node.
            case "BINARY_OP":
                inferExpressionType(node);
                break;

            case "RETURN":
                validateReturnStatement(node);
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
                symbolTable.enterScope();
                loopDepth++;
                analyzeChildren(node);
                loopDepth--;
                symbolTable.exitScope();
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
                // Increment loopDepth so 'break' inside switch is considered legal.
                loopDepth++;
                analyzeChildren(node);
                loopDepth--;
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

        // Initializer type check
        if (children.size() >= 3) {
            ASTNode initWrapper = children.get(2);
            ASTNode initExpr = initWrapper.getChildren().isEmpty()
                ? initWrapper
                : initWrapper.getChildren().get(0);

            String initType = inferExpressionType(initExpr);

            if (initType != null && !initType.equals("type_error")
                    && !isTypeCompatible(type, initType)) {
                ErrorHandler.report(
                    "Semantic Error: Type mismatch in initialization. Variable '" + name
                    + "' is " + type + " but initialized with " + initType + ".",
                    initExpr.getLine(), initExpr.getColumn());
            }
        }

        symbolTable.defineVariable(name, type);
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
        ASTNode bodyNode   = null;

        for (ASTNode child : node.getChildren()) {
            switch (child.getType()) {
                case "RETURN_TYPE": returnType = child.getValue(); break;
                case "PARAMS":      paramsNode = child;            break;
                case "BODY":        bodyNode   = child;            break;
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

        // Set up scope and analyze body
        currentMethodReturnType = returnType;
        symbolTable.enterScope();

        for (int i = 0; i < paramNames.size(); i++) {
            symbolTable.defineVariable(paramNames.get(i), paramTypes.get(i));
        }

        if (bodyNode != null) {
            analyze(bodyNode);
        }

        symbolTable.exitScope();
        currentMethodReturnType = null;

        analyze(bodyNode); // existing walk

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
        if (children.size() < 2) return;

        ASTNode lhs = children.get(0);
        ASTNode rhs = children.get(1);

        // 1. Resolve the base variable name and validate the target type
        String varName;
        String lhsType = lhs.getType();

        if (lhsType.equals("ARRAY_ACCESS")) {
            // For array access, the name is in the first child (the identifier)
            varName = lhs.getChildren().get(0).getValue(); 
        } else if (lhsType.equals("IDENTIFIER") || lhsType.equals("FIELD_ACCESS")) {
            varName = lhs.getValue();
        } else {
            ErrorHandler.report("Semantic Error: Invalid assignment target.", node.getLine(), node.getColumn());
            return;
        }

        // 2. Look up the variable in the Symbol Table
        String baseVarType = symbolTable.lookupVariableType(varName);
        if (baseVarType == null) {
            ErrorHandler.report("Semantic Error: Variable '" + varName + "' used in assignment before declaration.", 
                                node.getLine(), node.getColumn());
            return;
        }

        // 3. Determine the exact expected type on the Left-Hand Side
        String expectedType = baseVarType;
        
        if (lhsType.equals("ARRAY_ACCESS")) {
            // If assigning to cars[1], we expect String, not String[]
            if (baseVarType.endsWith("[]")) {
                expectedType = baseVarType.substring(0, baseVarType.length() - 2);
            }
        } else if (lhsType.equals("FIELD_ACCESS") && lhs.getValue().equals("length")) {
            // Prevent assigning to read-only .length property
            ErrorHandler.report("Semantic Error: Cannot assign value to read-only property 'length'.", 
                                lhs.getLine(), lhs.getColumn());
            return;
        }

        // 4. Validate RHS compatibility
        String rhsType = inferExpressionType(rhs);
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
        if (expr == null) return "void";

        String type  = expr.getType();
        String value = expr.getValue();

        switch (type) {
            case "NUMBER":
                // Rule 12: Integer Overflow Prevention
                if (!value.contains(".") && !value.contains("e") && !value.contains("E")) {
                    try {
                        long val = Long.parseLong(value);
                        if (val > Integer.MAX_VALUE || val < Integer.MIN_VALUE) {
                            ErrorHandler.report("Semantic Error: Integer number too large: " + value, 
                                                expr.getLine(), expr.getColumn());
                        }
                    } catch (NumberFormatException e) {
                        // This handles values even larger than a Long can hold
                        ErrorHandler.report("Semantic Error: Integer number too large: " + value, 
                                            expr.getLine(), expr.getColumn());
                    }
                    return "int";
                }
                return "double";

            case "LITERAL":
                if (value.startsWith("\""))                      return "String";
                if (value.startsWith("'"))                       return "char";
                if (value.equals("true") || value.equals("false")) return "boolean";
                if (value.equals("null"))                        return "null";
                return "String";

            case "IDENTIFIER": {
                String declaredType = symbolTable.lookupVariableType(value);
                if (declaredType == null) {
                    ErrorHandler.report(
                        "Semantic Error: Identifier '" + value + "' used before declaration.",
                        expr.getLine(), expr.getColumn());
                    return "unknown";
                }
                return declaredType;
            }

            case "BINARY_OP": {
                String op = value;
                List<ASTNode> children = expr.getChildren();
                if (children.size() < 2) return "unknown";

                ASTNode leftNode  = children.get(0);
                ASTNode rightNode = children.get(1);
                String leftType  = inferExpressionType(leftNode);
                String rightType = inferExpressionType(rightNode);

                // Division/modulo by literal zero
                if ((op.equals("/") || op.equals("%"))
                        && rightNode.getType().equals("NUMBER")
                        && (rightNode.getValue().equals("0") || rightNode.getValue().equals("0.0"))) {
                    ErrorHandler.report(
                        "Semantic Error: Arithmetic Exception: / by zero.",
                        rightNode.getLine(), rightNode.getColumn());
                }

                if (leftType != null && rightType != null) {
                    validateOperatorTypes(op, leftType, rightType, expr.getLine(), expr.getColumn());
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
                if ("double".equals(leftType) || "double".equals(rightType)) return "double";
                if ("float".equals(leftType)  || "float".equals(rightType))  return "float";
                if ("long".equals(leftType)   || "long".equals(rightType))   return "long";
                return "int";
            }

            case "UNARY_OP":
                if (value.equals("!")) return "boolean";
                if (!expr.getChildren().isEmpty()) {
                    return inferExpressionType(expr.getChildren().get(0));
                }
                return "int";

            case "POSTFIX_OP":
                if (!expr.getChildren().isEmpty()) {
                    return inferExpressionType(expr.getChildren().get(0));
                }
                return "int";

            case "METHOD_CALL": {
                String methodName = value;
                List<SymbolTable.MethodSignature> possibleMethods = symbolTable.lookupMethods(methodName);

                if (possibleMethods == null || possibleMethods.isEmpty()) {
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
                    if (sig.parameterTypes.size() != providedTypes.size()) continue;
                    boolean ok = true;
                    for (int i = 0; i < providedTypes.size(); i++) {
                        String pt = providedTypes.get(i);
                        if (pt != null && !pt.equals("unknown")
                                && !isTypeCompatible(sig.parameterTypes.get(i), pt)) {
                            ok = false; break;
                        }
                    }
                    if (ok) { matched = sig; matchCount++; }
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
                return matched.returnType;
            }

            case "TERNARY": {
                // FIX: Guard against missing THEN/ELSE children from error-recovery ASTs.
                List<ASTNode> children = expr.getChildren();
                if (children.size() < 3) return "unknown";

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

                if ("double".equals(thenType) || "double".equals(elseType)) return "double";
                if ("float".equals(thenType)  || "float".equals(elseType))  return "float";
                if ("String".equals(thenType) || "String".equals(elseType)) return "String";
                return thenType;
            }

            case "CAST":
                String targetType = value;
                ASTNode castExpr = expr.getChildren().get(0);
                String actualType = inferExpressionType(castExpr);

                // Rule 9: Narrowing/Explicit Cast Requirement
                boolean numericToNumeric = isNumericType(targetType) && isNumericType(actualType);
                boolean compatible = isTypeCompatible(targetType, actualType) || isTypeCompatible(actualType, targetType);

                if (!numericToNumeric && !compatible) {
                    ErrorHandler.report("Semantic Error: Inconvertible types; cannot cast '" + 
                                        actualType + "' to '" + targetType + "'.", 
                                        expr.getLine(), expr.getColumn());
                    return "type_error";
                }
                return targetType;

            case "NEW":
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
                if (expr.getChildren().isEmpty()) return "Object[]";
                String firstElem = inferExpressionType(expr.getChildren().get(0));
                for (ASTNode element : expr.getChildren()) {
                    String elemType = inferExpressionType(element);
                    if (!isTypeCompatible(firstElem, elemType)) {
                        ErrorHandler.report(
                            "Semantic Error: Inconsistent types in array literal.",
                            element.getLine(), element.getColumn());
                    }
                }
                return firstElem + "[]";
            }

            case "ARRAY_ACCESS": {
                List<ASTNode> children = expr.getChildren();
                if (children.size() < 2) return "type_error";

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
                if (expr.getChildren().isEmpty()) return "Object";
                String receiverType = inferExpressionType(expr.getChildren().get(0));
                String fieldName = expr.getValue();
                // Array .length is always int
                if (receiverType != null && receiverType.endsWith("[]")
                        && fieldName.equals("length")) {
                    return "int";
                }
                return "Object";
            }

            // ASSIGN used as an expression (e.g. int x = (y = 5)) — validate and return RHS type.
            case "ASSIGN": {
                validateAssignment(expr);
                List<ASTNode> children = expr.getChildren();
                if (children.size() >= 2) return inferExpressionType(children.get(1));
                return "unknown";
            }

            case "ERROR":
                return "unknown";

            default:
                return null;
        }
    }

    // ── Operator Validation ────────────────────────────────────────────────────
    /**
     * Checks that an operator is legal for its operand types.
     */
    private void validateOperatorTypes(String op, String leftType, String rightType,
                                       int line, int col) {
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
                        + leftType + "' and '" + rightType + "'.", line, col);
                }
                break;

            case "<": case ">": case "<=": case ">=":
                if (!isNumericType(leftType) || !isNumericType(rightType)) {
                    ErrorHandler.report(
                        "Semantic Error: Comparison '" + op + "' requires numeric operands, got "
                        + leftType + " and " + rightType + ".", line, col);
                }
                break;

            case "==": case "!=":
                if (!isTypeCompatible(leftType, rightType)
                        && !isTypeCompatible(rightType, leftType)) {
                    ErrorHandler.report(
                        "Semantic Error: Cannot compare " + leftType + " with "
                        + rightType + " using '" + op + "'.", line, col);
                }
                break;

            case "&&": case "||":
                if (!leftType.equals("boolean")) {
                    ErrorHandler.report(
                        "Semantic Error: Operator '" + op
                        + "' requires boolean operands, got " + leftType + ".", line, col);
                }
                if (!rightType.equals("boolean")) {
                    ErrorHandler.report(
                        "Semantic Error: Operator '" + op
                        + "' requires boolean operands, got " + rightType + ".", line, col);
                }
                break;

            case "&": case "|": case "^": case "<<": case ">>": case ">>>":
                if (!isIntegralType(leftType) || !isIntegralType(rightType)) {
                    ErrorHandler.report(
                        "Semantic Error: Bitwise operator '" + op
                        + "' requires integral operands, got "
                        + leftType + " and " + rightType + ".", line, col);
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
            case "int": case "double": case "float":
            case "long": case "byte": case "short": case "char":
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
            case "int": case "long": case "byte": case "short": case "char":
                return true;
            default:
                return false;
        }
    }

    /**
     * Returns true if actualType can be safely used where expectedType is required.
     * Handles: exact match, null assignment to reference types, numeric widening.
     * FIX: Uses "null" sentinel instead of "Object" for the null literal, so
     *      null is compatible with reference types but not with primitives.
     */
    private boolean isTypeCompatible(String expectedType, String actualType) {
        if (expectedType == null || actualType == null) return false;
        if (expectedType.equals(actualType)) return true;

        // null is compatible with any reference type (non-primitive)
        if ("null".equals(actualType)) {
            boolean isPrimitive = isNumericType(expectedType)
                || expectedType.equals("boolean");
            return !isPrimitive;
        }

        // Arrays: only exact match (no covariance for this subset checker)
        if (expectedType.endsWith("[]") || actualType.endsWith("[]")) {
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
     * Saves and restores isReachable so an inner block's terminal statement does not
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
                    || childType.equals("CONTINUE")) {
                isReachable = false;
            }
        }

        symbolTable.exitScope();
        isReachable = savedReachable; // Restore for outer block
    }

    /**
     * Enforces that IF/WHILE/FOR conditions evaluate to boolean.
     * FIX: Now uses the CONDITION node's own coordinates instead of -1, -1.
     */
    private void validateBooleanCondition(ASTNode loopOrIfNode) {
        for (ASTNode child : loopOrIfNode.getChildren()) {
            if (child.getType().equals("CONDITION")) {
                if (child.getChildren().isEmpty()) break;
                ASTNode expression = child.getChildren().get(0);
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
    if (node == null) return false;
    String type = node.getType();

    switch (type) {
        case "RETURN": return true;
        case "BLOCK":
        case "BODY":
            for (ASTNode child : node.getChildren()) {
                if (allPathsReturn(child)) return true;
            }
            return false;
        case "IF_STMT":
            // Both branches must return for the IF to guarantee a return
            ASTNode thenBranch = null, elseBranch = null;
            for (ASTNode child : node.getChildren()) {
                if (child.getType().equals("THEN")) thenBranch = child;
                if (child.getType().equals("ELSE")) elseBranch = child;
            }
            return allPathsReturn(thenBranch) && allPathsReturn(elseBranch);
        default: return false;
    }
}
}