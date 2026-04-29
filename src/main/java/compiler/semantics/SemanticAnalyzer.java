package compiler.semantics;

import java.util.List;

import compiler.parser.ast.ASTNode;
import compiler.util.ErrorHandler;

public class SemanticAnalyzer {
    private final SymbolTable symbolTable;

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

            case "BLOCK":
                symbolTable.enterScope();
                analyzeChildren(node);
                symbolTable.exitScope();
                break;

            case "VAR_DECL":
                validateVariableDeclaration(node);
                break;

            case "ASSIGN":
                validateAssignment(node);
                break;

            case "BINARY_OP":
                validateBinaryOperation(node);
                break;

            case "IF":
            case "WHILE":
            case "DO_WHILE":
            case "FOR":
            case "SWITCH":
                analyzeChildren(node);
                break;

            case "BREAK":
            case "CONTINUE":
                // These are simple control flow statements
                // In a full implementation, would verify they're inside a loop
                break;

            default:
                // For all other nodes, just traverse
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
        if (children.size() < 2) {
            ErrorHandler.report("Semantic Error: Invalid variable declaration structure", -1, -1);
            return;
        }

        String type = children.get(0).getValue();
        String name = children.get(1).getValue();

        if (symbolTable.isDefinedInCurrentScope(name)) {
            ErrorHandler.report("Semantic Error: Variable '" + name + "' is already defined in this scope.", -1, -1);
            return;
        }

        // Check if there's an initializer
        if (children.size() >= 3) {
            // INIT_VALUE is typically a wrapper node; get the actual expression
            ASTNode initWrapper = children.get(2); // INIT_VALUE node
            ASTNode initExpr = initWrapper.getChildren().isEmpty() ? initWrapper : initWrapper.getChildren().get(0);
            
            String initType = inferExpressionType(initExpr);
            if (initType != null && !isTypeCompatible(type, initType)) {
                ErrorHandler.report(
                    "Semantic Error: Type mismatch in initialization. Variable '" + name + 
                    "' is " + type + " but initialized with " + initType + ".", -1, -1);
            }
        }

        // Register the variable
        symbolTable.defineVariable(name, type);
        analyzeChildren(node);
    }

    /**
     * Validates assignment operations.
     * Structure: ASSIGN has children [left_side, right_side]
     */
    private void validateAssignment(ASTNode node) {
        List<ASTNode> children = node.getChildren();
        if (children.size() < 2) {
            analyzeChildren(node);
            return;
        }

        ASTNode lhs = children.get(0);
        ASTNode rhs = children.get(1);

        // LHS must be an identifier
        if (!"IDENTIFIER".equals(lhs.getType())) {
            ErrorHandler.report("Semantic Error: Assignment target must be an identifier.", -1, -1);
            analyzeChildren(node);
            return;
        }

        String varName = lhs.getValue();
        String varType = symbolTable.lookupVariableType(varName);

        if (varType == null) {
            ErrorHandler.report(
                "Semantic Error: Variable '" + varName + "' used in assignment before declaration.", -1, -1);
            analyzeChildren(node);
            return;
        }

        // Check RHS type compatibility
        String rhsType = inferExpressionType(rhs);
        if (rhsType != null && !isTypeCompatible(varType, rhsType)) {
            ErrorHandler.report(
                "Semantic Error: Cannot assign " + rhsType + " to " + varType + ".", -1, -1);
        }

        // Still analyze children for nested variable references
        analyzeChildren(node);
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

        String op = node.getValue(); // Operator like "+", "-", "&&", etc.
        ASTNode left = children.get(0);
        ASTNode right = children.get(1);

        String leftType = inferExpressionType(left);
        String rightType = inferExpressionType(right);

        // Validate operand types against operator
        if (leftType != null && rightType != null) {
            validateOperatorTypes(op, leftType, rightType);
        }

        analyzeChildren(node);
    }

    /**
     * Validates that an operator is legal for its operand types.
     */
    private void validateOperatorTypes(String op, String leftType, String rightType) {
        switch (op) {
            // Arithmetic operators: require numeric types
            case "+":
            case "-":
            case "*":
            case "/":
            case "%":
                if (!isNumericType(leftType) && !leftType.equals("String")) {
                    ErrorHandler.report(
                        "Semantic Error: Operator '" + op + "' requires numeric or String operands, got " + leftType, -1, -1);
                }
                if (!isNumericType(rightType) && !rightType.equals("String")) {
                    ErrorHandler.report(
                        "Semantic Error: Operator '" + op + "' requires numeric or String operands, got " + rightType, -1, -1);
                }
                // String concatenation is OK with +
                if (op.equals("+") && (leftType.equals("String") || rightType.equals("String"))) {
                    return; // String + anything is allowed
                }
                // Both must be numeric for other arithmetic
                if (!isNumericType(leftType) || !isNumericType(rightType)) {
                    ErrorHandler.report(
                        "Semantic Error: Both operands of '" + op + "' must be numeric, got " + 
                        leftType + " and " + rightType, -1, -1);
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
                        leftType + " and " + rightType, -1, -1);
                }
                break;

            // Equality operators: any type but must be compatible
            case "==":
            case "!=":
                if (!isTypeCompatible(leftType, rightType)) {
                    ErrorHandler.report(
                        "Semantic Error: Cannot compare " + leftType + " with " + rightType + 
                        " using '" + op + "'", -1, -1);
                }
                break;

            // Logical operators: require boolean types
            case "&&":
            case "||":
                if (!leftType.equals("boolean")) {
                    ErrorHandler.report(
                        "Semantic Error: Operator '" + op + "' requires boolean operands, got " + leftType, -1, -1);
                }
                if (!rightType.equals("boolean")) {
                    ErrorHandler.report(
                        "Semantic Error: Operator '" + op + "' requires boolean operands, got " + rightType, -1, -1);
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
                        leftType + " and " + rightType, -1, -1);
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
                        "Semantic Error: Identifier '" + name + "' used before declaration.", -1, -1);
                    return "unknown";
                }
                return declaredType;

            case "BINARY_OP":
                String op = value;
                // Logical operations return boolean
                if (op.equals("&&") || op.equals("||")) return "boolean";
                // Comparison returns boolean
                if (op.equals("<") || op.equals(">") || op.equals("<=") || 
                    op.equals(">=") || op.equals("==") || op.equals("!=")) {
                    return "boolean";
                }
                // Arithmetic: infer from operands
                List<ASTNode> children = expr.getChildren();
                if (children.size() >= 2) {
                    String leftType = inferExpressionType(children.get(0));
                    String rightType = inferExpressionType(children.get(1));
                    
                    // String concatenation with +
                    if (op.equals("+") && (leftType.equals("String") || rightType.equals("String"))) {
                        return "String";
                    }
                    
                    // Promote to double if either is double
                    if ("double".equals(leftType) || "double".equals(rightType)) return "double";
                    if ("float".equals(leftType) || "float".equals(rightType)) return "float";
                    return "int";
                }
                return "int";

            case "UNARY_OP":
                if (value.equals("!")) return "boolean";
                // Other unary ops return same type as operand
                if (!expr.getChildren().isEmpty()) {
                    return inferExpressionType(expr.getChildren().get(0));
                }
                return "int";

            case "METHOD_CALL":
                // For now, assume method calls return their inferred type or int
                return "int";

            case "TERNARY":
                // Ternary returns type of the then/else branches (should match)
                List<ASTNode> ternaryChildren = expr.getChildren();
                if (ternaryChildren.size() >= 3) {
                    // Find THEN and ELSE branches
                    String thenType = inferExpressionType(ternaryChildren.get(1).getChildren().isEmpty() ? 
                                                         ternaryChildren.get(1) : ternaryChildren.get(1).getChildren().get(0));
                    String elseType = inferExpressionType(ternaryChildren.get(2).getChildren().isEmpty() ? 
                                                         ternaryChildren.get(2) : ternaryChildren.get(2).getChildren().get(0));
                    // Return then type if compatible, else "Object"
                    if (thenType != null && elseType != null && isTypeCompatible(thenType, elseType)) {
                        return thenType;
                    }
                    return "Object";
                }
                return "Object";

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
     * Checks if two types are compatible (can be assigned/compared).
     */
    private boolean isTypeCompatible(String targetType, String sourceType) {
        // Same type is always compatible
        if (targetType.equals(sourceType)) return true;

        // Numeric type widening: smaller types can be assigned to larger
        // int -> double/float/long
        if (targetType.equals("double") && isNumericType(sourceType)) return true;
        if (targetType.equals("float") && !sourceType.equals("double")) return true;
        if (targetType.equals("long") && !sourceType.equals("double") && !sourceType.equals("float")) return true;

        // Object type accepts null
        if (targetType.equals("Object") && sourceType.equals("null")) return true;

        return false;
    }

    private void analyzeChildren(ASTNode node) {
        for (ASTNode child : node.getChildren()) {
            analyze(child);
        }
    }
}
