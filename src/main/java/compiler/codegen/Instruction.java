package compiler.codegen;

/**
 * Represents a single bytecode instruction.
 *
 * Each instruction has an {@link Opcode} and an optional operand.
 * Operand-less instructions (e.g. ADD, POP) leave the operand null.
 *
 * Usage examples:
 *   Instruction.of(Opcode.ADD)
 *   Instruction.of(Opcode.PUSH, 42)
 *   Instruction.of(Opcode.STORE, "x")
 *   Instruction.of(Opcode.LABEL, "WHILE_START_0")
 */
public final class Instruction {

    public enum Opcode {

        // --- Stack manipulation ---
        PUSH,           // PUSH <number>        — push a numeric literal
        PUSH_CONST,     // PUSH_CONST <value>   — push a string/bool/null literal
        POP,            // POP                  — discard top of stack
        DUP,            // DUP                  — duplicate top of stack

        // --- Variables ---
        LOAD,           // LOAD <name>          — push value of local/field variable
        STORE,          // STORE <name>         — pop and store into variable
        STORE_PARAM,    // STORE_PARAM <name>   — store incoming method parameter

        // --- Arithmetic ---
        ADD,            // ADD
        SUB,            // SUB
        MUL,            // MUL
        DIV,            // DIV
        MOD,            // MOD
        NEG,            // NEG                  — negate top of stack (unary -)

        // --- Bitwise ---
        BITWISE_NOT,    // BITWISE_NOT          — bitwise complement (~)

        // --- Comparison ---
        EQUAL,          // EQUAL
        NOT_EQUAL,      // NOT_EQUAL
        LESS_THAN,      // LESS_THAN
        LESS_EQUAL,     // LESS_EQUAL
        GREATER_THAN,   // GREATER_THAN
        GREATER_EQUAL,  // GREATER_EQUAL

        // --- Logic ---
        NOT,            // NOT                  — boolean not (!)

        // --- Jumps & labels ---
        LABEL,          // LABEL <name>         — branch target (no-op at runtime)
        JUMP,           // JUMP <label>         — unconditional jump
        JUMP_IF_TRUE,   // JUMP_IF_TRUE <label> — jump if top of stack is truthy
        JUMP_IF_FALSE,  // JUMP_IF_FALSE <label>— jump if top of stack is falsy

        // --- Methods ---
        METHOD_START,   // METHOD_START <name>:<returnType>
        METHOD_END,     // METHOD_END <name>
        INVOKE_VIRTUAL, // INVOKE_VIRTUAL <name> <argCount>  — instance method call
        INVOKE_STATIC,  // INVOKE_STATIC <name> <argCount>   — static method call
        RETURN,         // RETURN               — void return
        RETURN_VALUE,   // RETURN_VALUE         — return top of stack

        // --- Objects & arrays ---
        NEW,            // NEW <type> <argCount>— allocate new object
        FIELD_LOAD,     // FIELD_LOAD <name>    — load a field from object on stack
        FIELD_STORE,    // FIELD_STORE <name>   — store into a field of object on stack
        ARRAY_LOAD,     // ARRAY_LOAD           — load array[index]; pops index then ref
        ARRAY_STORE,    // ARRAY_STORE          — store into array[index]

        // --- Type ---
        CAST,           // CAST <type>          — cast top of stack to target type

        // --- I/O ---
        PRINT,          // PRINT <argCount>     — print top <argCount> values

        // --- Classes ---
        CLASS,          // CLASS <name>         — marks the start of a class definition
    }

    private final Opcode opcode;
    private final String operand; // null for operand-less instructions

    private Instruction(Opcode opcode, String operand) {
        this.opcode  = opcode;
        this.operand = operand;
    }

    /** Create an instruction with no operand (e.g. ADD, POP, RETURN). */
    public static Instruction of(Opcode opcode) {
        return new Instruction(opcode, null);
    }

    /** Create an instruction with a String operand (e.g. STORE "x", LABEL "L0"). */
    public static Instruction of(Opcode opcode, String operand) {
        return new Instruction(opcode, operand);
    }

    /** Create an instruction with a numeric operand (e.g. PUSH 42). */
    public static Instruction of(Opcode opcode, int operand) {
        return new Instruction(opcode, Integer.toString(operand));
    }

    /** Create an instruction with a double operand (e.g. PUSH 3.14). */
    public static Instruction of(Opcode opcode, double operand) {
        return new Instruction(opcode, Double.toString(operand));
    }

    public Opcode getOpcode() {
        return opcode;
    }

    /** Returns the operand string, or null if this instruction has none. */
    public String getOperand() {
        return operand;
    }

    /** True if this instruction carries an operand. */
    public boolean hasOperand() {
        return operand != null;
    }

    @Override
    public String toString() {
        return operand == null
                ? opcode.name()
                : opcode.name() + " " + operand;
    }
}