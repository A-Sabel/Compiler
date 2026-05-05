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
    }

    private static class StackTracker {
        private int current = 0;
        private int maxDepth = 0;

        public void push() {
            push(1);
        }

        public void push(int n) {
            current += n;
            if (current > maxDepth)
                maxDepth = current;
        }

        public void pop(int n) {
            current -= n;
            if (current < 0) {
                throw new IllegalStateException(
                        "Stack underflow: attempted to pop " + n +
                                " from depth " + (current + n));
            }
        }

        public int maxDepth() {
            return maxDepth;
        }

        public void reset() {
            current = 0;
            maxDepth = 0;
        }
    }

    private static class SlotAllocator {
        private final Map<String, Integer> slots = new LinkedHashMap<>();
        private final Map<String, String> slotTypes = new LinkedHashMap<>();
        private int nextSlot = 0;

        public void reserveThis() {
            slots.put("this", nextSlot);
            slotTypes.put("this", "A");
            nextSlot++;
        }

        public int allocate(String name, String jvmType) {
            if (slots.containsKey(name))
                return slots.get(name);
            int slot = nextSlot;
            slots.put(name, slot);
            slotTypes.put(name, jvmType);
            nextSlot += (jvmType.equals("J") || jvmType.equals("D")) ? 2 : 1;
            return slot;
        }

        public int slotOf(String name) {
            return slots.getOrDefault(name, -1);
        }

        public String typeOf(String name) {
            return slotTypes.getOrDefault(name, "I");
        }

        public int maxLocals() {
            return nextSlot;
        }

        public Map<String, Integer> allSlots() {
            return slots;
        }

        public void reset(boolean isInstanceMethod) {
            slots.clear();
            slotTypes.clear();
            nextSlot = 0;
            if (isInstanceMethod)
                reserveThis();
        }
    }

    private final List<Instruction> instructions = new ArrayList<>();
    private int tempCount = 0;
    private int labelCount = 0;
    private String currentBreakLabel = null;
    private String currentContinueLabel = null;
    private final ConstantPool constantPool = new ConstantPool();
    private final StackTracker stackTracker = new StackTracker();
    private final SlotAllocator slotAllocator = new SlotAllocator();
    private final Map<String, Map<String, Integer>> classFieldSlots = new HashMap<>();
    private final Map<String, Map<String, String>> classFieldTypes = new HashMap<>();
    private String currentClassName = null;
    private int currentSourceLine = 0;
    private String sourceFileName = null;
    private boolean unreachable = false;
    private Map<String, String> methodVarTypes = null;
    private final Map<String, String> cseResultMap = new HashMap<>();
    private int lambdaCounter = 1;
    private final List<ASTNode> pendingLambdas = new ArrayList<>();

    public void setSourceFileName(String name) {
        this.sourceFileName = name;
    }

    public void setCurrentSourceLine(int line) {
        this.currentSourceLine = line;
    }

    public List<Instruction> generate(ASTNode root) {
        instructions.clear();
        tempCount = 0;
        labelCount = 0;
        currentBreakLabel = null;
        currentContinueLabel = null;
        cseResultMap.clear();
        lambdaCounter = 1;
        pendingLambdas.clear();
        unreachable = false;
        visit(root);
        applyPeepholeOptimizations();
        return instructions;
    }

    private String newTemp() {
        return "t" + (tempCount++);
    }

    private String newLabel(String p) {
        return p + "_" + (labelCount++);
    }

    private void emit(Instruction instr) {
        if (unreachable
                && instr.getOpcode() != Opcode.LABEL
                && instr.getOpcode() != Opcode.STACK_MAP_FRAME
                && instr.getOpcode() != Opcode.METHOD_END
                && instr.getOpcode() != Opcode.EXCEPTION_TABLE_ENTRY) {
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
            case LINE_NUMBER:
            case LOCAL_VAR_TABLE:
            case SOURCE_FILE:
            case MAX_STACK:
            case MAX_LOCALS:
            case STACK_MAP_FRAME:
            case EXCEPTION_TABLE_ENTRY:
            case METHOD_DESCRIPTOR:
            case LABEL:
                return false;
            default:
                return true;
        }
    }

    public static String toJvmDescriptor(String sourceType) {
        if (sourceType == null)
            return "V";
        switch (sourceType.trim()) {
            case "int":
            case "boolean":
            case "byte":
            case "short":
            case "char":
                return "I";
            case "long":
                return "J";
            case "float":
                return "F";
            case "double":
                return "D";
            case "void":
                return "V";
            case "String":
                return "Ljava/lang/String;";
            default:
                if (sourceType.endsWith("[]"))
                    return "[" + toJvmDescriptor(sourceType.substring(0, sourceType.length() - 2));
                return "L" + sourceType.replace('.', '/') + ";";
        }
    }

    public static String buildMethodDescriptor(List<String> paramTypes, String returnType) {
        StringBuilder sb = new StringBuilder("(");
        for (String p : paramTypes)
            sb.append(toJvmDescriptor(p));
        sb.append(")").append(toJvmDescriptor(returnType));
        return sb.toString();
    }

    private Opcode resolveTypedBinaryOpcode(String op, String jvmType) {
        boolean isLong = jvmType.equals("J");
        boolean isFloat = jvmType.equals("F");
        boolean isDouble = jvmType.equals("D");
        switch (op) {
            case "+":
                if (jvmType.equals("Ljava/lang/String;"))
                    return Opcode.ADD;
                return isLong ? Opcode.LADD : isFloat ? Opcode.FADD : isDouble ? Opcode.DADD : Opcode.IADD;

            case "-":
                return isLong ? Opcode.LSUB : isFloat ? Opcode.FSUB : isDouble ? Opcode.DSUB : Opcode.ISUB;
            case "*":
                return isLong ? Opcode.LMUL : isFloat ? Opcode.FMUL : isDouble ? Opcode.DMUL : Opcode.IMUL;
            case "/":
                return isLong ? Opcode.LDIV : isFloat ? Opcode.FDIV : isDouble ? Opcode.DDIV : Opcode.IDIV;
            case "%":
                return isLong ? Opcode.LMOD : isFloat ? Opcode.FMOD : isDouble ? Opcode.DMOD : Opcode.IMOD;
            default:
                return binaryOpcode(op);
        }
    }

    private Opcode typedLoadOpcode(String jvmType) {
        switch (jvmType) {
            case "J":
                return Opcode.LLOAD;
            case "F":
                return Opcode.FLOAD;
            case "D":
                return Opcode.DLOAD;
            case "I":
                return Opcode.ILOAD;
            default:
                return Opcode.ALOAD;
        }
    }

    private Opcode typedStoreOpcode(String jvmType) {
        switch (jvmType) {
            case "J":
                return Opcode.LSTORE;
            case "F":
                return Opcode.FSTORE;
            case "D":
                return Opcode.DSTORE;
            case "I":
                return Opcode.ISTORE;
            default:
                return Opcode.ASTORE;
        }
    }

    private static String inferLiteralType(String literal) {
        if (literal == null || "null".equals(literal))
            return "A";
        if ("true".equals(literal) || "false".equals(literal))
            return "I";
        if (literal.startsWith("\""))
            return "Ljava/lang/String;";
        if (literal.endsWith("L") || literal.endsWith("l"))
            return "J";
        if (literal.endsWith("F") || literal.endsWith("f"))
            return "F";
        if (literal.contains(".") || literal.endsWith("D") || literal.endsWith("d"))
            return "D";
        return "I";
    }

    private String inferNodeJvmType(ASTNode node) {
        if (node == null)
            return "I";
        String resolvedReturnType = node.getAttribute("resolved_return_type");
        if (resolvedReturnType != null) {
            return toJvmDescriptor(resolvedReturnType);
        }
        switch (node.getType()) {
            case "NUMBER":
            case "LITERAL":
                return inferLiteralType(node.getValue());
            case "IDENTIFIER":
                return slotAllocator.typeOf(node.getValue());
            case "METHOD_CALL": {
                String returnType = node.getAttribute("resolved_return_type");
                return returnType != null ? toJvmDescriptor(returnType) : "I";
            }
            case "NEW":
                return "L" + node.getValue().replace('.', '/') + ";";
            case "ARRAY_LITERAL": {
                String arrayReturnType = node.getAttribute("resolved_return_type");
                if (arrayReturnType != null && !arrayReturnType.isBlank())
                    return toJvmDescriptor(arrayReturnType);
                if (node.getChildren().isEmpty())
                    return "[Ljava/lang/Object;";
                String elemType = inferNodeJvmType(node.getChildren().get(0));
                return "[" + normalizeJvmDescriptor(elemType);
            }
            case "BINARY_OP": {
                if ("+".equals(node.getValue())) {
                    if (inferNodeJvmType(node.getChildren().get(0)).equals("Ljava/lang/String;") ||
                            inferNodeJvmType(node.getChildren().get(1)).equals("Ljava/lang/String;")) {
                        return "Ljava/lang/String;";
                    }
                }
            }
            default:
                return "I";
        }
    }

    private String emitWideningIfNeeded(String src, String fromJvmType, String toJvmType) {
        if (fromJvmType.equals(toJvmType))
            return src;
        if (isWideningConversion(fromJvmType, toJvmType)) {
            String result = newTemp();
            emitConversion(result, src, fromJvmType, toJvmType);
            return result;
        }
        return src;
    }

    private boolean isWideningConversion(String from, String to) {
        int fromRank = typeRank(from);
        int toRank = typeRank(to);
        return fromRank >= 0 && toRank >= 0 && toRank > fromRank;
    }

    private int typeRank(String jvmType) {
        switch (jvmType) {
            case "I":
                return 0;
            case "J":
                return 1;
            case "F":
                return 2;
            case "D":
                return 3;
            default:
                return -1;
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
        if (literal.startsWith("\"") || jvmType.equals("Ljava/lang/String;")) {
            String cleanValue = literal;
            if (literal.startsWith("\"") && literal.endsWith("\"") && literal.length() >= 2) {
                cleanValue = literal.substring(1, literal.length() - 1);
            }
            int idx = constantPool.internString(cleanValue);
            emit(Instruction.internString(idx, cleanValue));
            emit(Instruction.ldc(result, idx));
            stackTracker.push();
            return;
        }
        try {
            if (jvmType.equals("J")) {
                long lv = Long.parseLong(literal.replaceAll("[Ll]$", ""));
                if (lv == 0L)
                    emit(Instruction.constPush(result, literal, "LCONST_0"));
                else if (lv == 1L)
                    emit(Instruction.constPush(result, literal, "LCONST_1"));
                else {
                    emit(Instruction.constPush(result, literal, "LDC2_W"));
                }
                stackTracker.push(2);
                return;
            }
            if (jvmType.equals("F")) {
                float fv = Float.parseFloat(literal.replaceAll("[Ff]$", ""));
                if (fv == 0f)
                    emit(Instruction.constPush(result, literal, "FCONST_0"));
                else if (fv == 1f)
                    emit(Instruction.constPush(result, literal, "FCONST_1"));
                else if (fv == 2f)
                    emit(Instruction.constPush(result, literal, "FCONST_2"));
                else {
                    emit(Instruction.constPush(result, literal, "LDC"));
                }
                stackTracker.push();
                return;
            }
            if (jvmType.equals("D")) {
                double dv = Double.parseDouble(literal.replaceAll("[Dd]$", ""));
                if (dv == 0.0)
                    emit(Instruction.constPush(result, literal, "DCONST_0"));
                else if (dv == 1.0)
                    emit(Instruction.constPush(result, literal, "DCONST_1"));
                else {
                    emit(Instruction.constPush(result, literal, "LDC2_W"));
                }
                stackTracker.push(2);
                return;
            }
            int iv = Integer.parseInt(literal);
            String mnemonic;
            if (iv == -1)
                mnemonic = "ICONST_M1";
            else if (iv >= 0 && iv <= 5)
                mnemonic = "ICONST_" + iv;
            else if (iv >= -128 && iv <= 127)
                mnemonic = "BIPUSH";
            else if (iv >= -32768 && iv <= 32767)
                mnemonic = "SIPUSH";
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
        String mnemonic = fromType + "2" + toType;
        emit(Instruction.convert(result, src, mnemonic));
        stackTracker.pop(fromType.equals("J") || fromType.equals("D") ? 2 : 1);
        stackTracker.push(toType.equals("J") || toType.equals("D") ? 2 : 1);
    }

    public void emitStackMapFrame(String labelName, List<String> localTypes, List<String> stackTypes) {
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
                case "+":
                    folded = l + r;
                    break;
                case "-":
                    folded = l - r;
                    break;
                case "*":
                    folded = l * r;
                    break;
                case "/":
                    if (r == 0)
                        return null;
                    folded = l / r;
                    break;
                case "%":
                    if (r == 0)
                        return null;
                    folded = l % r;
                    break;
                default:
                    return null;
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
        if (!op.equals("*") && !op.equals("/"))
            return null;
        try {
            long rv = Long.parseLong(right);
            if (rv <= 0 || (rv & (rv - 1)) != 0)
                return null;
            int shift = Long.numberOfTrailingZeros(rv);
            if (op.equals("*"))
                emit(Instruction.ishl(result, left, String.valueOf(shift)));
            else
                emit(Instruction.ishr(result, left, String.valueOf(shift)));
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
            if (curr.getOpcode() == Opcode.COPY && next.getOpcode() == Opcode.COPY
                    && curr.getResult() != null && next.getArg1() != null
                    && curr.getResult().equals(next.getArg1())) {
                instructions.set(i, Instruction.copy(next.getResult(), curr.getArg1()));
                instructions.remove(i + 1);
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

    private void generateLambdaMethod(ASTNode node) {
        String name = node.getAttribute("lambda_name");
        String returnType = "Object";

        slotAllocator.reset(false);
        stackTracker.reset();

        List<String> paramTypes = new ArrayList<>();
        ASTNode params = getChildOfType(node, "PARAMS");
        if (params != null) {
            for (ASTNode param : params.getChildren()) {
                String pType = getChildValue(param, "TYPE");
                paramTypes.add(pType != null && !pType.equals("var") ? pType : "Object");
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
                String jvmT = toJvmDescriptor(pType != null && !pType.equals("var") ? pType : "Object");
                slotAllocator.allocate(pName, jvmT);
                varTypes.put(pName, jvmT);
                emit(Instruction.param(pName));
            }
        }

        unreachable = false;
        methodVarTypes = varTypes;
        visitChildOfType(node, "BODY");
        methodVarTypes = null;
        unreachable = false;
        emit(Instruction.maxStack(name, stackTracker.maxDepth()));
        emit(Instruction.maxLocals(name, slotAllocator.maxLocals()));
        emitLocalVarTable(varTypes);
        emit(Instruction.methodEnd(name));
    }

    private void emitLocalVarTable(Map<String, String> varTypes) {
        for (Map.Entry<String, Integer> e : slotAllocator.allSlots().entrySet()) {
            String name = e.getKey();
            int slot = e.getValue();
            String desc = varTypes.getOrDefault(name, "I");
            emit(Instruction.localVarTable(slot, name, desc));
        }
    }

    private String visit(ASTNode node) {
        if (node == null)
            return null;

        switch (node.getType()) {

            case "PROGRAM":
                for (ASTNode child : node.getChildren())
                    visit(child);
                flushPendingLambdas();
                return null;

            case "CLASS_DECL": {
                String name = getChildValue(node, "NAME");
                if (name == null)
                    name = node.getValue();
                if (sourceFileName != null)
                    emit(Instruction.sourceFile(sourceFileName));
                emit(Instruction.classDecl(name));
                currentClassName = name;
                if (hasStaticFields(node))
                    beginStaticInit(name);
                boolean hasConstructor = hasExplicitConstructor(node);
                ASTNode body = getChildOfType(node, "CLASS_BODY");
                if (body != null) {
                    // First pass: handle field declarations so slots are allocated
                    for (ASTNode child : body.getChildren()) {
                        String t = child.getType();
                        if (t.equals("VAR_DECL_GROUP") || t.equals("VAR_DECL"))
                            visit(child);
                    }

                    // Snapshot field slots/types for later method codegen
                    Map<String, Integer> slotsSnapshot = new HashMap<>(slotAllocator.allSlots());
                    Map<String, String> typesSnapshot = new HashMap<>();
                    for (String fn : slotsSnapshot.keySet()) {
                        typesSnapshot.put(fn, slotAllocator.typeOf(fn));
                    }
                    classFieldSlots.put(name, slotsSnapshot);
                    classFieldTypes.put(name, typesSnapshot);

                    // Second pass: handle remaining class body members (methods, etc.)
                    for (ASTNode child : body.getChildren()) {
                        String t = child.getType();
                        if (!t.equals("VAR_DECL_GROUP") && !t.equals("VAR_DECL"))
                            visit(child);
                    }
                }
                flushPendingLambdas();
                if (!hasConstructor)
                    synthesizeDefaultConstructor(name);
                currentClassName = null;
                return null;
            }

            case "CLASS_BODY":
                for (ASTNode child : node.getChildren())
                    visit(child);
                return null;

            case "METHOD_DECL":
                handleMethodDecl(node);
                return null;

            case "BODY":
            case "BLOCK":
                for (ASTNode child : node.getChildren())
                    visit(child);
                return null;

            case "EXPR_STMT":
                visit(node.getChildren().get(0));
                return null;

            case "IF_STMT":
                handleIf(node);
                return null;
            case "WHILE_STMT":
                handleWhile(node);
                return null;
            case "DO_WHILE":
                handleDoWhile(node);
                return null;
            case "FOR":
                handleFor(node);
                return null;
            case "RETURN":
                handleReturn(node);
                return null;

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

            case "SWITCH":
                handleSwitch(node);
                return null;
            case "PRINT_STMT":
                handlePrint(node);
                return null;

            case "VAR_DECL_GROUP":
                for (ASTNode child : node.getChildren())
                    visit(child);
                return null;

            case "VAR_DECL":
                handleVarDecl(node);
                return null;
            case "LAMBDA":
                return handleLambda(node);

            case "TRY_STMT":
                handleTry(node);
                return null;

            case "THROW":
                if (!node.getChildren().isEmpty()) {
                    String exObj = visit(node.getChildren().get(0));
                    emit(Instruction.throwException(exObj));
                    stackTracker.pop(1);
                }
                unreachable = true;
                return null;

            case "PARAMS":
            case "PARAM":
            case "MODIFIERS":
            case "RETURN_TYPE":
            case "TYPE":
            case "NAME":
                return null;

            case "CONDITION":
            case "THEN":
            case "ELSE":
            case "UPDATE":
            case "UPDATE_LIST":
            case "ARGS":
            case "RECEIVER":
            case "CASES":
            case "CASE_BODY":
            case "INIT":
            case "EXPR": {
                String last = null;
                for (ASTNode child : node.getChildren())
                    last = visit(child);
                return last;
            }

            case "ASSIGN":
                return handleAssign(node);
            case "TERNARY":
                return handleTernary(node);
            case "BINARY_OP":
                return handleBinaryOp(node);
            case "UNARY_OP":
                return handleUnaryOp(node);
            case "POSTFIX_OP":
                return handlePostfixOp(node);
            case "CAST":
                return handleCast(node);
            case "NEW":
                return handleNew(node);
            case "NEW_ARRAY":
                return handleNewArray(node);
            case "ARRAY_LITERAL":
                return handleArrayLiteral(node);
            case "ARRAY_ACCESS":
                return handleArrayAccess(node);
            case "METHOD_CALL":
                return handleMethodCall(node);
            case "FIELD_ACCESS":
                return handleFieldAccess(node);

            case "IDENTIFIER": {
                String varName = node.getValue();
                int slot = slotAllocator.slotOf(varName);
                if (slot >= 0) {
                    String jvmType = slotAllocator.typeOf(varName);
                    Opcode loadOpc = typedLoadOpcode(jvmType);
                    String result = newTemp();
                    emit(Instruction.typedLoad(loadOpc, result, String.valueOf(slot)));
                    stackTracker.push(jvmType.equals("J") || jvmType.equals("D") ? 2 : 1);
                    return result;
                }
                // Not a local. If we're inside a method, try class-field snapshot
                if (methodVarTypes != null && currentClassName != null) {
                    Map<String, Integer> slots = classFieldSlots.get(currentClassName);
                    Map<String, String> types = classFieldTypes.get(currentClassName);
                    if (slots != null && slots.containsKey(varName)) {
                        // Prefer field load from the class-level container
                        String result = newTemp();
                        emit(Instruction.fieldLoad(result, currentClassName, varName));
                        stackTracker.push(1);
                        return result;
                    }
                }
                return varName;
            }

            case "NUMBER":
            case "LITERAL": {
                String t = newTemp();
                String jvmType = inferLiteralType(node.getValue());
                emitSpecializedConst(t, node.getValue(), jvmType);
                return t;
            }

            case "INIT_VALUE":
                return visit(node.getChildren().get(0));

            case "TEMP_REF":
                // Reference to a cached subexpression (from CSE optimization)
                // Look up the key in our CSE result map to get the temp variable
                String key = node.getValue();
                String cachedTemp = cseResultMap.get(key);
                if (cachedTemp != null) {
                    return cachedTemp;
                }
                // If not found, something went wrong - return a new temp for safety
                return newTemp();

            case "ERROR":
                throw new RuntimeException("Encountered ERROR node: " + node.getValue());

            default:
                throw new RuntimeException("Unknown node type: " + node.getType());
        }
    }

    private String handleLambda(ASTNode node) {
        String lambdaName = "lambda$" + (lambdaCounter++);
        node.setAttribute("lambda_name", lambdaName);
        pendingLambdas.add(node);
        String result = newTemp();
        emit(Instruction.lambdaRef(result, lambdaName));
        stackTracker.push();
        return result;
    }

    private void flushPendingLambdas() {
        for (int i = 0; i < pendingLambdas.size(); i++) {
            generateLambdaMethod(pendingLambdas.get(i));
        }
        pendingLambdas.clear();
    }

    private void handleMethodDecl(ASTNode node) {
        String name = getChildValue(node, "NAME");
        if (name == null)
            name = node.getValue();
        String returnType = getChildValue(node, "RETURN_TYPE");
        if (returnType == null)
            returnType = "void";

        boolean isInstance = !hasModifier(node, "static");
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
        if (isInstance)
            varTypes.put("this", "A");

        if (params != null) {
            for (ASTNode param : params.getChildren()) {
                String pName = getChildValue(param, "NAME");
                String pType = getChildValue(param, "TYPE");
                String jvmT = toJvmDescriptor(pType);
                slotAllocator.allocate(pName, jvmT);
                varTypes.put(pName, jvmT);
                emit(Instruction.param(pName));
            }
        }

        unreachable = false;
        methodVarTypes = varTypes;
        visitChildOfType(node, "BODY");
        methodVarTypes = null;

        unreachable = false; // Reset to ensure method metadata is emitted
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
        if (methodVarTypes != null)
            methodVarTypes.put(varName, jvmType);

        ASTNode initValue = getChildOfType(node, "INIT_VALUE");
        if (initValue != null) {
            String src = visit(initValue);
            // visit() above already pushed the value onto the stack tracker.
            String srcJvmType = inferNodeJvmType(initValue.getChildren().isEmpty()
                    ? initValue
                    : initValue.getChildren().get(0));
            src = emitWideningIfNeeded(src, srcJvmType, jvmType);

            if (methodVarTypes == null && currentClassName != null) {
                // Class-level field with initializer: emit a field store into the
                // class-level container (recorded by class name). Interpreter will
                // initialize class maps before main executes.
                emit(Instruction.fieldStore(currentClassName, varName, src));
                stackTracker.pop(jvmType.equals("J") || jvmType.equals("D") ? 2 : 1);
            } else {
                Opcode storeOpc = typedStoreOpcode(jvmType);
                emit(Instruction.typedStore(storeOpc,
                        String.valueOf(slotAllocator.slotOf(varName)), src));
                // The store consumes the value that visit() pushed.
                stackTracker.pop(jvmType.equals("J") || jvmType.equals("D") ? 2 : 1);
            }
        } else {
            emitFieldDefault(varName, jvmType);
        }
    }

    private void handleIf(ASTNode node) {
        String elseLabel = newLabel("ELSE");
        String endLabel = newLabel("IF_END");

        String cond = visit(getChildOfType(node, "CONDITION"));
        stackTracker.pop(1);
        emit(Instruction.jumpIfFalse(cond, elseLabel));

        visit(getChildOfType(node, "THEN"));
        emit(Instruction.jump(endLabel));

        emitStackMapFrame(elseLabel, List.of(), List.of());
        emit(Instruction.label(elseLabel));
        unreachable = false;

        ASTNode elseBranch = getChildOfType(node, "ELSE");
        if (elseBranch != null)
            visit(elseBranch);

        emitStackMapFrame(endLabel, List.of(), List.of());
        emit(Instruction.label(endLabel));
        unreachable = false;
    }

    private void handleWhile(ASTNode node) {
        String startLabel = newLabel("WHILE_START");
        String endLabel = newLabel("WHILE_END");

        String prevBreak = currentBreakLabel;
        String prevContinue = currentContinueLabel;
        currentBreakLabel = endLabel;
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

        currentBreakLabel = prevBreak;
        currentContinueLabel = prevContinue;
    }

    private void handleDoWhile(ASTNode node) {
        String startLabel = newLabel("DO_START");
        String continueLabel = newLabel("DO_CONTINUE");
        String endLabel = newLabel("DO_END");

        String prevBreak = currentBreakLabel;
        String prevContinue = currentContinueLabel;
        currentBreakLabel = endLabel;
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

        currentBreakLabel = prevBreak;
        currentContinueLabel = prevContinue;
    }

    private void handleFor(ASTNode node) {
        String startLabel = newLabel("FOR_START");
        String continueLabel = newLabel("FOR_CONTINUE");
        String endLabel = newLabel("FOR_END");

        String prevBreak = currentBreakLabel;
        String prevContinue = currentContinueLabel;
        currentBreakLabel = endLabel;
        currentContinueLabel = continueLabel;

        ASTNode condition = getChildOfType(node, "CONDITION");
        ASTNode update = getChildOfType(node, "UPDATE");

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

        // Handle UPDATE or UPDATE_LIST (comma-separated updates)
        if (update != null) {
            if (update.getType().equals("UPDATE_LIST")) {
                // Process each update expression in the list
                for (ASTNode expr : update.getChildren()) {
                    visit(expr);
                }
            } else {
                // Single update expression
                visit(update);
            }
        }

        emit(Instruction.jump(startLabel));

        emitStackMapFrame(endLabel, List.of(), List.of());
        emit(Instruction.label(endLabel));
        unreachable = false;

        currentBreakLabel = prevBreak;
        currentContinueLabel = prevContinue;
    }

    private void handleTry(ASTNode node) {
        String tryStart = newLabel("TRY_START");
        String tryEnd = newLabel("TRY_END");
        String afterAll = newLabel("TRY_AFTER");

        emit(Instruction.label(tryStart));
        unreachable = false;

        ASTNode tryBody = getChildOfType(node, "BODY");
        if (tryBody != null)
            visit(tryBody);

        // Jump then label: exception table range [tryStart, tryEnd) excludes the jump.
        emit(Instruction.jump(afterAll));
        emit(Instruction.label(tryEnd));

        for (ASTNode child : node.getChildren()) {
            if (!"CATCH".equals(child.getType()))
                continue;

            String catchType = getChildValue(child, "TYPE");
            String catchParam = getChildValue(child, "NAME");
            String handlerLabel = newLabel("CATCH_HANDLER");

            emitExceptionTableEntry(tryStart, tryEnd, handlerLabel,
                    catchType != null ? catchType : "java/lang/Exception");

            emitStackMapFrame(handlerLabel, List.of(), List.of());
            emit(Instruction.label(handlerLabel));
            unreachable = false;

            if (catchParam != null) {
                slotAllocator.allocate(catchParam, "A");
                if (methodVarTypes != null)
                    methodVarTypes.put(catchParam, "A");
                // The JVM pushes the exception object onto the operand stack on
                // handler entry; account for that push before the store.
                stackTracker.push();
                emit(Instruction.typedStore(Opcode.ASTORE,
                        String.valueOf(slotAllocator.slotOf(catchParam)), "exception"));
                stackTracker.pop(1);
            }

            ASTNode catchBody = getChildOfType(child, "BODY");
            if (catchBody != null)
                visit(catchBody);

            emit(Instruction.jump(afterAll));
        }

        emitStackMapFrame(afterAll, List.of(), List.of());
        emit(Instruction.label(afterAll));
        unreachable = false;
    }

    private void handleSwitch(ASTNode node) {
        String endLabel = newLabel("SWITCH_END");
        String prevBreak = currentBreakLabel;
        currentBreakLabel = endLabel;

        ASTNode switchExpr = getChildOfType(node, "EXPR");
        if (switchExpr == null)
            throw new RuntimeException("Malformed switch statement: missing switch expression");

        String switchVal = visit(switchExpr);
        stackTracker.pop(1);

        ASTNode casesNode = getChildOfType(node, "CASES");
        if (casesNode != null) {
            List<ASTNode> cases = casesNode.getChildren();
            List<String> caseLabels = new ArrayList<>();
            String defaultLabel = endLabel;

            for (ASTNode c : cases) {
                String lbl = newLabel("CASE");
                caseLabels.add(lbl);
                if (c.getType().equals("DEFAULT")) {
                    defaultLabel = lbl;
                } else {
                    String caseVal = visit(c.getChildren().get(0));
                    stackTracker.push(); // account for the switch value in the comparison
                    String cmp = newTemp();
                    emit(Instruction.binary(Opcode.EQUAL, cmp, switchVal, "==", caseVal));
                    stackTracker.pop(2);
                    stackTracker.push();
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
                if (body != null) {
                    for (ASTNode s : body.getChildren())
                        visit(s);
                }
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
        List<String> printArgs = new ArrayList<>();
        // Flatten any string concatenation in the print arguments
        if (!node.getChildren().isEmpty()) {
            flattenPrintArgs(node.getChildren().get(0), printArgs);
        }

        for (String arg : printArgs) {
            emit(Instruction.arg(arg));
            stackTracker.push();
        }
        String method = node.getValue();
        if (method != null && method.equals("println")) {
            emit(Instruction.println(printArgs.size()));
        } else {
            emit(Instruction.print(printArgs.size()));
        }
        stackTracker.pop(printArgs.size());
    }

    private void flattenPrintArgs(ASTNode node, List<String> args) {
        if ("BINARY_OP".equals(node.getType()) && "+".equals(node.getValue()) &&
                "Ljava/lang/String;".equals(inferNodeJvmType(node))) {
            flattenPrintArgs(node.getChildren().get(0), args);
            flattenPrintArgs(node.getChildren().get(1), args);
        } else {
            args.add(visit(node));
        }
    }

    private String handleAssign(ASTNode node) {
        ASTNode left = node.getChildren().get(0);
        ASTNode right = node.getChildren().get(1);
        String op = node.getValue();

        String rhs;
        if (!op.equals("=")) {
            String lhsVal = addressOf(left);
            String rhsVal = visit(right);
            String opStr = op.replace("=", "");
            String lhsJvmType = slotAllocator.typeOf(left.getType().equals("IDENTIFIER")
                    ? left.getValue()
                    : "");
            rhs = newTemp();
            String reduced = tryStrengthReduce(rhs, lhsVal, opStr, rhsVal);
            if (reduced == null) {
                Opcode binOp = resolveTypedBinaryOpcode(opStr, lhsJvmType);
                emit(Instruction.binary(binOp, rhs, lhsVal, opStr, rhsVal));
                stackTracker.push();
            }
            // Widen compound-op result to the target type if needed.
            String rhsJvmType = inferNodeJvmType(right);
            rhs = emitWideningIfNeeded(rhs, rhsJvmType, lhsJvmType);
        } else {
            rhs = visit(right);
            if (left.getType().equals("IDENTIFIER")) {
                String targetJvmType = slotAllocator.typeOf(left.getValue());
                String srcJvmType = inferNodeJvmType(right);
                rhs = emitWideningIfNeeded(rhs, srcJvmType, targetJvmType);
            }
        }

        storeInto(left, rhs);
        return rhs;
    }

    private String handleTernary(ASTNode node) {
        String elseLabel = newLabel("TERNARY_ELSE");
        String endLabel = newLabel("TERNARY_END");
        String result = newTemp();

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
        ASTNode leftNode = node.getChildren().get(0);
        ASTNode rightNode = node.getChildren().get(1);
        String opStr = node.getValue();

        if (opStr.equals("&&")) {
            String falseLabel = newLabel("AND_FALSE");
            String endLabel = newLabel("AND_END");
            String result = newTemp();
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
            String endLabel = newLabel("OR_END");
            String result = newTemp();
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

        String left = visit(leftNode);
        String right = visit(rightNode);
        String result = newTemp();

        // Fix: Determine the dominant type for the operation including Strings, Longs,
        // and Floats
        String leftJvmType = inferNodeJvmType(leftNode);
        String rightJvmType = inferNodeJvmType(rightNode);
        String dominantType = "I";

        if (leftJvmType.equals("Ljava/lang/String;") || rightJvmType.equals("Ljava/lang/String;")) {
            dominantType = "Ljava/lang/String;";
        } else if (leftJvmType.equals("D") || rightJvmType.equals("D")) {
            dominantType = "D";
        } else if (leftJvmType.equals("J") || rightJvmType.equals("J")) {
            dominantType = "J";
        } else if (leftJvmType.equals("F") || rightJvmType.equals("F")) {
            dominantType = "F";
        }

        String folded = tryConstantFold(result, left, opStr, right);
        if (folded != null) {
            // If this has a CSE key, cache the result
            String cseKey = node.getAttribute("cse_key");
            if (cseKey != null && !cseKey.isEmpty()) {
                cseResultMap.put(cseKey, result);
            }
            return folded;
        }
        String reduced = tryStrengthReduce(result, left, opStr, right);
        if (reduced != null) {
            // If this has a CSE key, cache the result
            String cseKey = node.getAttribute("cse_key");
            if (cseKey != null && !cseKey.isEmpty()) {
                cseResultMap.put(cseKey, result);
            }
            return reduced;
        }
        Opcode opc = resolveTypedBinaryOpcode(opStr, dominantType);
        emit(Instruction.binary(opc, result, left, opStr, right));
        stackTracker.pop(2);
        stackTracker.push();

        // If this has a CSE key, cache the result for future TEMP_REF nodes
        String cseKey = node.getAttribute("cse_key");
        if (cseKey != null && !cseKey.isEmpty()) {
            cseResultMap.put(cseKey, result);
        }

        return result;
    }

    // FIX #3 (prefix ++/--): addressOf(IDENTIFIER) does NOT push onto the stack,
    // so the const push from emitSpecializedConst is only 1 push total, not 2.
    // FIX #2 (postfix ++/--): same — pop(1) after binary, not pop(2).
    private String handleUnaryOp(ASTNode node) {
        ASTNode operand = node.getChildren().get(0);
        String opStr = node.getValue();
        String result = newTemp();

        switch (opStr) {
            case "-": {
                String val = visit(operand);
                emit(Instruction.unary(Opcode.NEG, result, val));
                stackTracker.pop(1);
                stackTracker.push();
                break;
            }
            case "!": {
                String val = visit(operand);
                emit(Instruction.unary(Opcode.NOT, result, val));
                stackTracker.pop(1);
                stackTracker.push();
                break;
            }
            case "~": {
                String val = visit(operand);
                emit(Instruction.unary(Opcode.BITWISE_NOT, result, val));
                stackTracker.pop(1);
                stackTracker.push();
                break;
            }
            case "++": {
                // FIX #3: addressOf does not push; only the const "1" is pushed (1 slot).
                String addr = addressOf(operand);
                String one = newTemp();
                emitSpecializedConst(one, "1", "I"); // pushes 1
                emit(Instruction.binary(Opcode.IADD, result, addr, "+", one));
                stackTracker.pop(1);
                stackTracker.push(); // net: consume the "1", produce result
                storeInto(operand, result);
                break;
            }
            case "--": {
                String addr = addressOf(operand);
                String one = newTemp();
                emitSpecializedConst(one, "1", "I");
                emit(Instruction.binary(Opcode.ISUB, result, addr, "-", one));
                stackTracker.pop(1);
                stackTracker.push();
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
        String opStr = node.getValue();

        // First, visit the operand to get its current value in a temp
        String original = visit(operand); // For IDENTIFIER, this emits ILOAD and returns temp
        stackTracker.pop(1);

        String one = newTemp();
        emitSpecializedConst(one, "1", "I"); // pushes 1
        String updated = newTemp();
        if (opStr.equals("++"))
            emit(Instruction.binary(Opcode.IADD, updated, original, "+", one));
        else if (opStr.equals("--"))
            emit(Instruction.binary(Opcode.ISUB, updated, original, "-", one));
        else
            throw new RuntimeException("Unknown postfix operator: " + opStr);
        stackTracker.pop(1);
        stackTracker.push();

        // Now store the updated value back to the original location
        storeInto(operand, updated);
        return original; // Return the original value (postfix semantics)
    }

    private String handleCast(ASTNode node) {
        String targetType = node.getValue();
        ASTNode operandNode = node.getChildren().get(0);
        String operand = visit(operandNode);
        String result = newTemp();
        String toDesc = toJvmDescriptor(targetType);
        String fromDesc = inferNodeJvmType(operandNode);
        if (!fromDesc.equals(toDesc))
            emitConversion(result, operand, fromDesc, toDesc);
        else
            emit(Instruction.copy(result, operand));
        return result;
    }

    private String handleNew(ASTNode node) {
        String type = node.getValue();
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
        stackTracker.pop(argCount);
        stackTracker.push();
        return result;
    }

    private String handleNewArray(ASTNode node) {
        String arrayType = node.getValue(); // e.g., "int[]"
        String baseType = arrayType.endsWith("[]") ? arrayType.substring(0, arrayType.length() - 2) : arrayType;

        // Get the size expression (first child)
        String size = visit(node.getChildren().get(0));
        stackTracker.pop(1); // size was pushed by visit()

        String result = newTemp();
        // Emit a special instruction for array creation
        emit(Instruction.newArray(result, baseType, size));
        stackTracker.push();
        return result;
    }

    private String handleArrayLiteral(ASTNode node) {
        String resolvedReturnType = node.getAttribute("resolved_return_type");
        String arrayType = resolvedReturnType != null && !resolvedReturnType.isBlank()
                ? resolvedReturnType
                : inferArrayLiteralType(node);
        String baseType = arrayType.endsWith("[]") ? arrayType.substring(0, arrayType.length() - 2) : arrayType;

        int length = node.getChildren().size();
        String sizeConst = newTemp();
        emitSpecializedConst(sizeConst, String.valueOf(length), "I");

        String arrayRef = newTemp();
        emit(Instruction.newArray(arrayRef, baseType, sizeConst));
        stackTracker.pop(1); // consume the size constant
        stackTracker.push(); // push the array reference

        for (int i = 0; i < length; i++) {
            String indexTemp = newTemp();
            emitSpecializedConst(indexTemp, String.valueOf(i), "I");
            String elementValue = visit(node.getChildren().get(i));
            emit(Instruction.arrayStore(arrayRef, indexTemp, elementValue));
            stackTracker.pop(2); // consume base + index
        }

        return arrayRef;
    }

    private String inferArrayLiteralType(ASTNode node) {
        if (node.getChildren().isEmpty())
            return "Object[]";
        String elemJvmType = inferNodeJvmType(node.getChildren().get(0));
        String elementSourceType = jvmDescriptorToSourceType(normalizeJvmDescriptor(elemJvmType));
        return elementSourceType + "[]";
    }

    private String jvmDescriptorToSourceType(String desc) {
        if (desc == null || desc.isBlank())
            return "Object";
        if (desc.startsWith("[")) {
            int dims = 0;
            while (dims < desc.length() && desc.charAt(dims) == '[') {
                dims++;
            }
            String elementDesc = desc.substring(dims);
            String elementSource = jvmDescriptorToSourceType(elementDesc);
            return elementSource + "[]".repeat(Math.max(0, dims));
        }
        switch (desc) {
            case "I":
                return "int";
            case "J":
                return "long";
            case "F":
                return "float";
            case "D":
                return "double";
            case "Z":
                return "boolean";
            case "B":
                return "byte";
            case "C":
                return "char";
            case "S":
                return "short";
            default:
                if (desc.startsWith("L") && desc.endsWith(";")) {
                    return desc.substring(1, desc.length() - 1).replace('/', '.');
                }
                return "Object";
        }
    }

    private String handleArrayAccess(ASTNode node) {
        String base = visit(node.getChildren().get(0)); // pushes 1
        String index = visit(node.getChildren().get(1)); // pushes 1
        String result = newTemp();
        emit(Instruction.binary(Opcode.ARRAY_LOAD, result, base, null, index));
        stackTracker.pop(2);
        stackTracker.push(); // consumes base+index, produces result
        return result;
    }

    private String handleMethodCall(ASTNode node) {
        String methodName = node.getValue();
        int slot = slotAllocator.slotOf(methodName);
        String callTarget = methodName;
        if (slot >= 0) {
            callTarget = newTemp();
            emit(Instruction.typedLoad(Opcode.ALOAD, callTarget, String.valueOf(slot)));
        }

        // Check if this is a built-in function
        if ("print".equals(callTarget) || "pow".equals(callTarget)) {
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
            if ("print".equals(callTarget)) {
                emit(Instruction.print(argCount));
                stackTracker.pop(argCount);
            } else {
                // pow function
                emit(Instruction.call(result, callTarget, argCount));
                stackTracker.pop(argCount);
                stackTracker.push();
            }
            return result;
        }

        ASTNode receiver = getChildOfType(node, "RECEIVER");
        ASTNode args = getChildOfType(node, "ARGS");
        ASTNode methodDecl = getChildOfType(node, "METHOD_DECL");
        boolean hasReceiver = receiver != null && !receiver.getChildren().isEmpty();

        boolean isStatic = false;
        String returnType = "void";
        String resolvedReturnType = node.getAttribute("resolved_return_type");
        if (resolvedReturnType != null) {
            returnType = resolvedReturnType;
        }
        if (methodDecl != null) {
            isStatic = hasModifier(methodDecl, "static");
            if (resolvedReturnType == null) {
                String declaredReturn = getChildValue(methodDecl, "RETURN_TYPE");
                if (declaredReturn != null)
                    returnType = declaredReturn;
            }
        }

        String objAddr = null;
        if (hasReceiver && !isStatic && receiver != null) {
            objAddr = visit(receiver.getChildren().get(0));
            stackTracker.push();
        } else if (!isStatic && !hasReceiver) {
            // Instance method called without explicit receiver: use implicit 'this'
            objAddr = "this";
            stackTracker.push();
        }

        List<String> argTypes = new ArrayList<>();
        int argCount = 0;
        if (args != null) {
            for (ASTNode a : args.getChildren()) {
                String val = visit(a);
                emit(Instruction.arg(val));
                stackTracker.push();
                argCount++;
                argTypes.add(inferNodeJvmType(a));
            }
        }

        String descriptor = buildMethodDescriptorFromJvmTypes(argTypes, toJvmDescriptor(returnType));

        String result = newTemp();
        if (!isStatic && slot < 0) {
            emit(Instruction.callVirtualWithDescriptor(result, objAddr, callTarget, descriptor, argCount));
            stackTracker.pop(argCount + 1); // receiver + arguments
        } else {
            emit(Instruction.callWithDescriptor(result, callTarget, descriptor, argCount));
            stackTracker.pop(argCount);
        }
        stackTracker.push(); // return value (even void methods push a placeholder temp)
        return result;
    }

    private String buildMethodDescriptorFromJvmTypes(List<String> paramTypes, String returnTypeJvm) {
        StringBuilder sb = new StringBuilder("(");
        for (String paramType : paramTypes) {
            sb.append(normalizeJvmDescriptor(paramType));
        }
        sb.append(")").append(normalizeJvmDescriptor(returnTypeJvm, "V"));
        return sb.toString();
    }

    private String normalizeJvmDescriptor(String type) {
        return normalizeJvmDescriptor(type, "I");
    }

    private String normalizeJvmDescriptor(String type, String fallback) {
        if (type == null || type.isBlank()) {
            return fallback;
        }

        String trimmed = type.trim();
        if (trimmed.length() == 1 && "VZCBSIFJD".contains(trimmed)) {
            return trimmed;
        }
        if (trimmed.startsWith("L") && trimmed.endsWith(";")) {
            return trimmed;
        }
        if (trimmed.startsWith("[")) {
            return trimmed;
        }
        return toJvmDescriptor(trimmed);
    }

    private String handleFieldAccess(ASTNode node) {
        ASTNode receiver = getChildOfType(node, "RECEIVER");
        String objAddr = null;
        if (receiver != null && !receiver.getChildren().isEmpty())
            objAddr = visit(receiver.getChildren().get(0));
        String result = newTemp();
        emit(Instruction.fieldLoad(result, objAddr != null ? objAddr : "this", node.getValue()));
        stackTracker.push();
        return result;
    }

    // addressOf: returns the name/temp for the operand WITHOUT pushing to the stack
    // for simple IDENTIFIERs (the value is already in a slot, not on the operand
    // stack).
    private String addressOf(ASTNode node) {
        switch (node.getType()) {
            case "IDENTIFIER":
                return node.getValue(); // no push
            case "ARRAY_ACCESS":
                return handleArrayAccess(node); // pushes 1
            case "FIELD_ACCESS":
                return handleFieldAccess(node); // pushes 1
            default:
                throw new RuntimeException("Cannot take address of: " + node.getType());
        }
    }

    // FIX #1: storeInto for IDENTIFIER uses typed store and only pops what was
    // actually pushed. For a plain identifier assignment the caller (handleAssign,
    // handleUnaryOp, handlePostfixOp) already holds the value in a temp name;
    // the typed store instruction consumes nothing from the conceptual TAC stack
    // because in TAC, stores are register-to-slot, not stack-to-slot. We therefore
    // do NOT call stackTracker.pop here for the IDENTIFIER case — the push/pop is
    // accounted for at the site that produced `src`.
    private void storeInto(ASTNode left, String src) {
        switch (left.getType()) {
            case "IDENTIFIER": {
                String varName = left.getValue();
                int slot = slotAllocator.slotOf(varName);
                if (slot >= 0) {
                    String jvmType = slotAllocator.typeOf(varName);
                    Opcode storeOpc = typedStoreOpcode(jvmType);
                    emit(Instruction.typedStore(storeOpc, String.valueOf(slot), src));
                    // No stackTracker.pop: TAC stores are register→slot; the value
                    // temp was already accounted for by whoever computed it.
                } else {
                    emit(Instruction.copy(varName, src));
                }
                break;
            }
            case "ARRAY_ACCESS": {
                // FIX #6: visit(base) pushes 1, visit(index) pushes 1. `src` is a
                // register name, not an extra stack push. Total pushes here: 2.
                String base = visit(left.getChildren().get(0)); // +1
                String index = visit(left.getChildren().get(1)); // +1
                emit(Instruction.arrayStore(base, index, src));
                stackTracker.pop(2); // consume base + index
                break;
            }
            case "FIELD_ACCESS": {
                ASTNode recv = getChildOfType(left, "RECEIVER");
                String obj = (recv != null && !recv.getChildren().isEmpty())
                        ? visit(recv.getChildren().get(0))
                        : "this";
                emit(Instruction.fieldStore(obj, left.getValue(), src));
                // visit(receiver) pushed 1 (or 0 for "this" fallback).
                if (recv != null && !recv.getChildren().isEmpty())
                    stackTracker.pop(1);
                break;
            }
            default:
                throw new RuntimeException("Invalid assignment target: " + left.getType());
        }
    }

    private Opcode binaryOpcode(String op) {
        switch (op) {
            case "+":
                return Opcode.ADD;
            case "-":
                return Opcode.SUB;
            case "*":
                return Opcode.MUL;
            case "/":
                return Opcode.DIV;
            case "%":
                return Opcode.MOD;
            case "**":
                return Opcode.POW;
            case "==":
                return Opcode.EQUAL;
            case "!=":
                return Opcode.NOT_EQUAL;
            case "<":
                return Opcode.LESS_THAN;
            case ">":
                return Opcode.GREATER_THAN;
            case "<=":
                return Opcode.LESS_EQUAL;
            case ">=":
                return Opcode.GREATER_EQUAL;
            case "&":
                return Opcode.IAND;
            case "|":
                return Opcode.LOR;
            case "^":
                return Opcode.IXOR;
            case "<<":
                return Opcode.ISHL;
            case ">>":
                return Opcode.ISHR;
            case ">>>":
                return Opcode.IUSHR;
            default:
                throw new RuntimeException("Unknown binary operator: " + op);
        }
    }

    private boolean hasExplicitConstructor(ASTNode classDecl) {
        ASTNode body = getChildOfType(classDecl, "CLASS_BODY");
        if (body == null)
            return false;
        for (ASTNode child : body.getChildren()) {
            if ("METHOD_DECL".equals(child.getType())) {
                String n = getChildValue(child, "NAME");
                if ("<init>".equals(n) || (n != null && n.equals(getChildValue(classDecl, "NAME"))))
                    return true;
            }
        }
        return false;
    }

    private boolean hasStaticFields(ASTNode classDecl) {
        ASTNode body = getChildOfType(classDecl, "CLASS_BODY");
        if (body == null)
            return false;
        for (ASTNode child : body.getChildren())
            if ("VAR_DECL".equals(child.getType()) && hasModifier(child, "static"))
                return true;
        return false;
    }

    private boolean hasModifier(ASTNode node, String mod) {
        ASTNode mods = getChildOfType(node, "MODIFIERS");
        if (mods == null)
            return false;
        for (ASTNode m : mods.getChildren())
            if (mod.equalsIgnoreCase(m.getValue()))
                return true;
        return false;
    }

    private void visitBody(ASTNode node) {
        ASTNode body = getChildOfType(node, "BODY");
        if (body == null)
            body = getChildOfType(node, "BLOCK");
        if (body != null)
            visit(body);
    }

    private void visitChildOfType(ASTNode node, String type) {
        ASTNode child = getChildOfType(node, type);
        if (child != null)
            visit(child);
    }

    private String getChildValue(ASTNode node, String type) {
        for (ASTNode child : node.getChildren())
            if (child.getType().equals(type))
                return child.getValue();
        return null;
    }

    private ASTNode getChildOfType(ASTNode node, String type) {
        for (ASTNode child : node.getChildren())
            if (child.getType().equals(type))
                return child;
        return null;
    }

    public void printInstructions(List<Instruction> instructions) {
        for (int i = 0; i < instructions.size(); i++)
            System.out.printf("%4d  %s%n", i, instructions.get(i));
    }
}