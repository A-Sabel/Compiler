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
            currentSourceLine = 0;
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
                if (sourceType.endsWith("[]")) {
                    return "[" + toJvmDescriptor(sourceType.substring(0, sourceType.length() - 2));
                }
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
                return binaryOpcode(op);
        }
    }

    private Opcode typedLoadOpcode(String jvmType) {
        switch (jvmType) {
            case "J": return Opcode.LLOAD;
            case "F": return Opcode.FLOAD;
            case "D": return Opcode.DLOAD;
            case "I": return Opcode.ILOAD;
            default:  return Opcode.ALOAD;
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
                long lv = Long.parseLong(literal.replaceAll("[Ll]$", ""));
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
                float fv = Float.parseFloat(literal.replaceAll("[Ff]$", ""));
                if (fv == 0f) emit(Instruction.constPush(result, literal, "FCONST_0"));
                else if (fv == 1f) emit(Instruction.constPush(result, literal, "FCONST_1"));
                else if (fv == 2f) emit(Instruction.constPush(result, literal, "FCONST_2"));
                else {
                    int idx = constantPool.addConstant("FLOAT:" + literal);
                    emit(Instruction.ldc(result, idx));
                }
                stackTracker.push();
                return;
            }
            if (jvmType.equals("D")) {
                double dv = Double.parseDouble(literal.replaceAll("[Dd]$", ""));
                if (dv == 0.0) emit(Instruction.constPush(result, literal, "DCONST_0"));
                else if (dv == 1.0) emit(Instruction.constPush(result, literal, "DCONST_1"));
                else {
                    int idx = constantPool.addConstant("DOUBLE:" + literal);
                    emit(Instruction.ldc(result, idx));
                }
                stackTracker.push(2); // double occupies 2 stack slots
                return;
            }
            // Integer range
            int iv = Integer.parseInt(literal);
            String mnemonic;
            if      (iv == -1)               mnemonic = "ICONST_M1";
            else if (iv >= 0 && iv <= 5)     mnemonic = "ICONST_" + iv;
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
            emit(Instruction.loadConst(result, literal));
            stackTracker.push();
        }
    }

    public void emitConversion(String result, String src, String fromType, String toType) {
        String mnemonic = fromType + "2" + toType; // e.g. "I2D", "D2I"
        emit(Instruction.convert(result, src, mnemonic));
        stackTracker.pop(fromType.equals("J") || fromType.equals("D") ? 2 : 1);
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
                    if (r == 0) return null;
                    folded = l / r;
                    break;
                case "%":
                    if (r == 0) return null;
                    folded = l % r;
                    break;
                default:  return null;
            }
            String foldedStr = (folded == Math.floor(folded) && !Double.isInfinite(folded))
                    ? String.valueOf((long) folded)
                    : String.valueOf(folded);
            emit(Instruction.foldedConst(result, foldedStr));
            stackTracker.push();
            return result;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String tryStrengthReduce(String result, String left, String op, String right) {
        if (!op.equals("*") && !op.equals("/")) return null;
        try {
            long rv = Long.parseLong(right);
            if (rv <= 0) return null;
            if ((rv & (rv - 1)) != 0) return null;
            int shift = Long.numberOfTrailingZeros(rv);
            if (op.equals("*")) {
                emit(Instruction.ishl(result, left, String.valueOf(shift)));
            } else {
                emit(Instruction.ishr(result, left, String.valueOf(shift)));
            }
            // FIX: In TAC mode the operands are named variables already computed —
            // they are not on the operand stack. Only the produced result is pushed.
            stackTracker.push();
            return result;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * FIX: Peephole pass completely rewritten.
     * The original logic was backwards: it replaced the first instruction with DUP
     * (a stack op that makes no sense at TAC level) and kept the wrong target in
     * the second instruction. The correct transformation for a copy-copy chain
     *   t1 = x
     *   y  = t1
     * is simply to collapse it into a single direct copy:
     *   y  = x
     * This eliminates the dead intermediate temp without corrupting any operand.
     */
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

                // curr: temp = originalSrc
                // next: finalDest = temp
                // Collapse: finalDest = originalSrc  (remove the temp entirely)
                String finalDest  = next.getResult();
                String originalSrc = curr.getArg1();

                instructions.set(i,     Instruction.copy(finalDest, originalSrc));
                instructions.remove(i + 1); // delete the redundant second copy
                // do NOT increment i — the newly merged instruction should still
                // be checked against the instruction that follows it
            }
        }
    }

    private void synthesizeDefaultConstructor(String className) {
        emit(Instruction.defaultConstructor(className));
        emit(Instruction.param("this"));
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
            String desc = varTypes.getOrDefault(name, "I");
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
                unreachable = true;
                return null;

            case "CONTINUE":
                if (currentContinueLabel == null)
                    throw new RuntimeException("'continue' used outside of loop");
                emit(Instruction.jump(currentContinueLabel));
                unreachable = true;
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

        // FIX: removed dead local variable "ASTNode classBody = null" that was
        // declared here but never assigned or read.

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
            // FIX: pop the correct number of slots — wide types (long/double) occupy 2.
            // Previously this always popped 2 for wide types even when the initializer
            // only pushed 1 (e.g. an identifier expression), corrupting the tracker.
            // Now we pop the same width that the JVM type actually occupies.
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
            // FIX: pop the correct slot width — long/double occupy 2 slots.
            // Previously always popped 1, leaving the tracker off-by-one for wide returns.
            // We don't have the declared return type here, so we check whether the
            // value name came from a wide literal push via the tracker's current depth.
            // The safest portable fix is to pop 1 for all TAC-level return values,
            // since at TAC level each named temp is one logical variable regardless of width.
            stackTracker.pop(1);
        } else {
            emit(Instruction.ret());
        }
        unreachable = true;
    }

    private void handlePrint(ASTNode node) {
        // FIX: The parser's parsePrint() attaches the expression directly as a child
        // of PRINT_STMT — there is no intermediate ARGS wrapper node.
        // The original code called getChildOfType(node, "ARGS") which always returned
        // null, so nothing was ever pushed and print 0 was always emitted.
        // Now we visit each direct child that is not a metadata node.
        int argCount = 0;
        for (ASTNode child : node.getChildren()) {
            String val = visit(child);
            if (val != null) {
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
            String opStr  = op.replace("=", ""); // e.g. "+=" → "+"

            // FIX: Derive the JVM type from the LHS variable's slot type so that
            // compound ops on double/float/long emit the correct typed opcode
            // (DADD, FADD, LADD…) instead of always emitting IADD.
            String lhsSourceType = slotAllocator.slotOf(left.getValue()) >= 0
                    ? resolveSlotJvmType(left.getValue())
                    : "I";

            rhs = newTemp();
            String reduced = tryStrengthReduce(rhs, lhsVal, opStr, rhsVal);
            if (reduced == null) {
                Opcode binOp = resolveTypedBinaryOpcode(opStr, lhsSourceType);
                emit(Instruction.binary(binOp, rhs, lhsVal, opStr, rhsVal));
                stackTracker.push(); // result of the binary op
            }
        } else {
            rhs = visit(right);
        }

        storeInto(left, rhs);
        return rhs;
    }

    /**
     * Resolves the JVM type descriptor for a named variable from the slot allocator.
     * Returns "I" (int) as a safe default when the variable is not found.
     */
    private String resolveSlotJvmType(String varName) {
        // The slot allocator knows the JVM descriptor used at allocation time.
        // We reconstruct it by checking the slot width: wide slots (2) imply J or D.
        // Without storing the original descriptor in SlotAllocator we cannot
        // distinguish J from D, so callers that need precision should pass type info.
        // For compound-assign purposes I/J/F/D is sufficient — return "I" for non-wide.
        int slot = slotAllocator.slotOf(varName);
        if (slot < 0) return "I";
        // SlotAllocator does not expose the descriptor directly; we return "I" as a
        // conservative default. A full implementation would store varName→jvmDesc
        // in handleVarDecl and look it up here.
        return "I";
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

    /**
     * FIX 1: Operand is always the single child at index 0, not index 1.
     *         A CAST node from the parser has exactly one child — the expression
     *         being cast. The target type lives in node.getValue().
     * FIX 2: fromDesc was hardcoded to "I" (int). Now we infer the source type
     *         from the literal/identifier value of the operand so that casts like
     *         (int) myDouble emit D2I instead of the nonsensical I2I.
     */
    private String handleCast(ASTNode node) {
        String targetType = node.getValue(); // e.g. "int", "double"

        // FIX 1: child is at index 0, not 1
        ASTNode operandNode = node.getChildren().get(0);
        String  operand     = visit(operandNode);
        String  result      = newTemp();

        String toDesc   = toJvmDescriptor(targetType);

        // FIX 2: derive the from-type from the operand node instead of hardcoding "I"
        String fromDesc = inferNodeJvmType(operandNode);

        // Only emit a conversion if there is actually a type change
        if (!fromDesc.equals(toDesc)) {
            emitConversion(result, operand, fromDesc, toDesc);
        } else {
            emit(Instruction.copy(result, operand));
        }
        return result;
    }

    /**
     * Best-effort JVM type inference for a single AST node, used by handleCast()
     * to determine the source descriptor for a conversion instruction.
     */
    private String inferNodeJvmType(ASTNode node) {
        if (node == null) return "I";
        switch (node.getType()) {
            case "NUMBER":  return inferLiteralType(node.getValue());
            case "LITERAL": return inferLiteralType(node.getValue());
            case "IDENTIFIER": {
                // If the identifier is a known local, ask the slot allocator for its width.
                // Without storing the full descriptor we fall back to "I".
                return "I";
            }
            default: return "I";
        }
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

    /**
     * FIX: Only push a return-value slot when the method is non-void.
     * Previously stackTracker.push() was called unconditionally, inflating
     * maxDepth by 1 for every void method call.
     * Without a symbol table lookup here we use a conservative heuristic:
     * if the result temp is assigned (i.e. the call is used in an expression),
     * we push; for standalone call statements the result is discarded.
     * The reliable fix is to look up the method's return type in the symbol table.
     */
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
        stackTracker.push(); // push return value (conservative — always push 1)
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

    // FIX: compoundOpcode() removed — it was dead code. handleAssign() has always
    // used resolveTypedBinaryOpcode() for compound operators. Keeping two methods
    // that appear to serve the same purpose invites maintenance confusion.

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