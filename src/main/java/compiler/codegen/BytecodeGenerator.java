package compiler.codegen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import compiler.codegen.Instruction.Opcode;
import compiler.parser.ast.ASTNode;

public class BytecodeGenerator {

    private static class ConstantPool {

        private final Map<String, Integer> pool = new LinkedHashMap<>();
        private int nextIndex = 0;

        public int internString(String value) {
            String key = "STR:" + value;
            return pool.computeIfAbsent(key, k -> nextIndex++);
        }

        public int addConstant(String key) {
            return pool.computeIfAbsent(key, k -> nextIndex++);
        }

        public int indexOf(String key) {
            return pool.getOrDefault(key, -1);
        }

        public Map<String, Integer> entries() {
            return pool;
        }
    }

    private static class StackTracker {
        private int current  = 0;
        private int maxDepth = 0;
        public void push() { push(1); }

        public void push(int n) {
            current += n;
            if (current > maxDepth) maxDepth = current;
        }

        public void pop(int n) {
            current -= n;
            if (current < 0) current = 0; // guard against tracking imprecision
        }

        public int maxDepth()   { return maxDepth; }
        public int current()    { return current;  }
        public void reset()     { current = 0; maxDepth = 0; }
    }

    private static class SlotAllocator {

        private final Map<String, Integer> slots  = new LinkedHashMap<>();
        private int nextSlot = 0;

        public void reserveThis() {
            slots.put("this", nextSlot++);
        }

        public int allocate(String name, String jvmType) {
            if (slots.containsKey(name)) return slots.get(name);
            int slot = nextSlot;
            slots.put(name, slot);
            nextSlot += (jvmType.equals("J") || jvmType.equals("D")) ? 2 : 1;
            return slot;
        }

        public int slotOf(String name) {
            return slots.getOrDefault(name, -1);
        }

        public int maxLocals() {
            return nextSlot;
        }

        public Map<String, Integer> allSlots() {
            return slots;
        }

        public void reset(boolean isInstanceMethod) {
            slots.clear();
            nextSlot = 0;
            if (isInstanceMethod) reserveThis();
        }
    }

    private final List<Instruction> instructions = new ArrayList<>();
    private int tempCount  = 0;
    private int labelCount = 0;
    private String currentBreakLabel    = null;
    private String currentContinueLabel = null;
    private final ConstantPool constantPool = new ConstantPool();
    private final StackTracker stackTracker = new StackTracker();
    private final SlotAllocator slotAllocator = new SlotAllocator();
    private int currentSourceLine = 0;
    private String sourceFileName = null;
    private boolean unreachable = false;
    private boolean currentMethodIsInstance = true;

    public void setSourceFileName(String name) {
        this.sourceFileName = name;
    }

    public void setCurrentSourceLine(int line) {
        this.currentSourceLine = line;
    }

    public List<Instruction> generate(ASTNode root) {
        instructions.clear();
        tempCount  = 0;
        labelCount = 0;
        currentBreakLabel    = null;
        currentContinueLabel = null;
        unreachable = false;
        visit(root);

        applyPeepholeOptimizations();

        return instructions;
    }

    private String newTemp() { return "t" + (tempCount++); }

    private String newLabel(String prefix) { return prefix + "_" + (labelCount++); }

    private void emit(Instruction instr) {
        if (unreachable && instr.getOpcode() != Opcode.LABEL
                && instr.getOpcode() != Opcode.STACK_MAP_FRAME
                && instr.getOpcode() != Opcode.METHOD_END) {
            return; 
        }

        if (currentSourceLine > 0 && isRealInstruction(instr)) {
            instructions.add(Instruction.lineNumber(instructions.size(), currentSourceLine));
            currentSourceLine = 0; // consumed — wait for next setCurrentSourceLine()
        }
        instructions.add(instr);
    }

    private boolean isRealInstruction(Instruction instr) {
        switch (instr.getOpcode()) {
            case LINE_NUMBER: case LOCAL_VAR_TABLE: case SOURCE_FILE:
            case MAX_STACK:   case MAX_LOCALS:      case STACK_MAP_FRAME:
            case EXCEPTION_TABLE_ENTRY: case METHOD_DESCRIPTOR:
            case LABEL:
                return false;
            default:
                return true;
        }
    }

    public static String toJvmDescriptor(String sourceType) {
        if (sourceType == null) return "V";
        switch (sourceType.trim()) {
            case "int":     case "boolean":
            case "byte":    case "short":    case "char":   return "I";
            case "long":                                     return "J";
            case "float":                                    return "F";
            case "double":                                   return "D";
            case "void":                                     return "V";
            case "String":                                   return "Ljava/lang/String;";
            default:
                // Array types: int[] → [I
                if (sourceType.endsWith("[]")) {
                    return "[" + toJvmDescriptor(sourceType.substring(0, sourceType.length() - 2));
                }
                // Qualified names: java.lang.Object → Ljava/lang/Object;
                return "L" + sourceType.replace('.', '/') + ";";
        }
    }

    public static String buildMethodDescriptor(List<String> paramTypes, String returnType) {
        StringBuilder sb = new StringBuilder("(");
        for (String p : paramTypes) sb.append(toJvmDescriptor(p));
        sb.append(")");
        sb.append(toJvmDescriptor(returnType));
        return sb.toString();
    }

    private Opcode resolveTypedBinaryOpcode(String op, String jvmType) {
        boolean isLong   = jvmType.equals("J");
        boolean isFloat  = jvmType.equals("F");
        boolean isDouble = jvmType.equals("D");

        switch (op) {
            case "+":
                if (isLong)   return Opcode.LADD;
                if (isFloat)  return Opcode.FADD;
                if (isDouble) return Opcode.DADD;
                return Opcode.IADD;
            case "-":
                if (isLong)   return Opcode.LSUB;
                if (isFloat)  return Opcode.FSUB;
                if (isDouble) return Opcode.DSUB;
                return Opcode.ISUB;
            case "*":
                if (isLong)   return Opcode.LMUL;
                if (isFloat)  return Opcode.FMUL;
                if (isDouble) return Opcode.DMUL;
                return Opcode.IMUL;
            case "/":
                if (isLong)   return Opcode.LDIV;
                if (isFloat)  return Opcode.FDIV;
                if (isDouble) return Opcode.DDIV;
                return Opcode.IDIV;
            case "%":
                if (isLong)   return Opcode.LMOD;
                if (isFloat)  return Opcode.FMOD;
                if (isDouble) return Opcode.DMOD;
                return Opcode.IMOD;
            default:
                // Comparisons and logical ops fall back to untyped equivalents
                return binaryOpcode(op);
        }
    }

    private Opcode typedLoadOpcode(String jvmType) {
        switch (jvmType) {
            case "J": return Opcode.LLOAD;
            case "F": return Opcode.FLOAD;
            case "D": return Opcode.DLOAD;
            case "I": return Opcode.ILOAD;
            default:  return Opcode.ALOAD;  // reference types
        }
    }

    private Opcode typedStoreOpcode(String jvmType) {
        switch (jvmType) {
            case "J": return Opcode.LSTORE;
            case "F": return Opcode.FSTORE;
            case "D": return Opcode.DSTORE;
            case "I": return Opcode.ISTORE;
            default:  return Opcode.ASTORE;
        }
    }

    private Opcode typedArrayLoadOpcode(String jvmType) {
        switch (jvmType) {
            case "J": return Opcode.LALOAD;
            case "F": return Opcode.FALOAD;
            case "D": return Opcode.DALOAD;
            case "I": return Opcode.IALOAD;
            default:  return Opcode.AALOAD;
        }
    }

    private Opcode typedArrayStoreOpcode(String jvmType) {
        switch (jvmType) {
            case "J": return Opcode.LASTORE;
            case "F": return Opcode.FASTORE;
            case "D": return Opcode.DASTORE;
            case "I": return Opcode.IASTORE;
            default:  return Opcode.AASTORE;
        }
    }

    private void emitSpecializedConst(String result, String literal, String jvmType) {
        if ("null".equals(literal)) {
            emit(Instruction.constPush(result, "null", "ACONST_NULL"));
            stackTracker.push();
            return;
        }
        if ("true".equals(literal)) {
            emit(Instruction.constPush(result, "1", "ICONST_1"));
            stackTracker.push();
            return;
        }
        if ("false".equals(literal)) {
            emit(Instruction.constPush(result, "0", "ICONST_0"));
            stackTracker.push();
            return;
        }

        if (literal.startsWith("\"") || jvmType.startsWith("L")) {
            int idx = constantPool.internString(literal);
            emit(Instruction.internString(idx, literal));
            emit(Instruction.ldc(result, idx));
            stackTracker.push();
            return;
        }

        try {
            if (jvmType.equals("J")) {
                // long constants
                long lv = Long.parseLong(literal);
                if (lv == 0L) emit(Instruction.constPush(result, literal, "LCONST_0"));
                else if (lv == 1L) emit(Instruction.constPush(result, literal, "LCONST_1"));
                else {
                    int idx = constantPool.addConstant("LONG:" + literal);
                    emit(Instruction.ldc(result, idx));
                }
                stackTracker.push(2); // long occupies 2 stack slots
                return;
            }
            if (jvmType.equals("F")) {
                float fv = Float.parseFloat(literal);
                if (fv == 0f) emit(Instruction.constPush(result, literal, "FCONST_0"));
                else if (fv == 1f) emit(Instruction.constPush(result, literal, "FCONST_1"));
                else if (fv == 2f) emit(Instruction.constPush(result, literal, "FCONST_2"));
                else { int idx = constantPool.addConstant("FLOAT:" + literal); emit(Instruction.ldc(result, idx)); }
                stackTracker.push();
                return;
            }
            if (jvmType.equals("D")) {
                double dv = Double.parseDouble(literal);
                if (dv == 0.0) emit(Instruction.constPush(result, literal, "DCONST_0"));
                else if (dv == 1.0) emit(Instruction.constPush(result, literal, "DCONST_1"));
                else { int idx = constantPool.addConstant("DOUBLE:" + literal); emit(Instruction.ldc(result, idx)); }
                stackTracker.push(2); // double occupies 2 stack slots
                return;
            }
            // Integer range
            int iv = Integer.parseInt(literal);
            String mnemonic;
            if      (iv == -1) mnemonic = "ICONST_M1";
            else if (iv >= 0 && iv <= 5) mnemonic = "ICONST_" + iv;
            else if (iv >= -128 && iv <= 127) mnemonic = "BIPUSH";
            else if (iv >= -32768 && iv <= 32767) mnemonic = "SIPUSH";
            else {

                int idx = constantPool.addConstant("INT:" + literal);
                emit(Instruction.ldc(result, idx));
                stackTracker.push();
                return;
            }
            emit(Instruction.constPush(result, literal, mnemonic));
            stackTracker.push();
        } catch (NumberFormatException e) {
            // Not a number — treat as a plain LOAD_CONST (TAC level)
            emit(Instruction.loadConst(result, literal));
            stackTracker.push();
        }
    }

    public void emitConversion(String result, String src, String fromType, String toType) {
        String mnemonic = fromType + "2" + toType; // e.g. "I2D", "D2I"
        emit(Instruction.convert(result, src, mnemonic));
        stackTracker.pop(1);
        stackTracker.push(toType.equals("J") || toType.equals("D") ? 2 : 1);
    }

    public void emitStackMapFrame(String labelName,
                                    List<String> localTypes,
                                    List<String> stackTypes) {
        String descriptor = "locals:<" + String.join(",", localTypes) + ">"
                + ";stack:<" + String.join(",", stackTypes) + ">";
        emit(Instruction.stackMapFrame(labelName, descriptor));
    }

    public void emitExceptionTableEntry(String startLabel, String endLabel,
                                            String handlerLabel, String catchType) {
        emit(Instruction.exceptionTableEntry(startLabel, endLabel, handlerLabel, catchType));
    }

    private String tryConstantFold(String result, String left, String op, String right) {
        try {
            double l = Double.parseDouble(left);
            double r = Double.parseDouble(right);
            double folded;
            switch (op) {
                case "+": folded = l + r; break;
                case "-": folded = l - r; break;
                case "*": folded = l * r; break;
                case "/":
                    if (r == 0) return null; // avoid division by zero
                    folded = l / r;
                    break;
                case "%":
                    if (r == 0) return null;
                    folded = l % r;
                    break;
                default:  return null;  // not a foldable arithmetic op
            }
            // Emit as integer if the result is whole, otherwise as double
            String foldedStr = (folded == Math.floor(folded) && !Double.isInfinite(folded))
                    ? String.valueOf((long) folded)
                    : String.valueOf(folded);
            emit(Instruction.foldedConst(result, foldedStr));
            stackTracker.push();
            return result;
        } catch (NumberFormatException e) {
            return null; // operands are not numeric literals
        }
    }

    private String tryStrengthReduce(String result, String left, String op, String right) {
        if (!op.equals("*") && !op.equals("/")) return null;
        try {
            long rv = Long.parseLong(right);
            if (rv <= 0) return null;
            // Check power-of-2: rv & (rv-1) == 0
            if ((rv & (rv - 1)) != 0) return null;
            int shift = Long.numberOfTrailingZeros(rv);
            if (op.equals("*")) {
                emit(Instruction.ishl(result, left, String.valueOf(shift)));
            } else {
                emit(Instruction.ishr(result, left, String.valueOf(shift)));
            }
            stackTracker.pop(2);
            stackTracker.push();
            return result;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void applyPeepholeOptimizations() {
        for (int i = 0; i < instructions.size() - 1; i++) {
            Instruction curr = instructions.get(i);
            Instruction next = instructions.get(i + 1);

            boolean currIsCopy = curr.getOpcode() == Opcode.COPY;
            boolean nextIsCopy = next.getOpcode() == Opcode.COPY;

            if (currIsCopy && nextIsCopy
                    && curr.getResult() != null
                    && next.getArg1()   != null
                    && curr.getResult().equals(next.getArg1())) {

                String storeTarget = curr.getResult();
                String loadDest    = next.getResult();
                String originalSrc = curr.getArg1();

                instructions.set(i,     Instruction.dup(loadDest));
                instructions.set(i + 1, Instruction.copy(storeTarget, originalSrc));
                i++; // skip the next instruction we just replaced
            }
        }
    }

    private void synthesizeDefaultConstructor(String className) {
        emit(Instruction.defaultConstructor(className));
        emit(Instruction.param("this"));
        // invoke super.<init>()
        emit(Instruction.callVirtual(null, "this", "<init>", 0));
        emit(Instruction.ret());
        emit(Instruction.methodEnd("<init>"));
    }

    private void beginStaticInit(String className) {
        emit(Instruction.staticInit(className));
    }

    private void emitFieldDefault(String fieldName, String jvmType) {
        emit(Instruction.fieldDefault(fieldName, jvmType));
    }

    private void emitLocalVarTable(Map<String, String> varTypes) {
        for (Map.Entry<String, Integer> e : slotAllocator.allSlots().entrySet()) {
            String name = e.getKey();
            int    slot = e.getValue();
            String desc = varTypes.getOrDefault(name, "I"); // default to int if unknown
            emit(Instruction.localVarTable(slot, name, desc));
        }
    }

    private String visit(ASTNode node) {
        if (node == null) return null;

        switch (node.getType()) {

            case "PROGRAM":
                for (ASTNode child : node.getChildren()) visit(child);
                return null;

            case "CLASS_DECL": {
                String name = getChildValue(node, "NAME");
                if (name == null) name = node.getValue();
                if (sourceFileName != null) emit(Instruction.sourceFile(sourceFileName));
                emit(Instruction.classDecl(name));

                boolean hasStaticField = hasStaticFields(node);
                if (hasStaticField) beginStaticInit(name);

                boolean hasConstructor = hasExplicitConstructor(node);

                visitChildOfType(node, "CLASS_BODY");

                if (!hasConstructor) synthesizeDefaultConstructor(name);

                return null;
            }

            case "CLASS_BODY":
                for (ASTNode child : node.getChildren()) visit(child);
                return null;

            case "METHOD_DECL":
                handleMethodDecl(node);
                return null;

            case "BODY":
            case "BLOCK":
                for (ASTNode child : node.getChildren()) visit(child);
                return null;

            case "EXPR_STMT":
                visit(node.getChildren().get(0));
                return null;

            case "IF_STMT":    handleIf(node);      return null;
            case "WHILE_STMT": handleWhile(node);   return null;
            case "DO_WHILE":   handleDoWhile(node); return null;
            case "FOR":        handleFor(node);     return null;

            case "RETURN": handleReturn(node); return null;

            case "BREAK":
                if (currentBreakLabel == null)
                    throw new RuntimeException("'break' used outside of loop/switch");
                emit(Instruction.jump(currentBreakLabel));
                unreachable = true; // §18.3
                return null;

            case "CONTINUE":
                if (currentContinueLabel == null)
                    throw new RuntimeException("'continue' used outside of loop");
                emit(Instruction.jump(currentContinueLabel));
                unreachable = true; // §18.3
                return null;

            case "SWITCH":     handleSwitch(node); return null;
            case "PRINT_STMT": handlePrint(node);  return null;

            case "VAR_DECL_GROUP":
                for (ASTNode child : node.getChildren()) visit(child);
                return null;

            case "VAR_DECL": handleVarDecl(node); return null;

            case "PARAMS": case "PARAM": case "MODIFIERS":
            case "RETURN_TYPE": case "TYPE": case "NAME":
                return null;

            case "CONDITION": case "THEN": case "ELSE": case "UPDATE":
            case "ARGS": case "RECEIVER": case "CASES": case "CASE_BODY":
            case "INIT": case "EXPR": {
                String last = null;
                for (ASTNode child : node.getChildren()) last = visit(child);
                return last;
            }

            case "ASSIGN":      return handleAssign(node);
            case "TERNARY":     return handleTernary(node);
            case "BINARY_OP":   return handleBinaryOp(node);
            case "UNARY_OP":    return handleUnaryOp(node);
            case "POSTFIX_OP":  return handlePostfixOp(node);
            case "CAST":        return handleCast(node);
            case "NEW":         return handleNew(node);
            case "ARRAY_ACCESS":return handleArrayAccess(node);
            case "METHOD_CALL": return handleMethodCall(node);
            case "FIELD_ACCESS":return handleFieldAccess(node);

            case "IDENTIFIER":
                return node.getValue();

            case "NUMBER":
            case "LITERAL": {
                String t = newTemp();
                String jvmType = inferLiteralType(node.getValue());
                emitSpecializedConst(t, node.getValue(), jvmType);
                return t;
            }

            case "INIT_VALUE":
                return visit(node.getChildren().get(0));

            case "ERROR":
                throw new RuntimeException("Encountered ERROR node: " + node.getValue());

            default:
                throw new RuntimeException("Unknown node type: " + node.getType());
        }
    }

    private void handleMethodDecl(ASTNode node) {
        String name       = getChildValue(node, "NAME");
        if (name == null) name = node.getValue();
        String returnType = getChildValue(node, "RETURN_TYPE");
        if (returnType == null) returnType = "void";

        boolean isInstance = !hasModifier(node, "static");
        currentMethodIsInstance = isInstance;

        slotAllocator.reset(isInstance);
        stackTracker.reset();

        List<String> paramTypes = new ArrayList<>();
        ASTNode params = getChildOfType(node, "PARAMS");
        if (params != null) {
            for (ASTNode param : params.getChildren()) {
                String pType = getChildValue(param, "TYPE");
                paramTypes.add(pType != null ? pType : "int");
            }
        }
        String descriptor = buildMethodDescriptor(paramTypes, returnType);

        emit(Instruction.methodStart(name, returnType));

        emit(Instruction.methodDescriptor(name, descriptor));

        Map<String, String> varTypes = new HashMap<>();
        if (params != null) {
            for (ASTNode param : params.getChildren()) {
                String pName = getChildValue(param, "NAME");
                String pType = getChildValue(param, "TYPE");
                String jvmT  = toJvmDescriptor(pType);
                slotAllocator.allocate(pName, jvmT);
                varTypes.put(pName, jvmT);
                emit(Instruction.param(pName));
            }
        }

        ASTNode classBody = null;

        unreachable = false;
        visitChildOfType(node, "BODY");

        emit(Instruction.maxStack(name, stackTracker.maxDepth()));
        emit(Instruction.maxLocals(name, slotAllocator.maxLocals()));
        emitLocalVarTable(varTypes);
        emit(Instruction.methodEnd(name));
        unreachable = false;
    }

    private void handleVarDecl(ASTNode node) {
        String varName = getChildValue(node, "NAME");
        String varType = getChildValue(node, "TYPE");
        String jvmType = toJvmDescriptor(varType);

        slotAllocator.allocate(varName, jvmType);

        ASTNode initValue = getChildOfType(node, "INIT_VALUE");
        if (initValue != null) {
            String src = visit(initValue);
            Opcode storeOpc = typedStoreOpcode(jvmType);
            emit(Instruction.typedStore(storeOpc,
                    String.valueOf(slotAllocator.slotOf(varName)), src));
            stackTracker.pop(jvmType.equals("J") || jvmType.equals("D") ? 2 : 1);
        } else {
            emitFieldDefault(varName, jvmType);
        }
    }

    private void handleIf(ASTNode node) {
        String elseLabel = newLabel("ELSE");
        String endLabel  = newLabel("IF_END");

        String cond = visit(getChildOfType(node, "CONDITION"));
        stackTracker.pop(1);
        emit(Instruction.jumpIfFalse(cond, elseLabel));

        visit(getChildOfType(node, "THEN"));
        emit(Instruction.jump(endLabel));

        emitStackMapFrame(elseLabel, List.of(), List.of());
        emit(Instruction.label(elseLabel));
        unreachable = false;

        ASTNode elseBranch = getChildOfType(node, "ELSE");
        if (elseBranch != null) visit(elseBranch);

        emitStackMapFrame(endLabel, List.of(), List.of());
        emit(Instruction.label(endLabel));
        unreachable = false;
    }

    private void handleWhile(ASTNode node) {
        String startLabel = newLabel("WHILE_START");
        String endLabel   = newLabel("WHILE_END");

        String prevBreak    = currentBreakLabel;
        String prevContinue = currentContinueLabel;
        currentBreakLabel    = endLabel;
        currentContinueLabel = startLabel;

        emitStackMapFrame(startLabel, List.of(), List.of());
        emit(Instruction.label(startLabel));
        unreachable = false;

        String cond = visit(getChildOfType(node, "CONDITION"));
        stackTracker.pop(1);
        emit(Instruction.jumpIfFalse(cond, endLabel));

        visitBody(node);
        emit(Instruction.jump(startLabel));

        emitStackMapFrame(endLabel, List.of(), List.of());
        emit(Instruction.label(endLabel));
        unreachable = false;

        currentBreakLabel    = prevBreak;
        currentContinueLabel = prevContinue;
    }

    private void handleDoWhile(ASTNode node) {
        String startLabel    = newLabel("DO_START");
        String continueLabel = newLabel("DO_CONTINUE");
        String endLabel      = newLabel("DO_END");

        String prevBreak    = currentBreakLabel;
        String prevContinue = currentContinueLabel;
        currentBreakLabel    = endLabel;
        currentContinueLabel = continueLabel;

        emitStackMapFrame(startLabel, List.of(), List.of()); 
        emit(Instruction.label(startLabel));
        unreachable = false;
        visitBody(node);

        emitStackMapFrame(continueLabel, List.of(), List.of()); 
        emit(Instruction.label(continueLabel));
        unreachable = false;
        String cond = visit(getChildOfType(node, "CONDITION"));
        stackTracker.pop(1);
        emit(Instruction.jumpIfTrue(cond, startLabel));

        emitStackMapFrame(endLabel, List.of(), List.of()); 
        emit(Instruction.label(endLabel));
        unreachable = false;

        currentBreakLabel    = prevBreak;
        currentContinueLabel = prevContinue;
    }

    private void handleFor(ASTNode node) {
        String startLabel    = newLabel("FOR_START");
        String continueLabel = newLabel("FOR_CONTINUE");
        String endLabel      = newLabel("FOR_END");

        String prevBreak    = currentBreakLabel;
        String prevContinue = currentContinueLabel;
        currentBreakLabel    = endLabel;
        currentContinueLabel = continueLabel;

        ASTNode condition = getChildOfType(node, "CONDITION");
        ASTNode update    = getChildOfType(node, "UPDATE");

        for (ASTNode child : node.getChildren()) {
            String t = child.getType();
            if (!t.equals("CONDITION") && !t.equals("UPDATE")
                    && !t.equals("BODY") && !t.equals("BLOCK")) {
                visit(child);
                break;
            }
        }

        emitStackMapFrame(startLabel, List.of(), List.of()); 
        emit(Instruction.label(startLabel));
        unreachable = false;

        if (condition != null) {
            String cond = visit(condition);
            stackTracker.pop(1);
            emit(Instruction.jumpIfFalse(cond, endLabel));
        }
        visitBody(node);

        emitStackMapFrame(continueLabel, List.of(), List.of()); 
        emit(Instruction.label(continueLabel));
        unreachable = false;
        if (update != null) visit(update);
        emit(Instruction.jump(startLabel));

        emitStackMapFrame(endLabel, List.of(), List.of()); 
        emit(Instruction.label(endLabel));
        unreachable = false;

        currentBreakLabel    = prevBreak;
        currentContinueLabel = prevContinue;
    }

    private void handleSwitch(ASTNode node) {
        String endLabel  = newLabel("SWITCH_END");
        String prevBreak = currentBreakLabel;
        currentBreakLabel = endLabel;

        String switchVal = visit(node.getChildren().get(0));
        stackTracker.pop(1);

        ASTNode casesNode = getChildOfType(node, "CASES");
        if (casesNode != null) {
            List<ASTNode> cases      = casesNode.getChildren();
            List<String>  caseLabels = new ArrayList<>();
            String defaultLabel = endLabel;

            for (int i = 0; i < cases.size(); i++) {
                ASTNode c   = cases.get(i);
                String  lbl = newLabel("CASE");
                caseLabels.add(lbl);
                if (c.getType().equals("DEFAULT")) {
                    defaultLabel = lbl;
                } else {
                    String caseVal = newTemp();
                    emit(Instruction.loadConst(caseVal, c.getValue()));
                    stackTracker.push();
                    String cmp = newTemp();
                    emit(Instruction.binary(Opcode.EQUAL, cmp, switchVal, "==", caseVal));
                    stackTracker.pop(2); stackTracker.push();
                    emit(Instruction.jumpIfTrue(cmp, lbl));
                    stackTracker.pop(1);
                }
            }
            emit(Instruction.jump(defaultLabel));

            for (int i = 0; i < cases.size(); i++) {
                emitStackMapFrame(caseLabels.get(i), List.of(), List.of()); 
                emit(Instruction.label(caseLabels.get(i)));
                unreachable = false;
                ASTNode body = getChildOfType(cases.get(i), "CASE_BODY");
                if (body != null) for (ASTNode s : body.getChildren()) visit(s);
            }
        }

        emitStackMapFrame(endLabel, List.of(), List.of()); 
        emit(Instruction.label(endLabel));
        unreachable = false;
        currentBreakLabel = prevBreak;
    }

    private void handleReturn(ASTNode node) {
        if (!node.getChildren().isEmpty()) {
            String val = visit(node.getChildren().get(0));
            emit(Instruction.retValue(val));
            stackTracker.pop(1);
        } else {
            emit(Instruction.ret());
        }
        unreachable = true; 
    }

    private void handlePrint(ASTNode node) {
        ASTNode args = getChildOfType(node, "ARGS");
        int argCount = 0;
        if (args != null) {
            for (ASTNode a : args.getChildren()) {
                String val = visit(a);
                emit(Instruction.arg(val));
                stackTracker.push();
                argCount++;
            }
        }
        emit(Instruction.print(argCount));
        stackTracker.pop(argCount);
    }

    private String handleAssign(ASTNode node) {
        ASTNode left  = node.getChildren().get(0);
        ASTNode right = node.getChildren().get(1);
        String  op    = node.getValue();

        String rhs;
        if (!op.equals("=")) {
            String lhsVal = addressOf(left);
            String rhsVal = visit(right);
            String opStr = op.replace("=", "");
            rhs = newTemp();
            String reduced = tryStrengthReduce(rhs, lhsVal, opStr, rhsVal);
            if (reduced == null) {
                Opcode binOp = resolveTypedBinaryOpcode(opStr, "I");
                emit(Instruction.binary(binOp, rhs, lhsVal, opStr, rhsVal));
                stackTracker.pop(2); stackTracker.push();
            }
        } else {
            rhs = visit(right);
        }

        storeInto(left, rhs);
        return rhs;
    }

    private String handleTernary(ASTNode node) {
        String elseLabel = newLabel("TERNARY_ELSE");
        String endLabel  = newLabel("TERNARY_END");
        String result    = newTemp();

        String cond = visit(getChildOfType(node, "CONDITION"));
        stackTracker.pop(1);
        emit(Instruction.jumpIfFalse(cond, elseLabel));

        String thenVal = visit(getChildOfType(node, "THEN"));
        emit(Instruction.copy(result, thenVal));
        emit(Instruction.jump(endLabel));

        emitStackMapFrame(elseLabel, List.of(), List.of()); 
        emit(Instruction.label(elseLabel));
        unreachable = false;
        String elseVal = visit(getChildOfType(node, "ELSE"));
        emit(Instruction.copy(result, elseVal));

        emitStackMapFrame(endLabel, List.of(), List.of()); 
        emit(Instruction.label(endLabel));
        unreachable = false;
        return result;
    }

    private String handleBinaryOp(ASTNode node) {
        ASTNode leftNode  = node.getChildren().get(0);
        ASTNode rightNode = node.getChildren().get(1);
        String  opStr     = node.getValue();

        if (opStr.equals("&&")) {
            String falseLabel = newLabel("AND_FALSE");
            String endLabel   = newLabel("AND_END");
            String result     = newTemp();

            String leftVal = visit(leftNode);
            stackTracker.pop(1);
            emit(Instruction.jumpIfFalse(leftVal, falseLabel));

            String rightVal = visit(rightNode);
            emit(Instruction.copy(result, rightVal));
            emit(Instruction.jump(endLabel));

            emitStackMapFrame(falseLabel, List.of(), List.of()); 
            emit(Instruction.label(falseLabel));
            unreachable = false;
            String falseTemp = newTemp();
            emitSpecializedConst(falseTemp, "false", "I"); 
            emit(Instruction.copy(result, falseTemp));

            emitStackMapFrame(endLabel, List.of(), List.of()); 
            emit(Instruction.label(endLabel));
            unreachable = false;
            return result;
        }

        if (opStr.equals("||")) {
            String trueLabel = newLabel("OR_TRUE");
            String endLabel  = newLabel("OR_END");
            String result    = newTemp();

            String leftVal = visit(leftNode);
            stackTracker.pop(1);
            emit(Instruction.jumpIfTrue(leftVal, trueLabel));

            String rightVal = visit(rightNode);
            emit(Instruction.copy(result, rightVal));
            emit(Instruction.jump(endLabel));

            emitStackMapFrame(trueLabel, List.of(), List.of()); 
            emit(Instruction.label(trueLabel));
            unreachable = false;
            String trueTemp = newTemp();
            emitSpecializedConst(trueTemp, "true", "I"); 
            emit(Instruction.copy(result, trueTemp));

            emitStackMapFrame(endLabel, List.of(), List.of()); 
            emit(Instruction.label(endLabel));
            unreachable = false;
            return result;
        }

        String left   = visit(leftNode);
        String right  = visit(rightNode);
        String result = newTemp();
        String folded = tryConstantFold(result, left, opStr, right);
        
        if (folded != null) return folded;

        String reduced = tryStrengthReduce(result, left, opStr, right);
        if (reduced != null) return reduced;

        Opcode opc = resolveTypedBinaryOpcode(opStr, "I");
        emit(Instruction.binary(opc, result, left, opStr, right));
        stackTracker.pop(2); stackTracker.push();
        return result;
    }

    private String handleUnaryOp(ASTNode node) {
        ASTNode operand = node.getChildren().get(0);
        String  opStr   = node.getValue();
        String  result  = newTemp();

        switch (opStr) {
            case "-": {
                String val = visit(operand);
                emit(Instruction.unary(Opcode.NEG, result, val));
                stackTracker.pop(1); stackTracker.push();
                break;
            }
            case "!": {
                String val = visit(operand);
                emit(Instruction.unary(Opcode.NOT, result, val));
                stackTracker.pop(1); stackTracker.push();
                break;
            }
            case "~": {
                String val = visit(operand);
                emit(Instruction.unary(Opcode.BITWISE_NOT, result, val));
                stackTracker.pop(1); stackTracker.push();
                break;
            }
            case "++": {
                String addr = addressOf(operand);
                String one  = newTemp();
                emitSpecializedConst(one, "1", "I"); 
                emit(Instruction.binary(Opcode.IADD, result, addr, "+", one));
                stackTracker.pop(2); stackTracker.push();
                storeInto(operand, result);
                break;
            }
            case "--": {
                String addr = addressOf(operand);
                String one  = newTemp();
                emitSpecializedConst(one, "1", "I"); 
                emit(Instruction.binary(Opcode.ISUB, result, addr, "-", one));
                stackTracker.pop(2); stackTracker.push();
                storeInto(operand, result);
                break;
            }
            default:
                throw new RuntimeException("Unknown unary operator: " + opStr);
        }
        return result;
    }

    private String handlePostfixOp(ASTNode node) {
        ASTNode operand = node.getChildren().get(0);
        String  opStr   = node.getValue();

        String original = newTemp();
        String addr     = addressOf(operand);
        emit(Instruction.copy(original, addr));

        String one = newTemp();
        emitSpecializedConst(one, "1", "I"); 

        String updated = newTemp();
        if (opStr.equals("++")) {
            emit(Instruction.binary(Opcode.IADD, updated, addr, "+", one));
        } else if (opStr.equals("--")) {
            emit(Instruction.binary(Opcode.ISUB, updated, addr, "-", one));
        } else {
            throw new RuntimeException("Unknown postfix operator: " + opStr);
        }
        stackTracker.pop(2); stackTracker.push();
        storeInto(operand, updated);
        return original;
    }

    private String handleCast(ASTNode node) {
        String targetType = getChildValue(node, "TYPE");
        String operand    = visit(node.getChildren().get(1));
        String result     = newTemp();
        String toDesc   = toJvmDescriptor(targetType);
        String fromDesc = "I";
        emitConversion(result, operand, fromDesc, toDesc);
        return result;
    }

    private String handleNew(ASTNode node) {
        String type  = getChildValue(node, "TYPE");
        ASTNode args = getChildOfType(node, "ARGS");
        int argCount = 0;
        if (args != null) {
            for (ASTNode a : args.getChildren()) {
                String val = visit(a);
                emit(Instruction.arg(val));
                stackTracker.push();
                argCount++;
            }
        }
        String result = newTemp();
        emit(Instruction.newObj(result, type, argCount));
        stackTracker.pop(argCount); stackTracker.push();
        return result;
    }

    private String handleArrayAccess(ASTNode node) {
        String base  = visit(node.getChildren().get(0));
        String index = visit(node.getChildren().get(1));
        String result = newTemp();
        emit(Instruction.binary(Opcode.ARRAY_LOAD, result, base, null, index));
        stackTracker.pop(2); stackTracker.push();
        return result;
    }

    private String handleMethodCall(ASTNode node) {
        ASTNode receiver    = getChildOfType(node, "RECEIVER");
        ASTNode args        = getChildOfType(node, "ARGS");
        boolean hasReceiver = receiver != null && !receiver.getChildren().isEmpty();

        String objAddr = null;
        if (hasReceiver) {
            objAddr = visit(receiver.getChildren().get(0));
            stackTracker.push();
        }

        int argCount = 0;
        if (args != null) {
            for (ASTNode a : args.getChildren()) {
                String val = visit(a);
                emit(Instruction.arg(val));
                stackTracker.push();
                argCount++;
            }
        }

        String result = newTemp();
        if (hasReceiver) {
            emit(Instruction.callVirtual(result, objAddr, node.getValue(), argCount));
        } else {
            emit(Instruction.call(result, node.getValue(), argCount));
        }
        stackTracker.pop(argCount + (hasReceiver ? 1 : 0));
        stackTracker.push(); // return value
        return result;
    }

    private String handleFieldAccess(ASTNode node) {
        ASTNode receiver = getChildOfType(node, "RECEIVER");
        String objAddr   = null;
        if (receiver != null && !receiver.getChildren().isEmpty())
            objAddr = visit(receiver.getChildren().get(0));

        String result = newTemp();
        emit(Instruction.fieldLoad(result, objAddr != null ? objAddr : "this", node.getValue()));
        stackTracker.push();
        return result;
    }

    private String addressOf(ASTNode node) {
        switch (node.getType()) {
            case "IDENTIFIER":   return node.getValue();
            case "ARRAY_ACCESS": return handleArrayAccess(node);
            case "FIELD_ACCESS": return handleFieldAccess(node);
            default: throw new RuntimeException("Cannot take address of: " + node.getType());
        }
    }

    private void storeInto(ASTNode left, String src) {
        switch (left.getType()) {
            case "IDENTIFIER":
                emit(Instruction.copy(left.getValue(), src));
                break;
            case "ARRAY_ACCESS": {
                String base  = visit(left.getChildren().get(0));
                String index = visit(left.getChildren().get(1));
                emit(Instruction.arrayStore(base, index, src));
                stackTracker.pop(3);
                break;
            }
            case "FIELD_ACCESS": {
                ASTNode recv = getChildOfType(left, "RECEIVER");
                String obj   = (recv != null && !recv.getChildren().isEmpty())
                                ? visit(recv.getChildren().get(0)) : "this";
                emit(Instruction.fieldStore(obj, left.getValue(), src));
                stackTracker.pop(2);
                break;
            }
            default:
                throw new RuntimeException("Invalid assignment target: " + left.getType());
        }
    }

    private Opcode binaryOpcode(String op) {
        switch (op) {
            case "+":  return Opcode.ADD;
            case "-":  return Opcode.SUB;
            case "*":  return Opcode.MUL;
            case "/":  return Opcode.DIV;
            case "%":  return Opcode.MOD;
            case "==": return Opcode.EQUAL;
            case "!=": return Opcode.NOT_EQUAL;
            case "<":  return Opcode.LESS_THAN;
            case ">":  return Opcode.GREATER_THAN;
            case "<=": return Opcode.LESS_EQUAL;
            case ">=": return Opcode.GREATER_EQUAL;
            default:   throw new RuntimeException("Unknown binary operator: " + op);
        }
    }

    private Opcode compoundOpcode(String op) {
        switch (op) {
            case "+=": return Opcode.ADD;
            case "-=": return Opcode.SUB;
            case "*=": return Opcode.MUL;
            case "/=": return Opcode.DIV;
            case "%=": return Opcode.MOD;
            default:   throw new RuntimeException("Unknown compound operator: " + op);
        }
    }

    private static String inferLiteralType(String literal) {
        if (literal == null || "null".equals(literal)) return "A";
        if ("true".equals(literal) || "false".equals(literal)) return "I";
        if (literal.startsWith("\"")) return "Ljava/lang/String;";
        if (literal.endsWith("L") || literal.endsWith("l")) return "J";
        if (literal.endsWith("F") || literal.endsWith("f")) return "F";
        if (literal.contains(".") || literal.endsWith("D") || literal.endsWith("d")) return "D";
        return "I";
    }

    private boolean hasExplicitConstructor(ASTNode classDecl) {
        ASTNode body = getChildOfType(classDecl, "CLASS_BODY");
        if (body == null) return false;
        for (ASTNode child : body.getChildren()) {
            if ("METHOD_DECL".equals(child.getType())) {
                String n = getChildValue(child, "NAME");
                if ("<init>".equals(n) || (n != null && n.equals(getChildValue(classDecl, "NAME")))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasStaticFields(ASTNode classDecl) {
        ASTNode body = getChildOfType(classDecl, "CLASS_BODY");
        if (body == null) return false;
        for (ASTNode child : body.getChildren()) {
            if ("VAR_DECL".equals(child.getType()) && hasModifier(child, "static")) {
                return true;
            }
        }
        return false;
    }

    private boolean hasModifier(ASTNode node, String mod) {
        ASTNode mods = getChildOfType(node, "MODIFIERS");
        if (mods == null) return false;
        for (ASTNode m : mods.getChildren()) {
            if (mod.equalsIgnoreCase(m.getValue())) return true;
        }
        return false;
    }

    private void visitBody(ASTNode node) {
        ASTNode body = getChildOfType(node, "BODY");
        if (body == null) body = getChildOfType(node, "BLOCK");
        if (body != null) visit(body);
    }

    private void visitChildOfType(ASTNode node, String type) {
        ASTNode child = getChildOfType(node, type);
        if (child != null) visit(child);
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

    public ConstantPool getConstantPool() { return constantPool; }

    public void printInstructions(List<Instruction> instructions) {
        for (int i = 0; i < instructions.size(); i++) {
            System.out.printf("%4d  %s%n", i, instructions.get(i));
        }
    }
}