package compiler.vm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import compiler.codegen.Instruction;
import compiler.codegen.Instruction.Opcode;

public class Interpreter {
    private final Map<Integer, Object> constPool = new HashMap<>();
    private final Map<String, MethodInfo> methodTable = new HashMap<>();
    private final Map<Integer, MethodInfo> methodsByStartIndex = new HashMap<>();
    private final List<ExceptionEntry> exceptionTable = new ArrayList<>();
    private final Map<String, Integer> labelIndices = new HashMap<>();
    private final Map<String, Map<String, Object>> classStatics = new HashMap<>();

    public Object execute(List<Instruction> instructions) {
        buildMetadata(instructions);
        ExecutionContext context = new ExecutionContext(null);
        // Seed class-level maps into the root context so FIELD_STORE/FIELD_LOAD
        // at top-level and from methods can resolve class fields by class name.
        for (Map.Entry<String, Map<String, Object>> e : classStatics.entrySet()) {
            context.variables.put(e.getKey(), e.getValue());
        }
        executeRange(instructions, 0, instructions.size(), context);

        MethodInfo entrypoint = findEntrypoint("main");
        if (entrypoint != null) {
            Object result = invokeMethod(instructions, entrypoint, false, null, List.of());
            if (result != null) {
                context.returned = true;
                context.returnValue = result;
            }
        }

        return context.returned ? context.returnValue : null;
    }

    private void executeRange(List<Instruction> instructions, int start, int end, ExecutionContext context) {
        int pc = start;
        while (pc < end) {
            Instruction instr = instructions.get(pc);
            Opcode opcode = instr.getOpcode();

            try {
                switch (opcode) {
                    case PARAM:
                    case METHOD_START:
                    case METHOD_END:
                    case METHOD_DESCRIPTOR:
                    case CLASS:
                    case SOURCE_FILE:
                    case LINE_NUMBER:
                    case LOCAL_VAR_TABLE:
                    case MAX_STACK:
                    case MAX_LOCALS:
                    case STACK_MAP_FRAME:
                    case EXCEPTION_TABLE_ENTRY:
                        if (opcode == Opcode.METHOD_START && context.methodInfo == null) {
                            MethodInfo methodInfo = methodsByStartIndex.get(pc);
                            if (methodInfo != null) {
                                pc = methodInfo.bodyEnd + 1;
                                continue;
                            }
                        }
                        break;

                    case ARG: {
                        Object val = resolveValue(instr.getArg1(), context);
                        context.pendingArgs.add(val);
                        break;
                    }

                    case ILOAD:
                    case LLOAD:
                    case FLOAD:
                    case DLOAD:
                    case ALOAD: {
                        context.temps.put(instr.getResult(),
                                context.variables.getOrDefault(slotKey(instr.getArg1()), 0));
                        break;
                    }

                    case ISTORE:
                    case LSTORE:
                    case FSTORE:
                    case DSTORE:
                    case ASTORE: {
                        context.variables.put(slotKey(instr.getArg1()), resolveValue(instr.getArg2(), context));
                        break;
                    }

                    case ARRAY_LOAD: {
                        Object base = resolveValue(instr.getArg1(), context);
                        int idx = toNumber(resolveValue(instr.getArg2(), context)).intValue();
                        Object value = null;
                        if (base instanceof List) {
                            List<?> list = (List<?>) base;
                            if (idx >= 0 && idx < list.size())
                                value = list.get(idx);
                        }
                        context.temps.put(instr.getResult(), value);
                        break;
                    }

                    case ARRAY_STORE: {
                        Object base = resolveValue(instr.getArg1(), context);
                        int idx = toNumber(resolveValue(instr.getOp(), context)).intValue();
                        Object value = resolveValue(instr.getArg2(), context);
                        if (base instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<Object> list = (List<Object>) base;
                            if (idx >= 0 && idx < list.size())
                                list.set(idx, value);
                        }
                        break;
                    }

                    case FIELD_LOAD: {
                        Object objectValue = resolveValue(instr.getArg1(), context);
                        Object fieldValue = null;
                        if (objectValue instanceof RuntimeObject) {
                            fieldValue = ((RuntimeObject) objectValue).fields.get(instr.getOp());
                        } else if (objectValue instanceof Map) {
                            fieldValue = ((Map<?, ?>) objectValue).get(instr.getOp());
                        }
                        context.temps.put(instr.getResult(), fieldValue);
                        break;
                    }

                    case FIELD_STORE: {
                        Object objectValue = resolveValue(instr.getArg1(), context);
                        Object fieldValue = resolveValue(instr.getArg2(), context);
                        if (objectValue instanceof RuntimeObject) {
                            ((RuntimeObject) objectValue).fields.put(instr.getOp(), fieldValue);
                        } else if (objectValue instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> map = (Map<String, Object>) objectValue;
                            map.put(instr.getOp(), fieldValue);
                        }

                        break;
                    }

                    case NEW: {
                        RuntimeObject object = new RuntimeObject(instr.getArg1());
                        context.temps.put(instr.getResult(), object);
                        break;
                    }

                    case NEW_ARRAY: {
                        String elementType = instr.getArg1();
                        Object sizeObj = resolveValue(instr.getArg2(), context);
                        int size = toNumber(sizeObj).intValue();
                        List<Object> array = new ArrayList<>();
                        for (int i = 0; i < size; i++) {
                            array.add(null); // Initialize with nulls
                        }
                        context.temps.put(instr.getResult(), array);
                        break;
                    }

                    case CONST_PUSH: {
                        context.temps.put(instr.getResult(), parseLiteral(instr.getArg1()));
                        break;
                    }

                    case INTERN_STRING: {
                        constPool.put(Integer.parseInt(instr.getResult()), unescape(instr.getArg1()));
                        break;
                    }

                    case LDC: {
                        context.temps.put(instr.getResult(), constPool.get(Integer.parseInt(instr.getArg1())));
                        break;
                    }

                    case COPY: {
                        context.temps.put(instr.getResult(), resolveValue(instr.getArg1(), context));
                        break;
                    }

                    case IADD:
                    case ISUB:
                    case IMUL:
                    case IDIV:
                    case IMOD: {
                        Object a = resolveValue(instr.getArg1(), context);
                        Object b = resolveValue(instr.getArg2(), context);
                        Number an = toNumber(a);
                        Number bn = toNumber(b);
                        long left = an.longValue();
                        long right = bn.longValue();
                        long result;
                        if (opcode == Opcode.IADD)
                            result = left + right;
                        else if (opcode == Opcode.ISUB)
                            result = left - right;
                        else if (opcode == Opcode.IMUL)
                            result = left * right;
                        else if (opcode == Opcode.IDIV)
                            result = left / right;
                        else
                            result = left % right;
                        context.temps.put(instr.getResult(), (int) result);
                        break;
                    }

                    case POW: {
                        Object a = resolveValue(instr.getArg1(), context);
                        Object b = resolveValue(instr.getArg2(), context);
                        Number an = toNumber(a);
                        Number bn = toNumber(b);
                        double base = an.doubleValue();
                        double exponent = bn.doubleValue();
                        double result = Math.pow(base, exponent);
                        context.temps.put(instr.getResult(), (int) result);
                        break;
                    }

                    case IAND:
                    case LOR:
                    case IXOR:
                    case ISHL:
                    case ISHR:
                    case IUSHR: {
                        Object left = resolveValue(instr.getArg1(), context);
                        Object right = resolveValue(instr.getArg2(), context);
                        long leftVal = toNumber(left).longValue();
                        long rightVal = toNumber(right).longValue();
                        int result;

                        if (opcode == Opcode.IAND) {
                            result = (int) (leftVal & rightVal);
                        } else if (opcode == Opcode.LOR) {
                            result = (int) (leftVal | rightVal);
                        } else if (opcode == Opcode.IXOR) {
                            result = (int) (leftVal ^ rightVal);
                        } else if (opcode == Opcode.ISHL) {
                            result = (int) (leftVal << rightVal);
                        } else if (opcode == Opcode.ISHR) {
                            result = (int) (leftVal >> rightVal);
                        } else {
                            result = (int) (leftVal >>> rightVal);
                        }

                        context.temps.put(instr.getResult(), result);
                        break;
                    }

                    case EQUAL:
                    case NOT_EQUAL:
                    case LESS_THAN:
                    case LESS_EQUAL:
                    case GREATER_THAN:
                    case GREATER_EQUAL: {
                        Object left = resolveValue(instr.getArg1(), context);
                        Object right = resolveValue(instr.getArg2(), context);
                        boolean result;

                        if (opcode == Opcode.EQUAL) {
                            result = Objects.equals(left, right);
                        } else if (opcode == Opcode.NOT_EQUAL) {
                            result = !Objects.equals(left, right);
                        } else {
                            Number leftNumber = toNumber(left);
                            Number rightNumber = toNumber(right);
                            double leftValue = leftNumber.doubleValue();
                            double rightValue = rightNumber.doubleValue();

                            if (opcode == Opcode.LESS_THAN) {
                                result = leftValue < rightValue;
                            } else if (opcode == Opcode.LESS_EQUAL) {
                                result = leftValue <= rightValue;
                            } else if (opcode == Opcode.GREATER_THAN) {
                                result = leftValue > rightValue;
                            } else {
                                result = leftValue >= rightValue;
                            }
                        }

                        context.temps.put(instr.getResult(), result);
                        break;
                    }

                    case LABEL:
                        break;

                    case JUMP: {
                        pc = findLabel(instructions, instr.getArg1());
                        continue;
                    }

                    case JUMP_IF_TRUE: {
                        if (isTruthy(resolveValue(instr.getArg1(), context))) {
                            pc = findLabel(instructions, instr.getOp());
                            continue;
                        }
                        break;
                    }

                    case JUMP_IF_FALSE: {
                        if (!isTruthy(resolveValue(instr.getArg1(), context))) {
                            pc = findLabel(instructions, instr.getOp());
                            continue;
                        }
                        break;
                    }

                    case CALL:
                    case CALL_DESC: {
                        int argCount = parseArgCount(instr.getOp());
                        List<Object> args = consumeArgs(context, argCount);
                        String rawName = instr.getArg1();
                        Object resolved = resolveValue(rawName, context);
                        String name = (resolved instanceof String) ? (String) resolved : rawName;
                        Object returnValue = invokeCall(instructions, name, instr.getDescriptor(), false, null, args);
                        if (instr.getResult() != null) {
                            context.temps.put(instr.getResult(), returnValue);
                        }
                        break;
                    }

                    case CALL_VIRTUAL:
                    case CALL_VIRTUAL_DESC: {
                        int argCount = parseArgCount(instr.getArg2());
                        List<Object> args = consumeArgs(context, argCount);
                        String rawName = instr.getOp();
                        Object resolved = resolveValue(rawName, context);
                        String name = (resolved instanceof String) ? (String) resolved : rawName;
                        Object receiver = resolveValue(instr.getArg1(), context);
                        Object returnValue = invokeCall(instructions, name, instr.getDescriptor(), true,
                                receiver, args);
                        if (instr.getResult() != null) {
                            context.temps.put(instr.getResult(), returnValue);
                        }
                        break;
                    }

                    case PRINT: {
                        int argCount = parseArgCount(instr.getArg1());
                        List<Object> args = consumeArgs(context, argCount);
                        for (Object arg : args) {
                            System.out.print(arg);
                        }
                        break;
                    }

                    case PRINTLN: {
                        int argCount = parseArgCount(instr.getArg1());
                        List<Object> args = consumeArgs(context, argCount);
                        for (Object arg : args) {
                            System.out.print(arg);
                        }
                        System.out.println();
                        break;
                    }

                    case RETURN:
                        return;

                    case RETURN_VALUE:
                        context.returnValue = resolveValue(instr.getArg1(), context);
                        context.returned = true;
                        return;

                    case THROW: {
                        Object exObj = resolveValue(instr.getArg1(), context);
                        throw new VmException(exObj);
                    }

                    case CONVERT: {
                        Object srcVal = resolveValue(instr.getArg1(), context);
                        Number n = toNumber(srcVal);
                        String mnemonic = instr.getOp();
                        Object converted = n;
                        if (mnemonic != null) {
                            if (mnemonic.endsWith("2I"))
                                converted = n.intValue();
                            else if (mnemonic.endsWith("2D"))
                                converted = n.doubleValue();
                            else if (mnemonic.endsWith("2F"))
                                converted = n.floatValue();
                            else if (mnemonic.endsWith("2J"))
                                converted = n.longValue();
                        }
                        context.temps.put(instr.getResult(), converted);
                        break;
                    }
                    case FOLDED_CONST: {
                        context.temps.put(instr.getResult(), parseLiteral(instr.getArg1()));
                        break;
                    }
                    case LAMBDA_REF: {
                        context.temps.put(instr.getResult(), instr.getArg1());
                        break;
                    }
                    case DUP:
                    case DEFAULT_CONSTRUCTOR:
                    case STATIC_INIT:
                    case FIELD_DEFAULT:
                        break;

                    default:
                        break;
                }
            } catch (VmException ex) {
                Integer handlerIndex = findExceptionHandler(pc);
                if (handlerIndex != null) {
                    pc = handlerIndex;
                    context.temps.put("exception", ex.exceptionObject);
                    continue;
                }
                throw new RuntimeException("Unhandled Exception: " + ex.exceptionObject);
            } catch (RuntimeException ex) {
                Integer handlerIndex = findExceptionHandler(pc);
                if (handlerIndex != null) {
                    pc = handlerIndex;
                    RuntimeObject errObj = new RuntimeObject("java/lang/Exception");
                    context.temps.put("exception", errObj);
                    continue;
                }
                throw ex;
            }

            pc++;
        }
    }

    private Object invokeCall(List<Instruction> instructions, String name, String descriptor,
            boolean isVirtual, Object receiver, List<Object> args) {
        if ("print".equals(name)) {
            for (Object arg : args) {
                System.out.print(arg);
            }
            return null;
        }

        if ("println".equals(name)) {
            for (Object arg : args) {
                System.out.print(arg);
            }
            System.out.println();
            return null;
        }

        if ("pow".equals(name)) {
            if (args.size() < 2) {
                throw new RuntimeException("pow() requires 2 arguments");
            }
            Number base = toNumber(args.get(0));
            Number exponent = toNumber(args.get(1));
            return (int) Math.pow(base.doubleValue(), exponent.doubleValue());
        }

        MethodInfo method = lookupMethod(name, descriptor);
        if (method == null) {
            if (isVirtual && receiver != null && "toString".equals(name)) {
                return receiver.toString();
            }
            return null;
        }

        ExecutionContext nested = new ExecutionContext(method);
        if (isVirtual) {
            nested.variables.put("slot:0", receiver);
            seedArguments(nested, args, 1);
        } else {
            seedArguments(nested, args, 0);
        }

        executeRange(instructions, method.bodyStart, method.bodyEnd, nested);
        return nested.returned ? nested.returnValue : null;
    }

    private Object invokeMethod(List<Instruction> instructions, MethodInfo method,
            boolean isVirtual, Object receiver, List<Object> args) {
        if (method == null)
            return null;
        ExecutionContext nested = new ExecutionContext(method);
        if (isVirtual) {
            nested.variables.put(slotKey("0"), receiver);
            seedArguments(nested, args, 1);
        } else {
            seedArguments(nested, args, 0);
        }
        executeRange(instructions, method.bodyStart, method.bodyEnd, nested);
        return nested.returned ? nested.returnValue : null;
    }

    private void seedArguments(ExecutionContext context, List<Object> args, int baseSlot) {
        for (int i = 0; i < args.size(); i++) {
            context.variables.put(slotKey(String.valueOf(baseSlot + i)), args.get(i));
        }
    }

    private List<Object> consumeArgs(ExecutionContext context, int count) {
        int actualCount = Math.max(0, Math.min(count, context.pendingArgs.size()));
        int start = context.pendingArgs.size() - actualCount;
        List<Object> args = new ArrayList<>(context.pendingArgs.subList(start, context.pendingArgs.size()));
        context.pendingArgs.subList(start, context.pendingArgs.size()).clear();
        return args;
    }

    private MethodInfo lookupMethod(String name, String descriptor) {
        MethodInfo exact = methodTable.get(methodKey(name, descriptor));
        if (exact != null)
            return exact;
        MethodInfo fallback = methodTable.get(methodKey(name, null));
        if (fallback != null)
            return fallback;

        for (MethodInfo method : methodTable.values()) {
            if (name.equals(method.name)) {
                return method;
            }
        }
        return null;
    }

    private MethodInfo findEntrypoint(String name) {
        for (MethodInfo method : methodTable.values()) {
            if (name.equals(method.name)) {
                return method;
            }
        }
        return null;
    }

    private void buildMetadata(List<Instruction> instructions) {
        labelIndices.clear();
        methodTable.clear();
        methodsByStartIndex.clear();
        exceptionTable.clear();

        for (int i = 0; i < instructions.size(); i++) {
            Instruction instr = instructions.get(i);
            if (instr.getOpcode() == Opcode.LABEL) {
                String name = labelName(instr);
                if (name != null)
                    labelIndices.put(name, i);
            }
        }

        for (int i = 0; i < instructions.size(); i++) {
            Instruction instr = instructions.get(i);
            if (instr.getOpcode() == Opcode.CLASS) {
                // record class name so we can create a field container at runtime
                classStatics.put(instr.getArg1(), new HashMap<>());
            }
            if (instr.getOpcode() == Opcode.METHOD_START) {
                String name = instr.getArg1();
                String returnType = instr.getOp();
                String descriptor = null;
                int descriptorIndex = -1;
                int bodyStart = -1;
                int bodyEnd = -1;

                for (int j = i + 1; j < instructions.size(); j++) {
                    Instruction candidate = instructions.get(j);
                    if (candidate.getOpcode() == Opcode.METHOD_DESCRIPTOR && name.equals(candidate.getArg1())) {
                        descriptor = candidate.getOp();
                        descriptorIndex = j;
                        break;
                    }
                }

                for (int j = descriptorIndex + 1; j < instructions.size(); j++) {
                    Instruction candidate = instructions.get(j);
                    if (candidate.getOpcode() == Opcode.METHOD_END && name.equals(candidate.getArg1())) {
                        bodyStart = descriptorIndex >= 0 ? descriptorIndex + 1 : i + 1;
                        bodyEnd = j;
                        break;
                    }
                }

                MethodInfo methodInfo = new MethodInfo(name, descriptor, returnType, bodyStart, bodyEnd);
                methodTable.put(methodKey(name, descriptor), methodInfo);
                methodsByStartIndex.put(i, methodInfo);
            } else if (instr.getOpcode() == Opcode.EXCEPTION_TABLE_ENTRY) {
                String startLabel = instr.getArg1();
                String endLabel = instr.getOp();
                String handlerAndType = instr.getArg2();
                String[] parts = handlerAndType != null ? handlerAndType.split(":", 2) : new String[] { null, null };
                Integer startIndex = labelIndices.get(startLabel);
                Integer endIndex = labelIndices.get(endLabel);
                Integer handlerIndex = labelIndices.get(parts[0]);
                if (startIndex != null && endIndex != null && handlerIndex != null) {
                    exceptionTable.add(new ExceptionEntry(startIndex, endIndex, handlerIndex, parts[1]));
                }
            }
        }
    }

    private Integer findExceptionHandler(int pc) {
        for (ExceptionEntry entry : exceptionTable) {
            if (pc >= entry.start && pc < entry.end)
                return entry.handler;
        }
        return null;
    }

    private int findLabel(List<Instruction> instructions, String labelName) {
        Integer index = labelIndices.get(labelName);
        if (index != null)
            return index;
        for (int i = 0; i < instructions.size(); i++) {
            Instruction instr = instructions.get(i);
            if (instr.getOpcode() == Opcode.LABEL && labelName.equals(labelName(instr))) {
                return i;
            }
        }
        throw new RuntimeException("Target label not found: " + labelName);
    }

    private Object resolveValue(String name, ExecutionContext context) {
        if (name == null)
            return null;
        if (context.temps.containsKey(name))
            return context.temps.get(name);
        if ("this".equals(name))
            return context.variables.get(slotKey("0"));
        if (context.variables.containsKey(slotKey(name)))
            return context.variables.get(slotKey(name));
        if (context.variables.containsKey(name))
            return context.variables.get(name);
        if (classStatics.containsKey(name))
            return classStatics.get(name);
        if ("true".equals(name))
            return true;
        if ("false".equals(name))
            return false;
        if (name.startsWith("\"") && name.endsWith("\""))
            return unescape(name.substring(1, name.length() - 1));
        try {
            if (name.contains("."))
                return Double.parseDouble(name);
            return Integer.parseInt(name);
        } catch (NumberFormatException ex) {
            return name;
        }
    }

    private boolean isTruthy(Object value) {
        if (value == null)
            return false;
        if (value instanceof Boolean)
            return (Boolean) value;
        if (value instanceof Number)
            return ((Number) value).longValue() != 0;
        if (value instanceof String)
            return !((String) value).isEmpty();
        return true;
    }

    private Number toNumber(Object value) {
        if (value instanceof Number)
            return (Number) value;
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (Exception ignored) {
            }
            try {
                return Double.parseDouble((String) value);
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private Object parseLiteral(String value) {
        if (value == null)
            return null;
        if ("true".equals(value))
            return true;
        if ("false".equals(value))
            return false;
        if (value.startsWith("\"") && value.endsWith("\""))
            return unescape(value.substring(1, value.length() - 1));
        try {
            if (value.contains("."))
                return Double.parseDouble(value);
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return value;
        }
    }

    private String unescape(String s) {
        if (s == null)
            return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case 'n':
                        sb.append('\n');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case '"':
                        sb.append('\"');
                        break;
                    case '\'':
                        sb.append('\'');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    default:
                        sb.append('\\').append(next);
                        break;
                }
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private int parseArgCount(String raw) {
        if (raw == null || raw.isEmpty())
            return 0;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String slotKey(String slot) {
        return "slot:" + slot;
    }

    private String methodKey(String name, String descriptor) {
        return name + "#" + (descriptor != null ? descriptor : "");
    }

    private String labelName(Instruction instr) {
        return instr.getResult() != null ? instr.getResult() : instr.getArg1();
    }

    private static final class ExecutionContext {
        final MethodInfo methodInfo;
        final Map<String, Object> temps = new HashMap<>();
        final Map<String, Object> variables = new HashMap<>();
        final List<Object> pendingArgs = new ArrayList<>();
        boolean returned;
        Object returnValue;

        ExecutionContext(MethodInfo methodInfo) {
            this.methodInfo = methodInfo;
        }
    }

    private static final class MethodInfo {
        final String name;
        final String descriptor;
        final String returnType;
        final int bodyStart;
        final int bodyEnd;

        MethodInfo(String name, String descriptor, String returnType, int bodyStart, int bodyEnd) {
            this.name = name;
            this.descriptor = descriptor;
            this.returnType = returnType;
            this.bodyStart = bodyStart;
            this.bodyEnd = bodyEnd;
        }
    }

    private static final class ExceptionEntry {
        final int start;
        final int end;
        final int handler;
        final String catchType;

        ExceptionEntry(int start, int end, int handler, String catchType) {
            this.start = start;
            this.end = end;
            this.handler = handler;
            this.catchType = catchType;
        }
    }

    private static final class VmException extends RuntimeException {
        final Object exceptionObject;

        VmException(Object exceptionObject) {
            super(exceptionObject != null ? exceptionObject.toString() : "null");
            this.exceptionObject = exceptionObject;
        }
    }

    private static final class RuntimeObject {
        final String className;
        final Map<String, Object> fields = new HashMap<>();

        RuntimeObject(String className) {
            this.className = className;
        }

        @Override
        public String toString() {
            return className + fields.toString();
        }
    }
}