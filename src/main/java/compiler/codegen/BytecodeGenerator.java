package compiler.codegen;

import java.util.ArrayList;
import java.util.List;

import compiler.codegen.Instruction.Opcode;
import compiler.parser.ast.ASTNode;

public class BytecodeGenerator {

    private final List<Instruction> instructions = new ArrayList<>();
    private int labelCount = 0;

    private String currentBreakLabel    = null;
    private String currentContinueLabel = null;

    public List<Instruction> generate(ASTNode root) {
        instructions.clear();
        labelCount = 0;
        currentBreakLabel    = null;
        currentContinueLabel = null;
        visit(root);
        return instructions;
    }

    private void visit(ASTNode node) {
        if (node == null) return;

        switch (node.getType()) {

            case "PROGRAM":
                for (ASTNode child : node.getChildren()) visit(child);
                break;

            case "CLASS_DECL":
                String className = getChildValue(node, "NAME"); if (className == null) className = node.getValue(); emit(Opcode.CLASS, className);
                visitChildOfType(node, "CLASS_BODY");
                break;

            case "CLASS_BODY":
                for (ASTNode child : node.getChildren()) visit(child);
                break;

            case "METHOD_DECL":
                handleMethodDecl(node);
                break;

            case "BODY":
            case "BLOCK":
                for (ASTNode child : node.getChildren()) visit(child);
                break;

            case "EXPR_STMT":
                visit(node.getChildren().get(0));
                emit(Opcode.POP);
                break;

            case "IF_STMT":    handleIf(node);      break;
            case "WHILE_STMT": handleWhile(node);   break;
            case "DO_WHILE":   handleDoWhile(node); break;
            case "FOR":        handleFor(node);     break;

            case "RETURN": handleReturn(node); break;

            case "BREAK":
                if (currentBreakLabel == null)
                    throw new RuntimeException("'break' used outside of loop/switch");
                emit(Opcode.JUMP, currentBreakLabel);
                break;

            case "CONTINUE":
                if (currentContinueLabel == null)
                    throw new RuntimeException("'continue' used outside of loop");
                emit(Opcode.JUMP, currentContinueLabel);
                break;

            case "SWITCH":     handleSwitch(node); break;
            case "PRINT_STMT": handlePrint(node);  break;

            case "VAR_DECL_GROUP":
                for (ASTNode child : node.getChildren()) visit(child);
                break;

            case "VAR_DECL": handleVarDecl(node); break;

            case "PARAMS": case "PARAM": case "MODIFIERS":
            case "RETURN_TYPE": case "TYPE": case "NAME":
                break;

            case "CONDITION": case "THEN": case "ELSE": case "UPDATE":
            case "ARGS": case "RECEIVER": case "CASES": case "CASE_BODY":
            case "INIT": case "EXPR":
                for (ASTNode child : node.getChildren()) visit(child);
                break;

            case "ASSIGN":      handleAssign(node);      break;
            case "TERNARY":     handleTernary(node);     break;
            case "BINARY_OP":   handleBinaryOp(node);    break;
            case "UNARY_OP":    handleUnaryOp(node);     break;
            case "POSTFIX_OP":  handlePostfixOp(node);   break;
            case "CAST":        handleCast(node);        break;
            case "NEW":         handleNew(node);         break;
            case "ARRAY_ACCESS":handleArrayAccess(node); break;
            case "METHOD_CALL": handleMethodCall(node);  break;
            case "FIELD_ACCESS":handleFieldAccess(node); break;

            case "IDENTIFIER":  emit(Opcode.LOAD,       node.getValue()); break;
            case "NUMBER":      emit(Opcode.PUSH,       node.getValue()); break;
            case "LITERAL":     emit(Opcode.PUSH_CONST, node.getValue()); break;

            case "INIT_VALUE":
                visit(node.getChildren().get(0));
                break;

            case "ERROR":
                throw new RuntimeException("Encountered ERROR node: " + node.getValue());

            default:
                throw new RuntimeException("Unknown node type: " + node.getType());
        }
    }

    private void handleMethodDecl(ASTNode node) {
        String name = getChildValue(node, "NAME"); if (name == null) name = node.getValue();
        String returnType = getChildValue(node, "RETURN_TYPE"); if (returnType == null) returnType = "void";

        emit(Opcode.METHOD_START, name + ":" + returnType);

        ASTNode params = getChildOfType(node, "PARAMS");
        if (params != null) {
            for (ASTNode param : params.getChildren()) {
                emit(Opcode.STORE_PARAM, getChildValue(param, "NAME"));
            }
        }

        visitChildOfType(node, "BODY");
        emit(Opcode.METHOD_END, name);
    }

    private void handleIf(ASTNode node) {
        String elseLabel = newLabel("ELSE");
        String endLabel  = newLabel("IF_END");

        visit(getChildOfType(node, "CONDITION"));
        emit(Opcode.JUMP_IF_FALSE, elseLabel);
        visit(getChildOfType(node, "THEN"));
        emit(Opcode.JUMP, endLabel);
        emit(Opcode.LABEL, elseLabel);
        ASTNode elseBranch = getChildOfType(node, "ELSE");
        if (elseBranch != null) visit(elseBranch);
        emit(Opcode.LABEL, endLabel);
    }

    private void handleWhile(ASTNode node) {
        String startLabel = newLabel("WHILE_START");
        String endLabel   = newLabel("WHILE_END");

        String prevBreak = currentBreakLabel; String prevContinue = currentContinueLabel;
        currentBreakLabel = endLabel; currentContinueLabel = startLabel;

        emit(Opcode.LABEL, startLabel);
        visit(getChildOfType(node, "CONDITION"));
        emit(Opcode.JUMP_IF_FALSE, endLabel);
        visitBody(node);
        emit(Opcode.JUMP, startLabel);
        emit(Opcode.LABEL, endLabel);

        currentBreakLabel = prevBreak; currentContinueLabel = prevContinue;
    }

    private void handleDoWhile(ASTNode node) {
        String startLabel    = newLabel("DO_START");
        String continueLabel = newLabel("DO_CONTINUE");
        String endLabel      = newLabel("DO_END");

        String prevBreak = currentBreakLabel; String prevContinue = currentContinueLabel;
        currentBreakLabel = endLabel; currentContinueLabel = continueLabel;

        emit(Opcode.LABEL, startLabel);
        visitBody(node);
        emit(Opcode.LABEL, continueLabel);
        visit(getChildOfType(node, "CONDITION"));
        emit(Opcode.JUMP_IF_TRUE, startLabel);
        emit(Opcode.LABEL, endLabel);

        currentBreakLabel = prevBreak; currentContinueLabel = prevContinue;
    }

    private void handleFor(ASTNode node) {
        String startLabel    = newLabel("FOR_START");
        String continueLabel = newLabel("FOR_CONTINUE");
        String endLabel      = newLabel("FOR_END");

        String prevBreak = currentBreakLabel; String prevContinue = currentContinueLabel;
        currentBreakLabel = endLabel; currentContinueLabel = continueLabel;

        // Init: first child that is not CONDITION/UPDATE/BODY/BLOCK
        ASTNode condition = getChildOfType(node, "CONDITION");
        ASTNode update    = getChildOfType(node, "UPDATE");
        for (ASTNode child : node.getChildren()) {
            String t = child.getType();
            if (!t.equals("CONDITION") && !t.equals("UPDATE")
                    && !t.equals("BODY") && !t.equals("BLOCK")) {
                visit(child); // init
                break;
            }
        }

        emit(Opcode.LABEL, startLabel);
        if (condition != null) { visit(condition); emit(Opcode.JUMP_IF_FALSE, endLabel); }
        visitBody(node);
        emit(Opcode.LABEL, continueLabel);
        if (update != null) { visit(update); emit(Opcode.POP); }
        emit(Opcode.JUMP, startLabel);
        emit(Opcode.LABEL, endLabel);

        currentBreakLabel = prevBreak; currentContinueLabel = prevContinue;
    }

    private void handleSwitch(ASTNode node) {
        String endLabel   = newLabel("SWITCH_END");
        String prevBreak  = currentBreakLabel;
        currentBreakLabel = endLabel;

        visit(node.getChildren().get(0)); // switch expression

        ASTNode casesNode = getChildOfType(node, "CASES");
        if (casesNode != null) {
            List<ASTNode> cases = casesNode.getChildren();
            List<String>  caseLabels = new ArrayList<>();
            String defaultLabel = endLabel;

            for (int i = 0; i < cases.size(); i++) {
                ASTNode c = cases.get(i);
                String lbl = newLabel("CASE");
                caseLabels.add(lbl);
                if (c.getType().equals("DEFAULT")) {
                    defaultLabel = lbl;
                } else {
                    emit(Opcode.DUP);
                    emit(Opcode.PUSH_CONST, c.getValue());
                    emit(Opcode.EQUAL);
                    emit(Opcode.JUMP_IF_TRUE, lbl);
                }
            }
            emit(Opcode.JUMP, defaultLabel);

            for (int i = 0; i < cases.size(); i++) {
                emit(Opcode.LABEL, caseLabels.get(i));
                ASTNode body = getChildOfType(cases.get(i), "CASE_BODY");
                if (body != null) for (ASTNode s : body.getChildren()) visit(s);
            }
        }

        emit(Opcode.POP);
        emit(Opcode.LABEL, endLabel);
        currentBreakLabel = prevBreak;
    }

    private void handleVarDecl(ASTNode node) {
        ASTNode initValue = getChildOfType(node, "INIT_VALUE");
        if (initValue != null) visit(initValue);
        else emit(Opcode.PUSH_CONST, "null");
        emit(Opcode.STORE, getChildValue(node, "NAME"));
    }

    private void handleAssign(ASTNode node) {
        ASTNode left = node.getChildren().get(0);
        ASTNode right = node.getChildren().get(1);
        String op = node.getValue();

        if (!op.equals("=")) {
            visitAsLoad(left);
            visit(right);
            switch (op) {
                case "+=": emit(Opcode.ADD); break;
                case "-=": emit(Opcode.SUB); break;
                case "*=": emit(Opcode.MUL); break;
                case "/=": emit(Opcode.DIV); break;
                case "%=": emit(Opcode.MOD); break;
                default: throw new RuntimeException("Unknown assignment operator: " + op);
            }
        } else {
            visit(right);
        }
        emitStore(left);
    }

    private void visitAsLoad(ASTNode node) {
        switch (node.getType()) {
            case "IDENTIFIER":  emit(Opcode.LOAD, node.getValue()); break;
            case "ARRAY_ACCESS":
                visit(node.getChildren().get(0));
                visit(node.getChildren().get(1));
                emit(Opcode.ARRAY_LOAD);
                break;
            case "FIELD_ACCESS":
                visitChildOfType(node, "RECEIVER");
                emit(Opcode.FIELD_LOAD, node.getValue());
                break;
            default: throw new RuntimeException("Invalid LHS: " + node.getType());
        }
    }

    private void emitStore(ASTNode left) {
        switch (left.getType()) {
            case "IDENTIFIER":  emit(Opcode.STORE, left.getValue()); break;
            case "ARRAY_ACCESS":
                visit(left.getChildren().get(0));
                visit(left.getChildren().get(1));
                emit(Opcode.ARRAY_STORE);
                break;
            case "FIELD_ACCESS":
                visitChildOfType(left, "RECEIVER");
                emit(Opcode.FIELD_STORE, left.getValue());
                break;
            default: throw new RuntimeException("Invalid assignment target: " + left.getType());
        }
    }

    private void handleTernary(ASTNode node) {
        String elseLabel = newLabel("TERNARY_ELSE");
        String endLabel  = newLabel("TERNARY_END");
        visit(getChildOfType(node, "CONDITION"));
        emit(Opcode.JUMP_IF_FALSE, elseLabel);
        visit(getChildOfType(node, "THEN"));
        emit(Opcode.JUMP, endLabel);
        emit(Opcode.LABEL, elseLabel);
        visit(getChildOfType(node, "ELSE"));
        emit(Opcode.LABEL, endLabel);
    }

    private void handleBinaryOp(ASTNode node) {
        ASTNode left  = node.getChildren().get(0);
        ASTNode right = node.getChildren().get(1);

        if (node.getValue().equals("&&")) {
            String falseLabel = newLabel("AND_FALSE");
            String endLabel   = newLabel("AND_END");
            visit(left);
            emit(Opcode.JUMP_IF_FALSE, falseLabel);
            visit(right);
            emit(Opcode.JUMP, endLabel);
            emit(Opcode.LABEL, falseLabel);
            emit(Opcode.PUSH_CONST, "false");
            emit(Opcode.LABEL, endLabel);
            return;
        }
        if (node.getValue().equals("||")) {
            String trueLabel = newLabel("OR_TRUE");
            String endLabel  = newLabel("OR_END");
            visit(left);
            emit(Opcode.JUMP_IF_TRUE, trueLabel);
            visit(right);
            emit(Opcode.JUMP, endLabel);
            emit(Opcode.LABEL, trueLabel);
            emit(Opcode.PUSH_CONST, "true");
            emit(Opcode.LABEL, endLabel);
            return;
        }

        visit(left);
        visit(right);
        switch (node.getValue()) {
            case "+":  emit(Opcode.ADD);           break;
            case "-":  emit(Opcode.SUB);           break;
            case "*":  emit(Opcode.MUL);           break;
            case "/":  emit(Opcode.DIV);           break;
            case "%":  emit(Opcode.MOD);           break;
            case "==": emit(Opcode.EQUAL);         break;
            case "!=": emit(Opcode.NOT_EQUAL);     break;
            case "<":  emit(Opcode.LESS_THAN);     break;
            case ">":  emit(Opcode.GREATER_THAN);  break;
            case "<=": emit(Opcode.LESS_EQUAL);    break;
            case ">=": emit(Opcode.GREATER_EQUAL); break;
            default:   throw new RuntimeException("Unknown binary operator: " + node.getValue());
        }
    }

    private void handleUnaryOp(ASTNode node) {
        ASTNode operand = node.getChildren().get(0);
        switch (node.getValue()) {
            case "-":  visit(operand); emit(Opcode.NEG);         break;
            case "!":  visit(operand); emit(Opcode.NOT);         break;
            case "~":  visit(operand); emit(Opcode.BITWISE_NOT); break;
            case "++":
                visitAsLoad(operand); emit(Opcode.PUSH, "1"); emit(Opcode.ADD);
                emit(Opcode.DUP); emitStore(operand); break;
            case "--":
                visitAsLoad(operand); emit(Opcode.PUSH, "1"); emit(Opcode.SUB);
                emit(Opcode.DUP); emitStore(operand); break;
            default: throw new RuntimeException("Unknown unary operator: " + node.getValue());
        }
    }

    private void handlePostfixOp(ASTNode node) {
        ASTNode operand = node.getChildren().get(0);
        visitAsLoad(operand);
        emit(Opcode.DUP);
        emit(Opcode.PUSH, "1");
        switch (node.getValue()) {
            case "++": emit(Opcode.ADD); break;
            case "--": emit(Opcode.SUB); break;
            default: throw new RuntimeException("Unknown postfix operator: " + node.getValue());
        }
        emitStore(operand);
        // original value from DUP remains on stack
    }

    private void handleCast(ASTNode node) {
        visit(node.getChildren().get(1));
        emit(Opcode.CAST, getChildValue(node, "TYPE"));
    }

    private void handleNew(ASTNode node) {
        ASTNode args = getChildOfType(node, "ARGS");
        int argCount = 0;
        if (args != null) { for (ASTNode a : args.getChildren()) { visit(a); argCount++; } }
        emit(Opcode.NEW, getChildValue(node, "TYPE") + " " + argCount);
    }

    private void handleArrayAccess(ASTNode node) {
        visit(node.getChildren().get(0));
        visit(node.getChildren().get(1));
        emit(Opcode.ARRAY_LOAD);
    }

    private void handleMethodCall(ASTNode node) {
        ASTNode receiver = getChildOfType(node, "RECEIVER");
        ASTNode args     = getChildOfType(node, "ARGS");
        boolean hasReceiver = receiver != null && !receiver.getChildren().isEmpty();
        if (hasReceiver) visit(receiver.getChildren().get(0));
        int argCount = 0;
        if (args != null) { for (ASTNode a : args.getChildren()) { visit(a); argCount++; } }
        if (hasReceiver) emit(Opcode.INVOKE_VIRTUAL, node.getValue() + " " + argCount);
        else             emit(Opcode.INVOKE_STATIC,  node.getValue() + " " + argCount);
    }

    private void handleFieldAccess(ASTNode node) {
        ASTNode receiver = getChildOfType(node, "RECEIVER");
        if (receiver != null && !receiver.getChildren().isEmpty())
            visit(receiver.getChildren().get(0));
        emit(Opcode.FIELD_LOAD, node.getValue());
    }

    private void handleReturn(ASTNode node) {
        if (!node.getChildren().isEmpty()) { visit(node.getChildren().get(0)); emit(Opcode.RETURN_VALUE); }
        else emit(Opcode.RETURN);
    }

    private void handlePrint(ASTNode node) {
        ASTNode args = getChildOfType(node, "ARGS");
        int argCount = 0;
        if (args != null) { for (ASTNode a : args.getChildren()) { visit(a); argCount++; } }
        emit(Opcode.PRINT, String.valueOf(argCount));
    }

    private void emit(Opcode opcode) {
        instructions.add(Instruction.of(opcode));
    }

    private void emit(Opcode opcode, String operand) {
        instructions.add(Instruction.of(opcode, operand));
    }

    private String newLabel(String prefix) {
        return prefix + "_" + (labelCount++);
    }

    private void visitBody(ASTNode node) {
        ASTNode body = getChildOfType(node, "BODY");
        if (body == null) body = getChildOfType(node, "BLOCK");
        if (body != null) visit(body);
    }

    private String getChildValue(ASTNode node, String type) {
        for (ASTNode child : node.getChildren())
            if (child.getType().equals(type)) return child.getValue();
        return null;
    }

    private ASTNode getChildOfType(ASTNode node, String type) {
        for (ASTNode child : node.getChildren())
            if (child.getType().equals(type)) return child;
        return null;
    }

    private void visitChildOfType(ASTNode node, String type) {
        ASTNode child = getChildOfType(node, type);
        if (child != null) visit(child);
    }

    // debug
    public void printInstructions(List<Instruction> instructions) {
        for (int i = 0; i < instructions.size(); i++) {
            System.out.printf("%4d  %s%n", i, instructions.get(i));
        }
    }
}