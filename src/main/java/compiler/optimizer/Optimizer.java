package compiler.optimizer;

import java.util.List;

import compiler.parser.ast.ASTNode;

public class Optimizer {

    public ASTNode optimize(ASTNode node) {
        return optimizeNode(node);
    }

    private ASTNode optimizeNode(ASTNode node) {
        if (node == null) {
            return null;
        }

        List<ASTNode> children = node.getChildren();
        for (int i = 0; i < children.size(); i++) {
            ASTNode child = optimizeNode(children.get(i));
            if (child == null) {
                children.remove(i);
                i--;
            } else {
                children.set(i, child);
            }
        }

        ASTNode optimized = foldConstants(node);
        if (optimized != node) {
            return optimized;
        }

        optimized = eliminateDeadCode(node);
        if (optimized != node) {
            return optimized;
        }

        return node;
    }

    private ASTNode foldConstants(ASTNode node) {
        if (!"BINARY_OP".equals(node.getType())) {
            return node;
        }

        if (node.getChildren().size() < 2) {
            return node;
        }

        ASTNode left = node.getChildren().get(0);
        ASTNode right = node.getChildren().get(1);
        String op = node.getValue();

        // Numeric constant folding
        if (isNumericLiteral(left) && isNumericLiteral(right)) {
            String folded = evaluateNumericBinary(op, left.getValue(), right.getValue());
            if (folded != null) {
                return ASTNode.of("NUMBER", folded, node.getLine(), node.getColumn());
            }
        }

        // Boolean constant folding
        if (isBooleanLiteral(left) && isBooleanLiteral(right)) {
            String folded = evaluateBooleanBinary(op, left.getValue(), right.getValue());
            if (folded != null) {
                return ASTNode.of("LITERAL", folded, node.getLine(), node.getColumn());
            }
        }

        // Algebraic simplifications
        ASTNode simplified = simplifyAlgebraic(node, left, right, op);
        if (simplified != null) {
            return simplified;
        }

        ASTNode booleanSimplified = simplifyBoolean(node, left, right, op);
        if (booleanSimplified != null) {
            return booleanSimplified;
        }

        return node;
    }

    private ASTNode simplifyAlgebraic(ASTNode node, ASTNode left, ASTNode right, String op) {
        if (!isNumericLiteral(left) && !isNumericLiteral(right)) {
            return null;
        }

        if ("*".equals(op)) {
            if (isNumericValue(right, "1")) return left;
            if (isNumericValue(left, "1")) return right;
            if (isNumericValue(right, "0") || isNumericValue(left, "0")) {
                return ASTNode.of("NUMBER", "0", node.getLine(), node.getColumn());
            }
        }

        if ("+".equals(op)) {
            if (isNumericValue(right, "0")) return left;
            if (isNumericValue(left, "0")) return right;
        }

        if ("-".equals(op)) {
            if (isNumericValue(right, "0")) return left;
        }

        if ("/".equals(op)) {
            if (isNumericValue(right, "1")) return left;
        }

        return null;
    }

    private ASTNode simplifyBoolean(ASTNode node, ASTNode left, ASTNode right, String op) {
        if ("&&".equals(op)) {
            if (isBooleanValue(left, "true")) return right;
            if (isBooleanValue(right, "true")) return left;
            if (isBooleanValue(left, "false") || isBooleanValue(right, "false")) {
                return ASTNode.of("LITERAL", "false", node.getLine(), node.getColumn());
            }
        }
        if ("||".equals(op)) {
            if (isBooleanValue(left, "false")) return right;
            if (isBooleanValue(right, "false")) return left;
            if (isBooleanValue(left, "true") || isBooleanValue(right, "true")) {
                return ASTNode.of("LITERAL", "true", node.getLine(), node.getColumn());
            }
        }
        return null;
    }

    private ASTNode eliminateDeadCode(ASTNode node) {
        switch (node.getType()) {
            case "TERNARY":
                return eliminateConstantTernary(node);
            case "IF_STMT":
                return eliminateConstantIf(node);
            case "WHILE_STMT":
                return eliminateFalseWhile(node);
            default:
                return node;
        }
    }

    private ASTNode eliminateConstantTernary(ASTNode node) {
        if (node.getChildren().size() < 3) {
            return node;
        }
        ASTNode conditionWrapper = node.getChildren().get(0);
        ASTNode thenWrapper = node.getChildren().get(1);
        ASTNode elseWrapper = node.getChildren().get(2);

        ASTNode condition = conditionWrapper.getChildren().isEmpty() ? null : conditionWrapper.getChildren().get(0);
        if (condition != null && isBooleanLiteral(condition)) {
            if (isBooleanValue(condition, "true")) {
                return thenWrapper;
            }
            if (isBooleanValue(condition, "false")) {
                return elseWrapper;
            }
        }
        return node;
    }

    private ASTNode eliminateConstantIf(ASTNode node) {
        ASTNode condition = getChildOfType(node, "CONDITION");
        if (condition == null || condition.getChildren().isEmpty()) {
            return node;
        }
        ASTNode expr = condition.getChildren().get(0);
        if (expr == null || !isBooleanLiteral(expr)) {
            return node;
        }

        ASTNode thenBranch = getChildOfType(node, "THEN");
        ASTNode elseBranch = getChildOfType(node, "ELSE");

        if (isBooleanValue(expr, "true")) {
            return thenBranch != null ? thenBranch : ASTNode.of("BLOCK", "", node.getLine(), node.getColumn());
        }
        if (isBooleanValue(expr, "false")) {
            return elseBranch != null ? elseBranch : null;
        }
        return node;
    }

    private ASTNode eliminateFalseWhile(ASTNode node) {
        ASTNode condition = getChildOfType(node, "CONDITION");
        if (condition == null || condition.getChildren().isEmpty()) {
            return node;
        }
        ASTNode expr = condition.getChildren().get(0);
        if (expr != null && isBooleanValue(expr, "false")) {
            return null;
        }
        return node;
    }

    private ASTNode getChildOfType(ASTNode node, String type) {
        for (ASTNode child : node.getChildren()) {
            if (type.equals(child.getType())) {
                return child;
            }
        }
        return null;
    }

    private boolean isNumericLiteral(ASTNode node) {
        return node != null && "NUMBER".equals(node.getType());
    }

    private boolean isBooleanLiteral(ASTNode node) {
        return node != null && "LITERAL".equals(node.getType())
            && ("true".equals(node.getValue()) || "false".equals(node.getValue()));
    }

    private boolean isNumericValue(ASTNode node, String value) {
        return isNumericLiteral(node) && value.equals(node.getValue());
    }

    private boolean isBooleanValue(ASTNode node, String value) {
        return isBooleanLiteral(node) && value.equals(node.getValue());
    }

    private String evaluateNumericBinary(String op, String leftValue, String rightValue) {
        try {
            if (leftValue.contains(".") || rightValue.contains(".")) {
                double leftNum = Double.parseDouble(leftValue);
                double rightNum = Double.parseDouble(rightValue);
                switch (op) {
                    case "+": return removeTrailingZero(leftNum + rightNum);
                    case "-": return removeTrailingZero(leftNum - rightNum);
                    case "*": return removeTrailingZero(leftNum * rightNum);
                    case "/": if (rightNum != 0) return removeTrailingZero(leftNum / rightNum); break;
                    case "%": if (rightNum != 0) return removeTrailingZero(leftNum % rightNum); break;
                }
            } else {
                long leftNum = Long.parseLong(leftValue);
                long rightNum = Long.parseLong(rightValue);
                switch (op) {
                    case "+": return String.valueOf(leftNum + rightNum);
                    case "-": return String.valueOf(leftNum - rightNum);
                    case "*": return String.valueOf(leftNum * rightNum);
                    case "/": if (rightNum != 0) return String.valueOf(leftNum / rightNum); break;
                    case "%": if (rightNum != 0) return String.valueOf(leftNum % rightNum); break;
                }
            }
        } catch (NumberFormatException e) {
            // fall through
        }
        return null;
    }

    private String removeTrailingZero(double value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private String evaluateBooleanBinary(String op, String leftValue, String rightValue) {
        boolean leftBool = Boolean.parseBoolean(leftValue);
        boolean rightBool = Boolean.parseBoolean(rightValue);
        switch (op) {
            case "&&": return String.valueOf(leftBool && rightBool);
            case "||": return String.valueOf(leftBool || rightBool);
            case "==": return String.valueOf(leftBool == rightBool);
            case "!=": return String.valueOf(leftBool != rightBool);
            default: return null;
        }
    }
}
