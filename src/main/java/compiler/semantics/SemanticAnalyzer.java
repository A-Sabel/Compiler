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
            case "METHOD_DECL":
                validateMethodDeclaration(node);
                break;
            case "BLOCK":
                analyzeBlock(node);
                break;
            case "VAR_DECL":
                validateVariableDeclaration(node);
                break;
            case "ASSIGN":
                validateAssignment(node);
                break;
            case "EXPRESSION_STMT":
                if (!node.getChildren().isEmpty()) {
                    inferExpressionType(node.getChildren().get(0)); 
                }
                break;
            case "METHOD_CALL":
                inferExpressionType(node);
                break;
            case "BINARY_OP":
                validateBinaryOperation(node);
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
            case "BREAK":
            case "CONTINUE":
                if (loopDepth <= 0) {
                    ErrorHandler.report(
                        "Semantic Error: '" + nodeType.toLowerCase() + "' statement used outside of a loop.",
                        node.getLine(),
                        node.getColumn());
                }
                break;
            case "SWITCH":
                // Switch isn't a loop, but 'break' is legal inside it. 
                // A strict implementation tracks switch depth separately, 
                // but for this subset, incrementing loopDepth temporarily works to allow breaks.
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

    /**
     * Validates variable declaration and initialization.
     * Structure: VAR_DECL has children [TYPE, NAME, optional INIT_VALUE]
     */
    private void validateVariableDeclaration(ASTNode node) {
        List<ASTNode> children = node.getChildren();
        
        // 1. Structural Check
        if (children.size() < 2) {
            ErrorHandler.report("Semantic Error: Invalid variable declaration structure", 
                                node.getLine(), node.getColumn());
            return;
        }

        ASTNode typeNode = children.get(0);
        ASTNode nameNode = children.get(1);
        String type = typeNode.getValue();
        String name = nameNode.getValue();

        // 2. Duplicate Definition Check
        if (symbolTable.lookupVariableType(name) != null) {
            ErrorHandler.report("Semantic Error: Variable '" + name + "' is already defined in this scope.", 
                                nameNode.getLine(), nameNode.getColumn());
            return;
        }

        // 3. Initializer Logic
        if (children.size() >= 3) {
            ASTNode initWrapper = children.get(2);
            ASTNode initExpr = initWrapper.getChildren().isEmpty() ? initWrapper : initWrapper.getChildren().get(0);
            
            // This single line does ALL the recursive heavy lifting for the right side!
            String initType = inferExpressionType(initExpr);

            // Sentinel Type Strategy
            if (initType != null && !initType.equals("type_error") && !isTypeCompatible(type, initType)) {
                ErrorHandler.report(
                    "Semantic Error: Type mismatch in initialization. Variable '" + name + 
                    "' is " + type + " but initialized with " + initType + ".", 
                    initExpr.getLine(), initExpr.getColumn()); 
            }
        }

        // 4. Register and Finish 
        symbolTable.defineVariable(name, type);
        
        // REMOVED: analyzeChildren(node); 
        // We do not want to re-analyze the initializer and trigger duplicate errors!
    }

    /**
     * Validates method declarations and sets up parameter scoping.
     * Structure: METHOD_DECL(name) -> [MODIFIERS?, RETURN_TYPE, PARAMS, BODY]
     */
    private void validateMethodDeclaration(ASTNode node) {
        String methodName = node.getValue();
        
        String returnType = "void";
        ASTNode paramsNode = null;
        ASTNode bodyNode = null;

        // Safely locate children regardless of whether MODIFIERS exist
        for (ASTNode child : node.getChildren()) {
            if (child.getType().equals("RETURN_TYPE")) returnType = child.getValue();
            else if (child.getType().equals("PARAMS")) paramsNode = child;
            else if (child.getType().equals("BODY")) bodyNode = child;
        }
        
        List<String> paramTypes = new java.util.ArrayList<>();
        List<String> paramNames = new java.util.ArrayList<>();
        
        // Extract parameters safely by targeting the exact child nodes
        if (paramsNode != null) {
            for (ASTNode paramNode : paramsNode.getChildren()) {
                // The PARAM node has two children: TYPE and NAME
                if (paramNode.getChildren().size() >= 2) {
                    paramTypes.add(paramNode.getChildren().get(0).getValue()); 
                    paramNames.add(paramNode.getChildren().get(1).getValue()); 
                }
            }
        }
        
        // 1. Definition check (Check for exact duplicates)
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
            ErrorHandler.report("Semantic Error: Duplicate method signature for '" + methodName + "'.", -1, -1);
        } else {
            symbolTable.defineMethod(methodName, returnType, paramTypes);
        }
        
        // 2. Scope setup for the method body
        currentMethodReturnType = returnType;
        symbolTable.enterScope();
        
        // Define parameters as local variables inside the method's scope
        for (int i = 0; i < paramNames.size(); i++) {
            symbolTable.defineVariable(paramNames.get(i), paramTypes.get(i));
        }
        
        // 3. Analyze the actual code inside the method
        if (bodyNode != null) {
            analyze(bodyNode);
        }
        
        symbolTable.exitScope();
        currentMethodReturnType = null; // Leaving the method
    }

    /**
     * Validates that return statements match the method's declared return type.
     */
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
        } 
        else if (!currentMethodReturnType.equals("void") && !hasReturnValue) {
            ErrorHandler.report("Semantic Error: Missing return value for method expecting '" + currentMethodReturnType + "'.", 
                                node.getLine(), node.getColumn());
        }
        else if (hasReturnValue) {
            String actualType = inferExpressionType(node.getChildren().get(0));
            if (!isTypeCompatible(currentMethodReturnType, actualType)) {
                // target the coordinates of the RETURN node
                ErrorHandler.report("Semantic Error: Incompatible return type. Expected '" + currentMethodReturnType + "' but got '" + actualType + "'.", 
                                    node.getLine(), node.getColumn());
            }
        }
    }

    /**
     * Validates assignment operations.
     * Structure: ASSIGN has children [left_side, right_side]
     */
    private void validateAssignment(ASTNode node) {
        List<ASTNode> children = node.getChildren();
        if (children.size() < 2) {
            return; // Exit cleanly
        }

        ASTNode lhs = children.get(0);
        ASTNode rhs = children.get(1);

        // Rule E.1: LHS must be a valid assignment target (L-Value)
        String lhsTypeNode = lhs.getType();
        if (!lhsTypeNode.equals("IDENTIFIER") && !lhsTypeNode.equals("FIELD_ACCESS") && !lhsTypeNode.equals("ARRAY_ACCESS")) {
            ErrorHandler.report("Semantic Error: Invalid assignment target.", node.getLine(), node.getColumn());
            return;
        }

        String varName = lhs.getValue();
        String varType = symbolTable.lookupVariableType(varName);

        if (varType == null) {
            ErrorHandler.report(
                "Semantic Error: Variable '" + varName + "' used in assignment before declaration.", 
                node.getLine(), node.getColumn());
            return;
        }

        // Check RHS type compatibility
        // This single line recursively checks the entire right side of the equals sign!
        String rhsType = inferExpressionType(rhs);
        
        if (rhsType != null && !rhsType.equals("type_error") && !isTypeCompatible(varType, rhsType)) {
            ErrorHandler.report(
                "Semantic Error: Cannot assign " + rhsType + " to " + varType + ".", 
                node.getLine(), node.getColumn());
        }

        // REMOVED: analyzeChildren(node);
        // We let inferExpressionType handle the validation of the RHS to avoid spam.
    }

    /**
     * Validates binary operations for type compatibility.
     * Structure: BINARY_OP has children [left_operand, right_operand]
     */
    private void validateBinaryOperation(ASTNode node) {
        List<ASTNode> children = node.getChildren();
        if (children.size() < 2) {
            analyzeChildren(node);
            return;
        }

        String op = node.getValue(); // Defined here for use in the check below
        ASTNode left = children.get(0);
        ASTNode right = children.get(1); // Defined here

        String leftType = inferExpressionType(left);
        String rightType = inferExpressionType(right);

        boolean isZeroLiteral = right.getType().equals("NUMBER") && 
                                (right.getValue().equals("0") || right.getValue().equals("0.0"));

        // Rule 13: Division/modulo by literal zero
        if ((op.equals("/") || op.equals("%")) && isZeroLiteral) {
            // We use right.getLine() and right.getColumn() to target the '0' precisely
            ErrorHandler.report("Semantic Error: Arithmetic Exception: / by zero.", 
                                right.getLine(), right.getColumn());
        }

        if (leftType != null && rightType != null) {
            validateOperatorTypes(op, leftType, rightType, node.getLine(), node.getColumn());
        }
        analyzeChildren(node);
    }

    /**
     * Validates that an operator is legal for its operand types.
     */
    /**
     * Validates that an operator is legal for its operand types.
     */
    private void validateOperatorTypes(String op, String leftType, String rightType, int line, int col) {
        switch (op) {
            // Arithmetic operators: require numeric types
            case "+":
            case "-":
            case "*":
            case "/":
            case "%":
                // 1. Check for valid String concatenation first
                if (op.equals("+") && (leftType.equals("String") || rightType.equals("String"))) {
                    return; // Legal: String + anything
                }
                
                // 2. If it's not string concatenation, BOTH operands strictly must be numeric
                if (!isNumericType(leftType) || !isNumericType(rightType)) {
                    ErrorHandler.report(
                        "Semantic Error: Operator '" + op + "' cannot be applied to '" + 
                        leftType + "' and '" + rightType + "'.", line, col);
                }
                break;

            // Comparison operators: require compatible types
            case "<":
            case ">":
            case "<=":
            case ">=":
                if (!isNumericType(leftType) || !isNumericType(rightType)) {
                    ErrorHandler.report(
                        "Semantic Error: Comparison '" + op + "' requires numeric operands, got " + 
                        leftType + " and " + rightType, line, col);
                }
                break;

            // Equality operators: any type but must be compatible
            case "==":
            case "!=":
                if (!isTypeCompatible(leftType, rightType)) {
                    ErrorHandler.report(
                        "Semantic Error: Cannot compare " + leftType + " with " + rightType + 
                        " using '" + op + "'", line, col);
                }
                break;

            // Logical operators: require boolean types
            case "&&":
            case "||":
                if (!leftType.equals("boolean")) {
                    ErrorHandler.report(
                        "Semantic Error: Operator '" + op + "' requires boolean operands, got " + leftType, line, col);
                }
                if (!rightType.equals("boolean")) {
                    ErrorHandler.report(
                        "Semantic Error: Operator '" + op + "' requires boolean operands, got " + rightType, line, col);
                }
                break;

            // Bitwise operators: require integer types
            case "&":
            case "|":
            case "^":
            case "<<":
            case ">>":
            case ">>>":
                if (!isIntegralType(leftType) || !isIntegralType(rightType)) {
                    ErrorHandler.report(
                        "Semantic Error: Bitwise operator '" + op + "' requires integral operands, got " + 
                        leftType + " and " + rightType, line, col);
                }
                break;
        }
    }

    /**
     * Infers the type of an expression.
     */
    private String inferExpressionType(ASTNode expr) {
        if (expr == null) return "void";

        String type = expr.getType();
        String value = expr.getValue();

        switch (type) {
            case "NUMBER":
                // Simple heuristic: integer vs float
                return value.contains(".") || value.contains("e") || value.contains("E") ? "double" : "int";

            case "LITERAL":
                if (value.startsWith("\"")) return "String";
                if (value.startsWith("'")) return "char";
                // true/false/null
                if (value.equals("true") || value.equals("false")) return "boolean";
                if (value.equals("null")) return "Object";
                return "String";

            case "IDENTIFIER":
                String name = value;
                String declaredType = symbolTable.lookupVariableType(name);
                if (declaredType == null) {
                    ErrorHandler.report(
                        "Semantic Error: Identifier '" + name + "' used before declaration.", 
                        expr.getLine(), expr.getColumn());
                    return "unknown";
                }
                return declaredType;

            case "BINARY_OP":
                String op = value;
                
                List<ASTNode> children = expr.getChildren();
                if (children.size() >= 2) {
                    ASTNode leftNode = children.get(0);
                    ASTNode rightNode = children.get(1);
                    
                    String leftType = inferExpressionType(leftNode);
                    String rightType = inferExpressionType(rightNode);
                    
                    // --- 1. Division by Zero Check ---
                    if ((op.equals("/") || op.equals("%")) && rightNode.getType().equals("NUMBER") && 
                        (rightNode.getValue().equals("0") || rightNode.getValue().equals("0.0"))) {
                        ErrorHandler.report("Semantic Error: Arithmetic Exception: / by zero.", 
                                            rightNode.getLine(), rightNode.getColumn());
                    }

                    // --- 2. Run the Operator Validation ---
                    if (leftType != null && rightType != null) {
                        validateOperatorTypes(op, leftType, rightType, expr.getLine(), expr.getColumn());
                    }
                    
                    // --- 3. Determine Return Type ---
                    // Logical and Comparison return boolean
                    if (op.equals("&&") || op.equals("||") || 
                        op.equals("<") || op.equals(">") || op.equals("<=") || 
                        op.equals(">=") || op.equals("==") || op.equals("!=")) {
                        return "boolean";
                    }

                    // String concatenation with +
                    if (op.equals("+") && ("String".equals(leftType) || "String".equals(rightType))) {
                        return "String";
                    }
                    
                    // The Sentinel (Suppress cascading errors)
                    if ("type_error".equals(leftType) || "type_error".equals(rightType) || 
                        (!isNumericType(leftType) && !isNumericType(rightType))) {
                        return "type_error"; 
                    }
                    
                    // Promote to double/float
                    if ("double".equals(leftType) || "double".equals(rightType)) return "double";
                    if ("float".equals(leftType) || "float".equals(rightType)) return "float";
                    return "int";
                }
                return "unknown";

            case "UNARY_OP":
                if (value.equals("!")) return "boolean";
                // Other unary ops return same type as operand
                if (!expr.getChildren().isEmpty()) {
                    return inferExpressionType(expr.getChildren().get(0));
                }
                return "int";

            case "METHOD_CALL":
                String methodName = value;
                List<SymbolTable.MethodSignature> possibleMethods = symbolTable.lookupMethods(methodName);
                
                // 1. Definition Check
                if (possibleMethods == null || possibleMethods.isEmpty()) {
                    ErrorHandler.report("Semantic Error: Call to undefined method '" + methodName + "'.", 
                                        expr.getLine(), expr.getColumn());
                    return "unknown";
                }
                
                // Extract arguments safely
                List<ASTNode> argsNodeChildren = new java.util.ArrayList<>();
                for (ASTNode child : expr.getChildren()) {
                    if (child.getType().equals("ARGS")) {
                        argsNodeChildren = child.getChildren();
                        break;
                    }
                }
                
                // Resolve provided argument types
                List<String> providedArgTypes = new java.util.ArrayList<>();
                for (ASTNode argExpr : argsNodeChildren) {
                    providedArgTypes.add(inferExpressionType(argExpr));
                }
                
                // --- THE UPGRADE: Specific Error Reporting for Single Methods ---
                if (possibleMethods.size() == 1) {
                    SymbolTable.MethodSignature sig = possibleMethods.get(0);
                    
                    // Arity Check
                    if (sig.parameterTypes.size() != providedArgTypes.size()) {
                        ErrorHandler.report("Semantic Error: Method '" + methodName + "' expects " + 
                                            sig.parameterTypes.size() + " argument(s), but got " + providedArgTypes.size() + ".", 
                                            expr.getLine(), expr.getColumn());
                        return sig.returnType; // Return type to prevent cascade errors
                    }
                    
                    // Type Check
                    boolean typeError = false;
                    for (int i = 0; i < providedArgTypes.size(); i++) {
                        String expectedType = sig.parameterTypes.get(i);
                        String providedType = providedArgTypes.get(i);
                        
                        if (providedType != null && !providedType.equals("unknown") && !isTypeCompatible(expectedType, providedType)) {
                            ASTNode badArg = argsNodeChildren.get(i);
                            ErrorHandler.report("Semantic Error: Argument " + (i + 1) + " of '" + methodName + 
                                                "' expects " + expectedType + " but got " + providedType + ".", 
                                                badArg.getLine(), badArg.getColumn()); // Targets the exact bad argument!
                            typeError = true;
                        }
                    }
                    return sig.returnType;
                }
                
                // --- Fallback for Overloaded Methods (Multiple signatures) ---
                SymbolTable.MethodSignature matchedSig = null;
                int matchCount = 0;
                for (SymbolTable.MethodSignature sig : possibleMethods) {
                    if (sig.parameterTypes.size() != providedArgTypes.size()) continue; 
                    
                    boolean isMatch = true;
                    for (int i = 0; i < providedArgTypes.size(); i++) {
                        String pt = providedArgTypes.get(i);
                        if (pt != null && !pt.equals("unknown") && !isTypeCompatible(sig.parameterTypes.get(i), pt)) {
                            isMatch = false; break;
                        }
                    }
                    if (isMatch) { matchedSig = sig; matchCount++; }
                }
                
                if (matchCount == 0) {
                    ErrorHandler.report("Semantic Error: No suitable method found for '" + methodName + "' matching arguments.", expr.getLine(), expr.getColumn());
                    return "unknown";
                } else if (matchCount > 1) {
                    ErrorHandler.report("Semantic Error: Ambiguous method call for '" + methodName + "'.", expr.getLine(), expr.getColumn());
                    return "unknown";
                }
                
                return matchedSig.returnType;

            case "TERNARY":
                String thenType = inferExpressionType(expr.getChildren().get(1).getChildren().get(0));
                String elseType = inferExpressionType(expr.getChildren().get(2).getChildren().get(0));

                // 1. Fail Fast on incompatibility
                if (!isTypeCompatible(thenType, elseType) && !isTypeCompatible(elseType, thenType)) {
                    ErrorHandler.report("Semantic Error: Incompatible ternary branches ('" + 
                                        thenType + "' and '" + elseType + "').", 
                                        expr.getLine(), expr.getColumn());
                    return "type_error"; // Sentinel: tells downstream validators to skip secondary errors
                }

                // 2. Return the "Wider" type (Promotion logic)
                if (thenType.equals("double") || elseType.equals("double")) return "double";
                if (thenType.equals("float") || elseType.equals("float")) return "float";
                if (thenType.equals("String") || elseType.equals("String")) return "String";

                return thenType;

            case "CAST":
                // Cast expression returns the target type
                return value;

            case "NEW":
                // new ClassName returns ClassName type
                return value;

            case "ARRAY_ACCESS":
                // arr[i] returns the element type; for now assume int
                // In a real compiler, we'd track array component types
                List<ASTNode> accessChildren = expr.getChildren();
                if (!accessChildren.isEmpty()) {
                    String arrayType = inferExpressionType(accessChildren.get(0));
                    // Remove array brackets if present
                    if (arrayType != null && arrayType.endsWith("[]")) {
                        return arrayType.substring(0, arrayType.length() - 2);
                    }
                }
                return "int";

            case "FIELD_ACCESS":
                // obj.field returns unknown (would need class definitions)
                return "Object";

            case "POSTFIX_OP":
                // x++ or x-- returns the type of x
                if (!expr.getChildren().isEmpty()) {
                    return inferExpressionType(expr.getChildren().get(0));
                }
                return "int";

            case "ERROR":
                return "unknown"; // Prevents the 'null' return that causes "found null"

            default:
                return null;
        }
    }

    /**
     * Checks if a type is numeric (int, double, float, long, etc.).
     */
    private boolean isNumericType(String type) {
        switch (type) {
            case "int":
            case "double":
            case "float":
            case "long":
            case "byte":
            case "short":
                return true;
            default:
                return false;
        }
    }

    /**
     * Checks if a type is integral (for bitwise operations).
     */
    private boolean isIntegralType(String type) {
        switch (type) {
            case "int":
            case "long":
            case "byte":
            case "short":
                return true;
            default:
                return false;
        }
    }

    /**
     * Checks if actualType can be safely assigned to expectedType (Rule B.3)
     */
    private boolean isTypeCompatible(String expectedType, String actualType) {
        if (expectedType.equals(actualType)) return true;
        
        // Allowed Widening Conversions
        List<String> numericHierarchy = java.util.Arrays.asList("byte", "short", "int", "long", "float", "double");
        
        int expectedIndex = numericHierarchy.indexOf(expectedType);
        int actualIndex = numericHierarchy.indexOf(actualType);
        
        // If both are numeric, the actual type must be lower or equal in the hierarchy
        if (expectedIndex != -1 && actualIndex != -1) {
            return actualIndex <= expectedIndex; 
        }
        
        return false;
    }

    private void analyzeChildren(ASTNode node) {
        for (ASTNode child : node.getChildren()) {
            analyze(child);
        }
    }

    /**
     * Traverses a block and enforces dead-code detection (Rule D.2).
     */
    private void analyzeBlock(ASTNode node) {
        boolean previousReachable = isReachable;
        symbolTable.enterScope();
        
        for (ASTNode child : node.getChildren()) {
            if (!isReachable) {
                // THE FIX: Use child.getLine() and child.getColumn() instead of -1, -1
                ErrorHandler.report("Semantic Error: Unreachable statement.", 
                                    child.getLine(), child.getColumn());
                break; // Report once per block to avoid spam
            }
            
            analyze(child);
            
            String type = child.getType();
            if (type.equals("RETURN") || type.equals("BREAK") || type.equals("CONTINUE")) {
                isReachable = false; // Anything after this in the same block is dead code
            }
        }
        
        symbolTable.exitScope();
        isReachable = previousReachable; // Restore reachability state for outer blocks
    }

    /**
     * Enforces that IF, WHILE, and FOR conditions evaluate strictly to boolean (Rule D.3).
     */
    private void validateBooleanCondition(ASTNode loopOrIfNode) {
        for (ASTNode child : loopOrIfNode.getChildren()) {
            if (child.getType().equals("CONDITION")) {
                ASTNode expression = child.getChildren().isEmpty() ? null : child.getChildren().get(0);
                if (expression != null) {
                    String condType = inferExpressionType(expression);
                    if (!"boolean".equals(condType) && !"unknown".equals(condType)) {
                        ErrorHandler.report("Semantic Error: Condition must be boolean, found " + condType + ".", -1, -1);
                    }
                }
                break;
            }
        }
    }
}
