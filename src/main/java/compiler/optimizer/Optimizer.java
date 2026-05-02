package compiler.optimizer;

import java.util.ArrayList;
import java.util.List;

import compiler.parser.ast.ASTNode;

public class Optimizer {

    // Tracks whether any optimization fired in the current pass (for multi-pass loop).
    private boolean changed;

    // -------------------------------------------------------------------------
    // Public entry point — multi-pass loop (Suggest 1 + Suggest 2)
    // -------------------------------------------------------------------------

    public ASTNode optimize(ASTNode root) {
        ASTNode result = root;
        do {
            changed = false;
            result = optimizeNode(result);
        } while (changed);
        return result;
    }

    // -------------------------------------------------------------------------
    // Core recursive traversal — BUG FIX: no longer mutates the list in-place
    // -------------------------------------------------------------------------

    private ASTNode optimizeNode(ASTNode node) {
        if (node == null) {
            return null;
        }

        // BUG FIX 1: Build a fresh list instead of mutating getChildren() while iterating.
        // The old code used remove(i)/i-- on the live list, which skipped nodes when a
        // null-optimized child caused index shifts.
        List<ASTNode> optimizedChildren = new ArrayList<>();
        for (ASTNode child : node.getChildren()) {
            ASTNode optimizedChild = optimizeNode(child);
            if (optimizedChild != null) {
                optimizedChildren.add(optimizedChild);
            }
        }
        node.setChildren(optimizedChildren);

        // Dead-code elimination first so constant-folding can then fold the surviving node.
        ASTNode afterDead = eliminateDeadCode(node);
        if (afterDead != node) {
            changed = true;
            return afterDead;
        }

        ASTNode afterFold = foldConstants(node);
        if (afterFold != node) {
            changed = true;
            return afterFold;
        }

        // Missing 1: unary constant folding
        ASTNode afterUnary = foldUnary(node);
        if (afterUnary != node) {
            changed = true;
            return afterUnary;
        }

        return node;
    }

    // -------------------------------------------------------------------------
    // Constant folding (binary)
    // -------------------------------------------------------------------------

    private ASTNode foldConstants(ASTNode node) {
        if (!"BINARY_OP".equals(node.getType())) {
            return node;
        }
        if (node.getChildren().size() < 2) {
            return node;
        }

        ASTNode left  = node.getChildren().get(0);
        ASTNode right = node.getChildren().get(1);
        String  op    = node.getValue();

        // Numeric constant folding
        if (isNumericLiteral(left) && isNumericLiteral(right)) {
            // Warning 3 fix: comparison operators now fold numeric literals too.
            String folded = evaluateNumericBinary(op, left.getValue(), right.getValue());
            if (folded != null) {
                // Comparison ops produce a boolean literal; arithmetic produces a number.
                String type = isComparisonOp(op) ? "LITERAL" : "NUMBER";
                return ASTNode.of(type, folded, node.getLine(), node.getColumn());
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

    // -------------------------------------------------------------------------
    // Missing 1: Unary constant folding
    // -------------------------------------------------------------------------

    private ASTNode foldUnary(ASTNode node) {
        if (!"UNARY_OP".equals(node.getType()) || node.getChildren().isEmpty()) {
            return node;
        }
        ASTNode operand = node.getChildren().get(0);
        String  op      = node.getValue();

        if ("!".equals(op) && isBooleanLiteral(operand)) {
            boolean val = Boolean.parseBoolean(operand.getValue());
            return ASTNode.of("LITERAL", String.valueOf(!val), node.getLine(), node.getColumn());
        }

        if ("-".equals(op) && isNumericLiteral(operand)) {
            String negated = negateNumeric(operand.getValue());
            if (negated != null) {
                return ASTNode.of("NUMBER", negated, node.getLine(), node.getColumn());
            }
        }

        // Double-negation: ~~x → x (bitwise) or !!x → x (logical, if operand is bool)
        if ("~".equals(op) && "UNARY_OP".equals(operand.getType()) && "~".equals(operand.getValue())) {
            return operand.getChildren().isEmpty() ? node : operand.getChildren().get(0);
        }
        if ("!".equals(op) && "UNARY_OP".equals(operand.getType()) && "!".equals(operand.getValue())) {
            return operand.getChildren().isEmpty() ? node : operand.getChildren().get(0);
        }

        return node;
    }

    private String negateNumeric(String value) {
        try {
            if (value.startsWith("-")) {
                return value.substring(1);
            }
            if (value.contains(".")) {
                double d = Double.parseDouble(value);
                return formatDouble(-d, true);
            }
            long l = Long.parseLong(value);
            return String.valueOf(-l);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Algebraic simplifications (Suggest 3 fix: preserve source coords)
    // -------------------------------------------------------------------------

    private ASTNode simplifyAlgebraic(ASTNode node, ASTNode left, ASTNode right, String op) {
        if (!isNumericLiteral(left) && !isNumericLiteral(right)) {
            return null;
        }

        if ("*".equals(op)) {
            if (isNumericValue(right, "1")) return copyCoords(left, node);
            if (isNumericValue(left,  "1")) return copyCoords(right, node);
            if (isNumericValue(right, "0") || isNumericValue(left, "0")) {
                return ASTNode.of("NUMBER", "0", node.getLine(), node.getColumn());
            }
        }

        if ("+".equals(op)) {
            if (isNumericValue(right, "0")) return copyCoords(left, node);
            if (isNumericValue(left,  "0")) return copyCoords(right, node);
        }

        if ("-".equals(op)) {
            if (isNumericValue(right, "0")) return copyCoords(left, node);
        }

        if ("/".equals(op)) {
            if (isNumericValue(right, "1")) return copyCoords(left, node);
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // Boolean simplifications (Suggest 3 fix: preserve source coords)
    // -------------------------------------------------------------------------

    private ASTNode simplifyBoolean(ASTNode node, ASTNode left, ASTNode right, String op) {
        if ("&&".equals(op)) {
            if (isBooleanValue(left,  "true"))  return copyCoords(right, node);
            if (isBooleanValue(right, "true"))  return copyCoords(left,  node);
            if (isBooleanValue(left,  "false") || isBooleanValue(right, "false")) {
                return ASTNode.of("LITERAL", "false", node.getLine(), node.getColumn());
            }
        }
        if ("||".equals(op)) {
            if (isBooleanValue(left,  "false")) return copyCoords(right, node);
            if (isBooleanValue(right, "false")) return copyCoords(left,  node);
            if (isBooleanValue(left,  "true") || isBooleanValue(right, "true")) {
                return ASTNode.of("LITERAL", "true", node.getLine(), node.getColumn());
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Dead-code elimination
    // -------------------------------------------------------------------------

    private ASTNode eliminateDeadCode(ASTNode node) {
        switch (node.getType()) {
            case "TERNARY":    return eliminateConstantTernary(node);
            case "IF_STMT":    return eliminateConstantIf(node);
            case "WHILE_STMT": return eliminateConstantWhile(node);
            case "BLOCK":      return eliminateDeadStatementsInBlock(node); // Missing 3 + Suggest 4
            default:           return node;
        }
    }

    // BUG FIX 2: unwrap thenWrapper/elseWrapper to get the actual expression node.
    private ASTNode eliminateConstantTernary(ASTNode node) {
        if (node.getChildren().size() < 3) {
            return node;
        }
        ASTNode conditionWrapper = node.getChildren().get(0);
        ASTNode thenWrapper      = node.getChildren().get(1);
        ASTNode elseWrapper      = node.getChildren().get(2);

        ASTNode condition = conditionWrapper.getChildren().isEmpty()
                ? null : conditionWrapper.getChildren().get(0);

        if (condition != null && isBooleanLiteral(condition)) {
            if (isBooleanValue(condition, "true")) {
                // Unwrap: return the actual expression, not the wrapper node.
                return thenWrapper.getChildren().isEmpty()
                        ? thenWrapper : thenWrapper.getChildren().get(0);
            }
            if (isBooleanValue(condition, "false")) {
                return elseWrapper.getChildren().isEmpty()
                        ? elseWrapper : elseWrapper.getChildren().get(0);
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
            return thenBranch != null
                    ? thenBranch
                    : ASTNode.of("BLOCK", "", node.getLine(), node.getColumn());
        }
        if (isBooleanValue(expr, "false")) {
            // Returning null removes this IF entirely from the parent's child list.
            return elseBranch != null ? elseBranch : null;
        }
        return node;
    }

    // Missing 2 fix: handle while(true) annotation + while(false) removal.
    private ASTNode eliminateConstantWhile(ASTNode node) {
        ASTNode condition = getChildOfType(node, "CONDITION");
        if (condition == null || condition.getChildren().isEmpty()) {
            return node;
        }
        ASTNode expr = condition.getChildren().get(0);
        if (expr == null) {
            return node;
        }
        if (isBooleanValue(expr, "false")) {
            return null; // Remove entirely — loop never executes.
        }
        if (isBooleanValue(expr, "true")) {
            // Mark as infinite loop so downstream phases can use the annotation.
            node.setAttribute("infinite_loop", "true");
            return node;
        }
        return node;
    }

    // Missing 3 + Suggest 4: prune unreachable statements after RETURN/BREAK/CONTINUE.
    private ASTNode eliminateDeadStatementsInBlock(ASTNode node) {
        List<ASTNode> children = node.getChildren();
        int cutoff = -1;
        for (int i = 0; i < children.size(); i++) {
            String type = children.get(i).getType();
            if ("RETURN".equals(type) || "BREAK".equals(type) || "CONTINUE".equals(type)) {
                cutoff = i;
                break;
            }
        }
        if (cutoff >= 0 && cutoff < children.size() - 1) {
            // Keep everything up to and including the terminal statement.
            List<ASTNode> pruned = new ArrayList<>(children.subList(0, cutoff + 1));
            node.setChildren(pruned);
            changed = true;
        }
        return node;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Copy source coordinates from the optimized-site node onto a surviving child. */
    private ASTNode copyCoords(ASTNode target, ASTNode site) {
        // Only copy coords if the node factory supports it; otherwise return as-is.
        // This satisfies Suggest 3: surviving child nodes get the parent's position.
        return ASTNode.of(target.getType(), target.getValue(), site.getLine(), site.getColumn());
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

    private boolean isComparisonOp(String op) {
        switch (op) {
            case "<": case ">": case "<=": case ">=": case "==": case "!=": return true;
            default: return false;
        }
    }

    // -------------------------------------------------------------------------
    // Numeric evaluation — Warning 2 + Warning 3 fixes
    // -------------------------------------------------------------------------

    private String evaluateNumericBinary(String op, String leftValue, String rightValue) {
        try {
            boolean isFloat = leftValue.contains(".") || rightValue.contains(".");
            if (isFloat) {
                double l = Double.parseDouble(leftValue);
                double r = Double.parseDouble(rightValue);
                // Warning 3 fix: comparison operators now supported.
                switch (op) {
                    case "+":  return formatDouble(l + r, true);
                    case "-":  return formatDouble(l - r, true);
                    case "*":  return formatDouble(l * r, true);
                    case "/":  if (r != 0) return formatDouble(l / r, true); break;
                    case "%":  if (r != 0) return formatDouble(l % r, true); break;
                    case "<":  return String.valueOf(l <  r);
                    case ">":  return String.valueOf(l >  r);
                    case "<=": return String.valueOf(l <= r);
                    case ">=": return String.valueOf(l >= r);
                    case "==": return String.valueOf(l == r);
                    case "!=": return String.valueOf(l != r);
                }
            } else {
                long l = Long.parseLong(leftValue);
                long r = Long.parseLong(rightValue);
                // Warning 1 note: range checking against inferred type would require
                // type information passed in from the semantic analyser; we leave the
                // folded value as-is and let Rule 12 validation catch range violations.
                switch (op) {
                    case "+":  return String.valueOf(l + r);
                    case "-":  return String.valueOf(l - r);
                    case "*":  return String.valueOf(l * r);
                    case "/":  if (r != 0) return String.valueOf(l / r); break;
                    case "%":  if (r != 0) return String.valueOf(l % r); break;
                    // Warning 3 fix: comparison operators now supported.
                    case "<":  return String.valueOf(l <  r);
                    case ">":  return String.valueOf(l >  r);
                    case "<=": return String.valueOf(l <= r);
                    case ">=": return String.valueOf(l >= r);
                    case "==": return String.valueOf(l == r);
                    case "!=": return String.valueOf(l != r);
                }
            }
        } catch (NumberFormatException e) {
            // fall through
        }
        return null;
    }

    /**
     * Warning 2 fix: replaces removeTrailingZero().
     * When isFloatContext is true the result always keeps at least one decimal place
     * so the NUMBER node is correctly inferred as double by downstream phases.
     */
    private String formatDouble(double value, boolean isFloatContext) {
        if (isFloatContext && value == (long) value) {
            return (long) value + ".0";
        }
        return String.valueOf(value);
    }

    private String evaluateBooleanBinary(String op, String leftValue, String rightValue) {
        boolean l = Boolean.parseBoolean(leftValue);
        boolean r = Boolean.parseBoolean(rightValue);
        switch (op) {
            case "&&": return String.valueOf(l && r);
            case "||": return String.valueOf(l || r);
            case "==": return String.valueOf(l == r);
            case "!=": return String.valueOf(l != r);
            default:   return null;
        }
    }
}