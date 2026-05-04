package compiler.optimizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import compiler.parser.ast.ASTNode;

public class Optimizer {

    // -------------------------------------------------------------------------
    // Suggest 2: OptimizeResult — carries the rewritten tree, a changed flag,
    // the last rule that fired, and per-rule diagnostic counters.
    // -------------------------------------------------------------------------

    public static final class OptimizeResult {
        public final ASTNode node;
        public final boolean changed;
        public final String  ruleApplied;             // name of the last rule that fired
        public final Map<String, Integer> ruleCounts; // rule name → times fired across all passes

        OptimizeResult(ASTNode node, boolean changed,
                       String ruleApplied, Map<String, Integer> ruleCounts) {
            this.node        = node;
            this.changed     = changed;
            this.ruleApplied = ruleApplied;
            this.ruleCounts  = ruleCounts;
        }

        /** Human-readable summary, e.g. "folded 3 constants, eliminated 1 dead branch". */
        public String summary() {
            if (ruleCounts.isEmpty()) return "no optimizations applied";
            StringBuilder sb = new StringBuilder();
            ruleCounts.forEach((rule, count) -> {
                if (sb.length() > 0) sb.append(", ");
                sb.append(rule.replace('_', ' ')).append(' ').append(count);
            });
            return sb.toString();
        }
    }

    // -------------------------------------------------------------------------
    // Per-pass mutable state
    // -------------------------------------------------------------------------

    private boolean changed;
    private String  lastRule;
    private final Map<String, Integer> ruleCounts = new LinkedHashMap<>();

    // Missing 5: CSE table — maps a canonical expression key to the first node
    // that produced it in the current scope. Cleared at the start of every pass.
    private final Map<String, ASTNode> cseTable = new HashMap<>();

    // -------------------------------------------------------------------------
    // Public entry point — multi-pass loop (Suggest 1).
    // Returns OptimizeResult so callers get full diagnostics (Suggest 2).
    // -------------------------------------------------------------------------

    public OptimizeResult optimize(ASTNode root) {
        ASTNode result       = root;
        boolean everChanged  = false;
        String  finalRule    = null;
        Map<String, Integer> totalCounts = new LinkedHashMap<>();

        do {
            changed  = false;
            lastRule = null;
            ruleCounts.clear();
            cseTable.clear();

            result = optimizeNode(result);

            if (changed) {
                everChanged = true;
                finalRule   = lastRule;
                ruleCounts.forEach((k, v) -> totalCounts.merge(k, v, Integer::sum));
            }
        } while (changed);

        return new OptimizeResult(result, everChanged, finalRule, totalCounts);
    }

    // -------------------------------------------------------------------------
    // Core recursive traversal
    // Bug fix 1: builds a fresh child list — never mutates getChildren() in place.
    // -------------------------------------------------------------------------

    private ASTNode optimizeNode(ASTNode node) {
        if (node == null) return null;

        List<ASTNode> optimizedChildren = new ArrayList<>();
        for (ASTNode child : node.getChildren()) {
            ASTNode opt = optimizeNode(child);
            if (opt != null) optimizedChildren.add(opt);
        }
        node.setChildren(optimizedChildren);

        // Pass order matters: dead-code first so folding sees only live nodes.
        ASTNode result;

        result = eliminateDeadCode(node);
        if (result != node) { fire("dead code eliminated"); return result; }

        result = foldConstants(node);
        if (result != node) return result; // rule name fired inside foldConstants

        result = foldUnary(node);
        if (result != node) { fire("unary folded"); return result; }

        result = strengthReduce(node);
        if (result != node) { fire("strength reduced"); return result; }

        // Disable CSE for now - it breaks when variables used in expressions are reassigned
        // result = eliminateCommonSubexpression(node);
        // if (result != node) { fire("cse eliminated"); return result; }

        return node;
    }

    // -------------------------------------------------------------------------
    // Suggest 2 helper: record a named rule firing
    // -------------------------------------------------------------------------

    private void fire(String rule) {
        changed  = true;
        lastRule = rule;
        ruleCounts.merge(rule, 1, Integer::sum);
    }

    // -------------------------------------------------------------------------
    // Constant folding (binary)
    // -------------------------------------------------------------------------

    private ASTNode foldConstants(ASTNode node) {
        if (!"BINARY_OP".equals(node.getType()) || node.getChildren().size() < 2) {
            return node;
        }

        ASTNode left  = node.getChildren().get(0);
        ASTNode right = node.getChildren().get(1);
        String  op    = node.getValue();

        // Numeric constant folding — Warning 1 fix: pass inferred type for range check.
        if (isNumericLiteral(left) && isNumericLiteral(right)) {
            NumericType inferredType = inferNumericType(left.getValue(), right.getValue());
            String folded = evaluateNumericBinary(op, left.getValue(), right.getValue(), inferredType);
            if (folded != null) {
                if (folded.startsWith("OVERFLOW:")) {
                    // Out-of-range result: attach diagnostic and leave node unfolded
                    // so Rule 12 (semantic analyser) sees the original expression.
                    node.setAttribute("fold_overflow", folded.substring(9));
                    return node;
                }
                // Warning 3 fix: comparison ops produce a boolean LITERAL, not NUMBER.
                String type = isComparisonOp(op) ? "LITERAL" : "NUMBER";
                fire("constant folded");
                return ASTNode.of(type, folded, node.getLine(), node.getColumn());
            }
        }

        // Boolean constant folding.
        if (isBooleanLiteral(left) && isBooleanLiteral(right)) {
            String folded = evaluateBooleanBinary(op, left.getValue(), right.getValue());
            if (folded != null) {
                fire("constant folded");
                return ASTNode.of("LITERAL", folded, node.getLine(), node.getColumn());
            }
        }

        // Algebraic simplifications (Suggest 3: coords preserved via copyCoords).
        ASTNode simplified = simplifyAlgebraic(node, left, right, op);
        if (simplified != null) { fire("algebraic simplified"); return simplified; }

        ASTNode boolSimplified = simplifyBoolean(node, left, right, op);
        if (boolSimplified != null) { fire("boolean simplified"); return boolSimplified; }

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

        // !true → false,  !false → true
        if ("!".equals(op) && isBooleanLiteral(operand)) {
            boolean val = Boolean.parseBoolean(operand.getValue());
            return ASTNode.of("LITERAL", String.valueOf(!val), node.getLine(), node.getColumn());
        }

        // -NUMBER → negated NUMBER
        if ("-".equals(op) && isNumericLiteral(operand)) {
            String negated = negateNumeric(operand.getValue());
            if (negated != null)
                return ASTNode.of("NUMBER", negated, node.getLine(), node.getColumn());
        }

        // ~~x → x  and  !!x → x
        if (("~".equals(op) || "!".equals(op))
                && "UNARY_OP".equals(operand.getType())
                && op.equals(operand.getValue())
                && !operand.getChildren().isEmpty()) {
            return operand.getChildren().get(0);
        }

        return node;
    }

    private String negateNumeric(String value) {
        try {
            if (value.startsWith("-")) return value.substring(1);
            if (value.contains("."))   return formatDouble(-Double.parseDouble(value), true);
            return String.valueOf(-Long.parseLong(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Missing 4: Strength reduction
    // x * 2^n  →  x << n
    // x / 2^n  →  x >> n
    // Only fires when one operand is a compile-time power-of-two integer literal
    // and the other operand is a non-literal expression (variable, call, etc.).
    // -------------------------------------------------------------------------

    private ASTNode strengthReduce(ASTNode node) {
        if (!"BINARY_OP".equals(node.getType()) || node.getChildren().size() < 2) {
            return node;
        }

        ASTNode left  = node.getChildren().get(0);
        ASTNode right = node.getChildren().get(1);
        String  op    = node.getValue();

        if ("*".equals(op)) {
            // x * 2^n  (commutative — check both sides)
            if (isIntegerLiteral(right) && !isNumericLiteral(left)) {
                int shift = powerOfTwoShift(right.getValue());
                if (shift > 0) return makeShift("<<", left, shift, node);
            }
            if (isIntegerLiteral(left) && !isNumericLiteral(right)) {
                int shift = powerOfTwoShift(left.getValue());
                if (shift > 0) return makeShift("<<", right, shift, node);
            }
        }

        if ("/".equals(op)) {
            // x / 2^n  (divisor must be the literal, not the dividend)
            if (isIntegerLiteral(right) && !isNumericLiteral(left)) {
                int shift = powerOfTwoShift(right.getValue());
                if (shift > 0) return makeShift(">>", left, shift, node);
            }
        }

        return node;
    }

    /** Returns log2(value) if value is a positive power of two strictly greater than 1, else -1. */
    private int powerOfTwoShift(String value) {
        try {
            long v = Long.parseLong(value);
            if (v > 1 && (v & (v - 1)) == 0) return Long.numberOfTrailingZeros(v);
        } catch (NumberFormatException ignored) { }
        return -1;
    }

    /** Builds BINARY_OP(shiftOp, expr, NUMBER(shift)) at the parent site's source position. */
    private ASTNode makeShift(String shiftOp, ASTNode expr, int shift, ASTNode site) {
        ASTNode shiftNode = ASTNode.of("BINARY_OP", shiftOp, site.getLine(), site.getColumn());
        ASTNode shiftAmt  = ASTNode.of("NUMBER", String.valueOf(shift), site.getLine(), site.getColumn());
        List<ASTNode> children = new ArrayList<>();
        children.add(copyCoords(expr, site));
        children.add(shiftAmt);
        shiftNode.setChildren(children);
        return shiftNode;
    }

    // -------------------------------------------------------------------------
    // Missing 5: Common subexpression elimination (CSE)
    //
    // Within a single pass, if an identical pure binary expression is seen for
    // a second time in the same scope the second occurrence is replaced with a
    // TEMP_REF node. The first occurrence is annotated with cse_key so the code
    // generator knows to materialise a named temporary for it.
    //
    // "Identical" is structural: same operator + same literal values / variable
    // names. Side-effecting nodes (CALL, ASSIGN) are excluded.
    // Commutative operators are normalised so (a + b) and (b + a) share a key.
    // -------------------------------------------------------------------------

    private ASTNode eliminateCommonSubexpression(ASTNode node) {
        if (!"BINARY_OP".equals(node.getType()) || node.getChildren().size() < 2) {
            return node;
        }
        if (!isPureExpression(node)) return node;

        String key = cseKey(node);
        if (key == null) return node;

        ASTNode existing = cseTable.get(key);
        if (existing != null) {
            // Second occurrence — replace with a TEMP_REF to the canonical first.
            ASTNode ref = ASTNode.of("TEMP_REF", key, node.getLine(), node.getColumn());
            ref.setAttribute("cse_key", key);
            return ref;
        }

        // First occurrence — register and annotate so the code generator emits a temp.
        cseTable.put(key, node);
        node.setAttribute("cse_key", key);
        return node;
    }

    /**
     * Builds a canonical string key for a pure expression tree, e.g.:
     *   a + b  →  "BINARY_OP:+:[VAR:a]:[VAR:b]"
     * Returns null if any sub-tree contains a node that cannot be keyed
     * (unknown type, call, assignment), making CSE conservatively safe.
     */
    private String cseKey(ASTNode node) {
        if (node == null) return null;
        switch (node.getType()) {
            case "NUMBER":
            case "LITERAL":
                return node.getType() + ":" + node.getValue();
            case "IDENTIFIER":
            case "VAR_REF":
                return "VAR:" + node.getValue();
            case "BINARY_OP": {
                if (node.getChildren().size() < 2) return null;
                String lk = cseKey(node.getChildren().get(0));
                String rk = cseKey(node.getChildren().get(1));
                if (lk == null || rk == null) return null;
                String op = node.getValue();
                // Normalise commutative operators so (a+b) == (b+a).
                if (isCommutative(op) && lk.compareTo(rk) > 0) {
                    String tmp = lk; lk = rk; rk = tmp;
                }
                return "BINARY_OP:" + op + ":[" + lk + "]:[" + rk + "]";
            }
            default:
                return null; // conservative: unknown node type — skip CSE
        }
    }

    private boolean isPureExpression(ASTNode node) {
        if (node == null) return true;
        String t = node.getType();
        if ("CALL".equals(t) || "ASSIGN".equals(t)) return false;
        for (ASTNode child : node.getChildren()) {
            if (!isPureExpression(child)) return false;
        }
        return true;
    }

    private boolean isCommutative(String op) {
        return "+".equals(op) || "*".equals(op)
            || "==".equals(op) || "!=".equals(op)
            || "&&".equals(op) || "||".equals(op);
    }

    // -------------------------------------------------------------------------
    // Algebraic simplifications (Suggest 3: source coords preserved)
    // -------------------------------------------------------------------------

    private ASTNode simplifyAlgebraic(ASTNode node, ASTNode left, ASTNode right, String op) {
        if (!isNumericLiteral(left) && !isNumericLiteral(right)) return null;

        if ("*".equals(op)) {
            if (isNumericValue(right, "1")) return copyCoords(left,  node);
            if (isNumericValue(left,  "1")) return copyCoords(right, node);
            if (isNumericValue(right, "0") || isNumericValue(left, "0"))
                return ASTNode.of("NUMBER", "0", node.getLine(), node.getColumn());
        }
        if ("+".equals(op)) {
            if (isNumericValue(right, "0")) return copyCoords(left,  node);
            if (isNumericValue(left,  "0")) return copyCoords(right, node);
        }
        if ("-".equals(op) && isNumericValue(right, "0")) return copyCoords(left, node);
        if ("/".equals(op) && isNumericValue(right, "1")) return copyCoords(left, node);

        return null;
    }

    // -------------------------------------------------------------------------
    // Boolean simplifications (Suggest 3: source coords preserved)
    // -------------------------------------------------------------------------

    private ASTNode simplifyBoolean(ASTNode node, ASTNode left, ASTNode right, String op) {
        if ("&&".equals(op)) {
            if (isBooleanValue(left,  "true"))  return copyCoords(right, node);
            if (isBooleanValue(right, "true"))  return copyCoords(left,  node);
            if (isBooleanValue(left,  "false") || isBooleanValue(right, "false"))
                return ASTNode.of("LITERAL", "false", node.getLine(), node.getColumn());
        }
        if ("||".equals(op)) {
            if (isBooleanValue(left,  "false")) return copyCoords(right, node);
            if (isBooleanValue(right, "false")) return copyCoords(left,  node);
            if (isBooleanValue(left,  "true") || isBooleanValue(right, "true"))
                return ASTNode.of("LITERAL", "true", node.getLine(), node.getColumn());
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
            case "BLOCK":      return eliminateDeadStatementsInBlock(node);
            default:           return node;
        }
    }

    // Bug fix 2: unwrap thenWrapper/elseWrapper to return the actual expression.
    private ASTNode eliminateConstantTernary(ASTNode node) {
        if (node.getChildren().size() < 3) return node;

        ASTNode condWrapper = node.getChildren().get(0);
        ASTNode thenWrapper = node.getChildren().get(1);
        ASTNode elseWrapper = node.getChildren().get(2);

        ASTNode condition = condWrapper.getChildren().isEmpty()
                ? null : condWrapper.getChildren().get(0);

        if (condition != null && isBooleanLiteral(condition)) {
            if (isBooleanValue(condition, "true"))
                return thenWrapper.getChildren().isEmpty()
                        ? thenWrapper : thenWrapper.getChildren().get(0);
            if (isBooleanValue(condition, "false"))
                return elseWrapper.getChildren().isEmpty()
                        ? elseWrapper : elseWrapper.getChildren().get(0);
        }
        return node;
    }

    private ASTNode eliminateConstantIf(ASTNode node) {
        ASTNode condition = getChildOfType(node, "CONDITION");
        if (condition == null || condition.getChildren().isEmpty()) return node;

        ASTNode expr = condition.getChildren().get(0);
        if (expr == null || !isBooleanLiteral(expr)) return node;

        ASTNode thenBranch = getChildOfType(node, "THEN");
        ASTNode elseBranch = getChildOfType(node, "ELSE");

        if (isBooleanValue(expr, "true"))
            return thenBranch != null
                    ? thenBranch
                    : ASTNode.of("BLOCK", "", node.getLine(), node.getColumn());

        if (isBooleanValue(expr, "false"))
            return elseBranch != null ? elseBranch : null;

        return node;
    }

    // Missing 2: while(false) → removed; while(true) → annotated as infinite loop.
    private ASTNode eliminateConstantWhile(ASTNode node) {
        ASTNode condition = getChildOfType(node, "CONDITION");
        if (condition == null || condition.getChildren().isEmpty()) return node;

        ASTNode expr = condition.getChildren().get(0);
        if (expr == null) return node;

        if (isBooleanValue(expr, "false")) return null; // loop never executes

        if (isBooleanValue(expr, "true")) {
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
            String t = children.get(i).getType();
            if ("RETURN".equals(t) || "BREAK".equals(t) || "CONTINUE".equals(t)) {
                cutoff = i;
                break;
            }
        }
        if (cutoff >= 0 && cutoff < children.size() - 1) {
            node.setChildren(new ArrayList<>(children.subList(0, cutoff + 1)));
            fire("dead code eliminated");
        }
        return node;
    }

    // -------------------------------------------------------------------------
    // Warning 1: integer type inference + range checking
    // -------------------------------------------------------------------------

    private enum NumericType { BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, UNKNOWN }

    /**
     * Infers the narrowest integer type whose range covers both operand values.
     * If either value is floating-point, returns DOUBLE.
     */
    private NumericType inferNumericType(String left, String right) {
        if (left.contains(".") || right.contains(".")) return NumericType.DOUBLE;
        try {
            long l = Long.parseLong(left);
            long r = Long.parseLong(right);
            long maxAbs = Math.max(Math.abs(l), Math.abs(r));
            if (maxAbs <= Byte.MAX_VALUE)    return NumericType.BYTE;
            if (maxAbs <= Short.MAX_VALUE)   return NumericType.SHORT;
            if (maxAbs <= Integer.MAX_VALUE) return NumericType.INT;
            return NumericType.LONG;
        } catch (NumberFormatException e) {
            return NumericType.UNKNOWN;
        }
    }

    /**
     * Evaluates a numeric binary operation.
     * Returns "OVERFLOW:<detail>" when the folded integer result is outside
     * the range of the inferred operand type (Warning 1 fix).
     * Returns null if the operator is not handled.
     */
    private String evaluateNumericBinary(String op, String leftVal, String rightVal,
                                         NumericType inferredType) {
        try {
            boolean isFloat = leftVal.contains(".") || rightVal.contains(".");
            if (isFloat) {
                double l = Double.parseDouble(leftVal);
                double r = Double.parseDouble(rightVal);
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
                long l = Long.parseLong(leftVal);
                long r = Long.parseLong(rightVal);

                // Comparisons never overflow — return immediately.
                switch (op) {
                    case "<":  return String.valueOf(l <  r);
                    case ">":  return String.valueOf(l >  r);
                    case "<=": return String.valueOf(l <= r);
                    case ">=": return String.valueOf(l >= r);
                    case "==": return String.valueOf(l == r);
                    case "!=": return String.valueOf(l != r);
                }

                long result;
                switch (op) {
                    case "+": result = l + r; break;
                    case "-": result = l - r; break;
                    case "*": result = l * r; break;
                    case "/": if (r == 0) return null; result = l / r; break;
                    case "%": if (r == 0) return null; result = l % r; break;
                    default:  return null;
                }

                // Warning 1 fix: range-check against the inferred operand type.
                String overflow = checkIntegerRange(result, inferredType);
                if (overflow != null) return "OVERFLOW:" + overflow;
                return String.valueOf(result);
            }
        } catch (NumberFormatException e) {
            // fall through
        }
        return null;
    }

    /**
     * Returns a diagnostic string if {@code result} is outside the range of
     * {@code type}; null if within range.
     */
    private String checkIntegerRange(long result, NumericType type) {
        switch (type) {
            case BYTE:
                if (result < Byte.MIN_VALUE || result > Byte.MAX_VALUE)
                    return result + " out of byte range ["
                            + Byte.MIN_VALUE + ".." + Byte.MAX_VALUE + "]";
                break;
            case SHORT:
                if (result < Short.MIN_VALUE || result > Short.MAX_VALUE)
                    return result + " out of short range ["
                            + Short.MIN_VALUE + ".." + Short.MAX_VALUE + "]";
                break;
            case INT:
                if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE)
                    return result + " out of int range ["
                            + Integer.MIN_VALUE + ".." + Integer.MAX_VALUE + "]";
                break;
            default:
                break; // LONG, FLOAT, DOUBLE, UNKNOWN — no additional check
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Warning 2 fix: formatDouble always preserves float type information
    // -------------------------------------------------------------------------

    /**
     * Replaces removeTrailingZero(). When isFloatContext is true the result
     * always keeps at least one decimal digit so the NUMBER node is inferred
     * as double (not int) by all downstream phases.
     * e.g. 3.0 - 1.0 → "2.0"  not  "2".
     */
    private String formatDouble(double value, boolean isFloatContext) {
        if (isFloatContext && value == (long) value) return (long) value + ".0";
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

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Stamp the optimized-site's source position onto a surviving child node
     * (Suggest 3: coordinates must point to the optimized site, not the sub-expression).
     */
    private ASTNode copyCoords(ASTNode target, ASTNode site) {
        return ASTNode.of(target.getType(), target.getValue(), site.getLine(), site.getColumn());
    }

    private ASTNode getChildOfType(ASTNode node, String type) {
        for (ASTNode child : node.getChildren()) {
            if (type.equals(child.getType())) return child;
        }
        return null;
    }

    private boolean isNumericLiteral(ASTNode node) {
        return node != null && "NUMBER".equals(node.getType());
    }

    /** True only for integer (non-decimal) NUMBER nodes. */
    private boolean isIntegerLiteral(ASTNode node) {
        return isNumericLiteral(node) && !node.getValue().contains(".");
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
}