package compiler.codegen;

import compiler.parser.ast.ASTNode;
import java.util.ArrayList;
import java.util.List;

public class CodeGenerator {
    private int registerCounter = 0;
    private final List<String> instructions = new ArrayList<>();

    public String generate(ASTNode root) {
        instructions.clear();
        registerCounter = 0;
        visit(root);
        return instructions.isEmpty() ? "; No code generated" : String.join("\n", instructions);
    }

    private String visit(ASTNode node) {
        if (node == null) return null;

        switch (node.getType()) {
            case "PROGRAM", "BLOCK", "BODY", "CLASS_DECL", "CLASS_BODY", "METHOD_DECL", "VAR_DECL_GROUP" -> {
                for (ASTNode child : node.getChildren()) {
                    visit(child);
                }
            }
            case "EXPR_STMT" -> {
                if (!node.getChildren().isEmpty()) {
                    return visit(node.getChildren().get(0));
                }
            }
            case "VAR_DECL" -> {
                if (node.getChildren().size() >= 3) {
                    ASTNode nameNode = node.getChildren().get(1);
                    ASTNode initWrapper = node.getChildren().get(2);
                    ASTNode initExpr = initWrapper.getChildren().isEmpty() ? initWrapper : initWrapper.getChildren().get(0);
                    String rhsReg = visit(initExpr);
                    emit("STORE " + nameNode.getValue() + ", " + rhsReg);
                }
            }
            case "ASSIGN" -> {
                if (node.getChildren().size() >= 2) {
                    ASTNode lhs = node.getChildren().get(0);
                    ASTNode rhs = node.getChildren().get(1);
                    String rhsReg = visit(rhs);
                    emit("STORE " + lhs.getValue() + ", " + rhsReg);
                    return rhsReg;
                }
            }
            case "BINARY_OP" -> {
                if (node.getChildren().size() >= 2) {
                    String leftReg = visit(node.getChildren().get(0));
                    ASTNode rightNode = node.getChildren().get(1);
                    String rightVal = ("NUMBER".equals(rightNode.getType())) ? rightNode.getValue() : visit(rightNode);
                    String mnemonic = switch (node.getValue()) {
                        case "+" -> "ADD";
                        case "-" -> "SUB";
                        case "*" -> "MUL";
                        case "/" -> "DIV";
                        default -> "OP";
                    };
                    emit(mnemonic + " " + leftReg + ", " + leftReg + ", " + rightVal);
                    return leftReg;
                }
            }
            case "NUMBER" -> {
                String reg = nextRegister();
                emit("MOV " + reg + ", " + node.getValue());
                return reg;
            }
            case "IDENTIFIER" -> {
                String iReg = nextRegister();
                emit("LOAD " + iReg + ", " + node.getValue());
                return iReg;
            }
            default -> {
                for (ASTNode child : node.getChildren()) {
                    visit(child);
                }
            }
        }
        return null;
    }

    private String nextRegister() {
        return "R" + (++registerCounter);
    }

    private void emit(String instr) {
        instructions.add(instr);
    }
}