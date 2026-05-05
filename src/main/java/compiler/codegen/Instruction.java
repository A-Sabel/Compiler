package compiler.codegen;

public final class Instruction {

    public enum Opcode {

        // ── TAC data movement ────────────────────────────────────────────────
        LOAD_CONST, // result = <literal> e.g. t0 = 42
        COPY, // result = src e.g. x = t1

        // ── TAC arithmetic / logic (binary) ─────────────────────────────────
        ADD, // result = left + right
        SUB, // result = left - right
        MUL, // result = left * right
        DIV, // result = left / right
        MOD, // result = left % right
        POW, // result = left ** right

        // ── TAC unary ────────────────────────────────────────────────────────
        NEG, // result = - operand
        NOT, // result = ! operand
        BITWISE_NOT, // result = ~ operand
        CAST, // result = (type) operand

        // ── TAC comparison (binary, result is bool temp) ─────────────────────
        EQUAL, // result = left == right
        NOT_EQUAL, // result = left != right
        LESS_THAN, // result = left < right
        LESS_EQUAL, // result = left <= right
        GREATER_THAN, // result = left > right
        GREATER_EQUAL, // result = left >= right

        // ── TAC array / field ────────────────────────────────────────────────
        ARRAY_LOAD, // result = base[index]
        ARRAY_STORE, // base[index] = src
        FIELD_LOAD, // result = object.field
        FIELD_STORE, // object.field = src

        // ── TAC control flow ─────────────────────────────────────────────────
        LABEL, // LABEL <name> branch target (no-op at runtime)
        JUMP, // goto <label>
        JUMP_IF_TRUE, // ifTrue <cond> goto <label>
        JUMP_IF_FALSE, // ifFalse <cond> goto <label>

        // ── TAC methods / calls ──────────────────────────────────────────────
        METHOD_START, // begin method <name>:<returnType>
        METHOD_END, // end method <name>
        PARAM, // param <name> declare incoming parameter
        ARG, // arg <value> push one call argument
        CALL, // result = call <name> <argCount> (static)
        CALL_VIRTUAL, // result = callvirtual <obj> <name> <argCount>
        // §23.2: descriptor-carrying variants — preferred at all new call sites
        CALL_DESC, // result = call <name> <descriptor> <argCount>
        CALL_VIRTUAL_DESC, // result = callvirtual <obj> <name> <descriptor> <argCount>
        RETURN, // return
        RETURN_VALUE, // return <value>

        // ── TAC objects ───────────────────────────────────────────────────────
        NEW, // result = new <type>(<argCount args already emitted as ARG)
        NEW_ARRAY, // result = new <type>[size]
        PRINT, // print <argCount args already emitted as ARG>
        PRINTLN,

        // ── TAC class structure ───────────────────────────────────────────────
        CLASS, // class <name>
        MAX_STACK,
        HALT,
        MAX_LOCALS,
        LDC,
        INTERN_STRING,

        IADD, ISUB, IMUL, IDIV, IMOD, // int / boolean / byte / short / char
        LADD, LSUB, LMUL, LDIV, LMOD, // long
        FADD, FSUB, FMUL, FDIV, FMOD, // float
        DADD, DSUB, DMUL, DDIV, DMOD, // double

        IAND, LOR, IXOR, // bitwise AND, OR, XOR (integer)
        ISHL, ISHR, IUSHR, // shift operations (logical/arithmetic shift right)

        ILOAD, LLOAD, FLOAD, DLOAD, ALOAD, // local-variable loads
        ISTORE, LSTORE, FSTORE, DSTORE, ASTORE, // local-variable stores

        IALOAD, LALOAD, FALOAD, DALOAD, AALOAD,
        IASTORE, LASTORE, FASTORE, DASTORE, AASTORE,
        IRETURN, LRETURN, FRETURN, DRETURN, ARETURN,

        CONST_PUSH,
        STACK_MAP_FRAME,
        EXCEPTION_TABLE_ENTRY,
        CONVERT,
        FOLDED_CONST,
        DUP,
        LINE_NUMBER,
        LOCAL_VAR_TABLE,
        SOURCE_FILE,
        DEFAULT_CONSTRUCTOR,
        STATIC_INIT,
        FIELD_DEFAULT,
        METHOD_DESCRIPTOR,
    }

    private final Opcode opcode;
    private final String result;
    private final String arg1;
    private final String op;
    private final String arg2;
    private final String descriptor;

    private Instruction(Opcode opcode, String result, String arg1, String op, String arg2) {
        this(opcode, result, arg1, op, arg2, null);
    }

    private Instruction(Opcode opcode, String result, String arg1, String op, String arg2,
            String descriptor) {
        this.opcode = opcode;
        this.result = result;
        this.arg1 = arg1;
        this.op = op;
        this.arg2 = arg2;
        this.descriptor = descriptor;
    }

    public static Instruction loadConst(String result, String literal) {
        return new Instruction(Opcode.LOAD_CONST, result, literal, null, null);
    }

    public static Instruction copy(String result, String src) {
        return new Instruction(Opcode.COPY, result, src, null, null);
    }

    public static Instruction binary(Opcode opcode, String result, String left, String op, String right) {
        return new Instruction(opcode, result, left, op, right);
    }

    public static Instruction unary(Opcode opcode, String result, String operand) {
        return new Instruction(opcode, result, operand, null, null);
    }

    public static Instruction cast(String result, String type, String operand) {
        return new Instruction(Opcode.CAST, result, operand, type, null);
    }

    public static Instruction arrayLoad(String result, String base, String index) {
        return new Instruction(Opcode.ARRAY_LOAD, result, base, null, index);
    }

    public static Instruction arrayStore(String base, String index, String src) {
        return new Instruction(Opcode.ARRAY_STORE, null, base, index, src);
    }

    public static Instruction fieldLoad(String result, String object, String field) {
        return new Instruction(Opcode.FIELD_LOAD, result, object, field, null);
    }

    public static Instruction fieldStore(String object, String field, String src) {
        return new Instruction(Opcode.FIELD_STORE, null, object, field, src);
    }

    public static Instruction label(String name) {
        return new Instruction(Opcode.LABEL, name, null, null, null);
    }

    public static Instruction jump(String label) {
        return new Instruction(Opcode.JUMP, null, label, null, null);
    }

    public static Instruction jumpIfTrue(String cond, String label) {
        return new Instruction(Opcode.JUMP_IF_TRUE, null, cond, label, null);
    }

    public static Instruction jumpIfFalse(String cond, String label) {
        return new Instruction(Opcode.JUMP_IF_FALSE, null, cond, label, null);
    }

    public static Instruction methodStart(String name, String returnType) {
        return new Instruction(Opcode.METHOD_START, null, name, returnType, null);
    }

    public static Instruction methodEnd(String name) {
        return new Instruction(Opcode.METHOD_END, null, name, null, null);
    }

    public static Instruction param(String name) {
        return new Instruction(Opcode.PARAM, null, name, null, null);
    }

    public static Instruction arg(String value) {
        return new Instruction(Opcode.ARG, null, value, null, null);
    }

    public static Instruction call(String result, String name, int argCount) {
        return new Instruction(Opcode.CALL, result, name, String.valueOf(argCount), null);
    }

    public static Instruction callVirtual(String result, String obj, String name, int argCount) {
        return new Instruction(Opcode.CALL_VIRTUAL, result, obj, name, String.valueOf(argCount));
    }

    public static Instruction callWithDescriptor(String result, String name,
            String descriptor, int argCount) {
        return new Instruction(Opcode.CALL_DESC, result, name,
                String.valueOf(argCount), null, descriptor);
    }

    public static Instruction callVirtualWithDescriptor(String result, String obj,
            String name, String descriptor,
            int argCount) {
        return new Instruction(Opcode.CALL_VIRTUAL_DESC, result, obj,
                name, String.valueOf(argCount), descriptor);
    }

    public static Instruction ret() {
        return new Instruction(Opcode.RETURN, null, null, null, null);
    }

    public static Instruction retValue(String value) {
        return new Instruction(Opcode.RETURN_VALUE, null, value, null, null);
    }

    public static Instruction newObj(String result, String type, int argCount) {
        return new Instruction(Opcode.NEW, result, type, String.valueOf(argCount), null);
    }

    public static Instruction newArray(String result, String type, String size) {
        return new Instruction(Opcode.NEW_ARRAY, result, type, null, size);
    }

    public static Instruction print(int argCount) {
        return new Instruction(Opcode.PRINT, null, String.valueOf(argCount), null, null);
    }

    public static Instruction println(int argCount) {
        return new Instruction(Opcode.PRINTLN, null, String.valueOf(argCount), null, null);
    }

    public static Instruction classDecl(String name) {
        return new Instruction(Opcode.CLASS, null, name, null, null);
    }

    public static Instruction maxStack(String methodName, int maxStack) {
        return new Instruction(Opcode.MAX_STACK, null, methodName, String.valueOf(maxStack), null);
    }

    public static Instruction maxLocals(String methodName, int maxLocals) {
        return new Instruction(Opcode.MAX_LOCALS, null, methodName, String.valueOf(maxLocals), null);
    }

    public static Instruction ldc(String result, int poolIndex) {
        return new Instruction(Opcode.LDC, result, String.valueOf(poolIndex), null, null);
    }

    public static Instruction internString(int poolIndex, String value) {
        return new Instruction(Opcode.INTERN_STRING, String.valueOf(poolIndex), value, null, null);
    }

    public static Instruction typedBinary(Opcode opcode, String result, String left, String right) {
        return new Instruction(opcode, result, left, null, right);
    }

    public static Instruction typedLoad(Opcode opcode, String result, String slot) {
        return new Instruction(opcode, result, slot, null, null);
    }

    public static Instruction typedStore(Opcode opcode, String slot, String src) {
        return new Instruction(opcode, null, slot, null, src);
    }

    public static Instruction constPush(String result, String value, String mnemonic) {
        return new Instruction(Opcode.CONST_PUSH, result, value, mnemonic, null);
    }

    public static Instruction stackMapFrame(String labelName, String frameDescriptor) {
        return new Instruction(Opcode.STACK_MAP_FRAME, null, labelName, frameDescriptor, null);
    }

    public static Instruction exceptionTableEntry(String startLabel, String endLabel,
            String handlerLabel, String catchType) {
        return new Instruction(Opcode.EXCEPTION_TABLE_ENTRY,
                null, startLabel, endLabel, handlerLabel + ":" + catchType);
    }

    public static Instruction convert(String result, String src, String mnemonic) {
        return new Instruction(Opcode.CONVERT, result, src, mnemonic, null);
    }

    public static Instruction foldedConst(String result, String foldedValue) {
        return new Instruction(Opcode.FOLDED_CONST, result, foldedValue, null, null);
    }

    public static Instruction dup(String result) {
        return new Instruction(Opcode.DUP, result, null, null, null);
    }

    public static Instruction ishl(String result, String src, String shiftAmount) {
        return new Instruction(Opcode.ISHL, result, src, shiftAmount, null);
    }

    public static Instruction ishr(String result, String src, String shiftAmount) {
        return new Instruction(Opcode.ISHR, result, src, shiftAmount, null);
    }

    public static Instruction lineNumber(int bytecodeOffset, int lineNumber) {
        return new Instruction(Opcode.LINE_NUMBER, null,
                String.valueOf(bytecodeOffset), String.valueOf(lineNumber), null);
    }

    public static Instruction localVarTable(int slot, String varName, String descriptor) {
        return new Instruction(Opcode.LOCAL_VAR_TABLE, null,
                String.valueOf(slot), varName, descriptor);
    }

    public static Instruction sourceFile(String fileName) {
        return new Instruction(Opcode.SOURCE_FILE, null, fileName, null, null);
    }

    public static Instruction defaultConstructor(String className) {
        return new Instruction(Opcode.DEFAULT_CONSTRUCTOR, null, className, null, null);
    }

    public static Instruction staticInit(String className) {
        return new Instruction(Opcode.STATIC_INIT, null, className, null, null);
    }

    public static Instruction fieldDefault(String fieldName, String descriptor) {
        return new Instruction(Opcode.FIELD_DEFAULT, null, fieldName, descriptor, null);
    }

    public static Instruction methodDescriptor(String methodName, String descriptor) {
        return new Instruction(Opcode.METHOD_DESCRIPTOR, null, methodName, descriptor, null);
    }

    public Opcode getOpcode() {
        return opcode;
    }

    public String getResult() {
        return result;
    }

    public String getArg1() {
        return arg1;
    }

    public String getOp() {
        return op;
    }

    public String getArg2() {
        return arg2;
    }

    public String getDescriptor() {
        return descriptor;
    }

    @Override
    public String toString() {
        switch (opcode) {

            case LOAD_CONST:
                return result + " = " + arg1;
            case COPY:
                return result + " = " + arg1;

            case ADD:
            case SUB:
            case MUL:
            case DIV:
            case MOD:
            case POW:
            case EQUAL:
            case NOT_EQUAL:
            case LESS_THAN:
            case LESS_EQUAL:
            case GREATER_THAN:
            case GREATER_EQUAL:
            case IAND:
            case LOR:
            case IXOR:
            case ISHL:
            case ISHR:
            case IUSHR:
                return result + " = " + arg1 + " " + op + " " + arg2;

            case NEG:
                return result + " = -" + arg1;
            case NOT:
                return result + " = !" + arg1;
            case BITWISE_NOT:
                return result + " = ~" + arg1;
            case CAST:
                return result + " = (" + op + ") " + arg1;

            case ARRAY_LOAD:
                return result + " = " + arg1 + "[" + arg2 + "]";
            case ARRAY_STORE:
                return arg1 + "[" + op + "] = " + arg2;
            case FIELD_LOAD:
                return result + " = " + arg1 + "." + op;
            case FIELD_STORE:
                return arg1 + "." + op + " = " + arg2;

            case LABEL:
                return (result != null ? result : arg1) + ":";
            case JUMP:
                return "goto " + arg1;
            case JUMP_IF_TRUE:
                return "ifTrue " + arg1 + " goto " + op;
            case JUMP_IF_FALSE:
                return "ifFalse " + arg1 + " goto " + op;

            case METHOD_START:
                return "begin_method " + arg1 + ":" + op;
            case METHOD_END:
                return "end_method " + arg1;
            case PARAM:
                return "param " + arg1;
            case ARG:
                return "arg " + arg1;

            case CALL:
                return (result != null ? result + " = " : "") + "call " + arg1 + " " + op;
            case CALL_VIRTUAL:
                return (result != null ? result + " = " : "")
                        + "callvirtual " + arg1 + " " + op + " " + arg2;

            case CALL_DESC:
                return (result != null ? result + " = " : "")
                        + "call " + arg1 + " " + descriptor + " (" + op + " args)";
            case CALL_VIRTUAL_DESC:
                return (result != null ? result + " = " : "")
                        + "callvirtual " + arg1 + " " + op + " " + descriptor + " (" + arg2 + " args)";

            case RETURN:
                return "return";
            case RETURN_VALUE:
                return "return " + arg1;
            case NEW:
                return result + " = new " + arg1 + "(" + op + " args)";
            case NEW_ARRAY:
                return result + " = new " + arg1 + "[" + arg2 + "]";
            case PRINT:
                return "print " + arg1;
            case PRINTLN:
                return "println " + arg1;
            case CLASS:
                return "class " + arg1;

            case MAX_STACK:
                return ".maxstack " + op + "  ; method=" + arg1;
            case MAX_LOCALS:
                return ".maxlocals " + op + "  ; method=" + arg1;

            case LDC:
                return result + " = ldc #" + arg1;
            case INTERN_STRING:
                return "pool[" + result + "] = intern(\"" + arg1 + "\")";

            case IADD:
            case ISUB:
            case IMUL:
            case IDIV:
            case IMOD:
            case LADD:
            case LSUB:
            case LMUL:
            case LDIV:
            case LMOD:
            case FADD:
            case FSUB:
            case FMUL:
            case FDIV:
            case FMOD:
            case DADD:
            case DSUB:
            case DMUL:
            case DDIV:
            case DMOD:
                return result + " = " + arg1 + " " + opcode.name().toLowerCase() + " " + arg2;

            case ILOAD:
            case LLOAD:
            case FLOAD:
            case DLOAD:
            case ALOAD:
                return result + " = " + opcode.name().toLowerCase() + " [" + arg1 + "]";
            case ISTORE:
            case LSTORE:
            case FSTORE:
            case DSTORE:
            case ASTORE:
                return opcode.name().toLowerCase() + " [" + arg1 + "] = " + op;
            case IALOAD:
            case LALOAD:
            case FALOAD:
            case DALOAD:
            case AALOAD:
                return result + " = " + arg1 + "[" + arg2 + "]  ; " + opcode.name().toLowerCase();
            case IASTORE:
            case LASTORE:
            case FASTORE:
            case DASTORE:
            case AASTORE:
                return arg1 + "[" + op + "] = " + arg2 + "  ; " + opcode.name().toLowerCase();
            case IRETURN:
            case LRETURN:
            case FRETURN:
            case DRETURN:
            case ARETURN:
                return opcode.name().toLowerCase() + " " + arg1;

            case CONST_PUSH:
                return result + " = " + op + "  ; const_push(" + arg1 + ")";
            case STACK_MAP_FRAME:
                return ".stack_map " + arg1 + " { " + op + " }";
            case EXCEPTION_TABLE_ENTRY:
                return ".exception [" + arg1 + ", " + op + ") -> " + arg2;
            case CONVERT:
                return result + " = " + op + "(" + arg1 + ")";
            case FOLDED_CONST:
                return result + " = " + arg1 + "  ; folded";

            case DUP:
                return (result != null ? result + " = " : "") + "dup";

            case LINE_NUMBER:
                return ".line " + op + "  ; offset=" + arg1;
            case LOCAL_VAR_TABLE:
                return ".local [" + arg1 + "] " + op + " : " + arg2;
            case SOURCE_FILE:
                return ".source \"" + arg1 + "\"";
            case DEFAULT_CONSTRUCTOR:
                return "begin_method <init>:" + arg1 + "  ; synthesised";
            case STATIC_INIT:
                return "begin_method <clinit>:" + arg1;
            case FIELD_DEFAULT:
                return ".field_default " + arg1 + " : " + op;
            case METHOD_DESCRIPTOR:
                return ".descriptor " + arg1 + " " + op;

            default:
                return opcode.name();
        }
    }
}