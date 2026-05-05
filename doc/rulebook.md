# Compiler Engineering: Unified Rulebook & Specification

**Version:** 1.0.0
**Target Language:** Java

---

## Part I: Architectural Principles

### 1. Architectural "Must-Haves" (The Golden Rules)

- **1.1 Strict Phase Isolation:** Maintain strict boundaries between compilation phases. The Lexer should only know about characters, the Parser only about tokens, and the Semantic Analyzer only about the AST. Decoupling allows you to swap out or upgrade one phase without breaking the entire pipeline.
- **1.2 Graceful Error Recovery:** Implement synchronization-token recovery. When the parser encounters a syntax error, it should log it, skip forward to the next synchronization point (like a semicolon `;` or closing brace `}`), and continue parsing to report _all_ errors in a single compilation pass.
- **1.3 Centralized Diagnostics:** Use a single, unified diagnostic engine (like `ErrorHandler`) that captures the exact line, column, and context of an anomaly.
- **1.4 A Clean, Meaning-Driven AST:** Design the Abstract Syntax Tree to represent the _meaning_ of the code, not the exact formatting. Discard superficial tokens (parentheses, commas, semicolons) once their structural purpose is fulfilled by the Parser.

### 2. Architectural "Must-Nots" (The Anti-Patterns)

- **2.1 DO NOT Leak Context to the Lexer:** Don't make the Lexer try to figure out if `x` is an integer, a string, or a class name. Giving the Lexer semantic responsibilities creates circular dependencies.
- **2.2 DO NOT Fail Silently:** Don't ignore unexpected tokens or undefined variables just to keep the compiler running. Silent failures lead to compilers that successfully generate broken machine code.
- **2.3 DO NOT Mutate the AST During Traversal:** Don't change the structure of the AST while the Semantic Analyzer is actively walking through it to avoid infinite loops and dropped nodes.
- **2.4 DO NOT Combine Different Symbol Tables:** Don't use a lexical frequency counter as your semantic scoping environment. Lexical analysis needs a flat, global list; semantic analysis requires a dynamic stack of HashMaps.

---

## Part II: Core Language Specifications

### 3. Lexical Specifications (Phase 1: Scanner)

- **Token Categories:** Keywords, Identifiers, Constants, Literals, Operators, Punctuation, and Special Characters.
- **Ignored Elements:** Whitespace (spaces, tabs, carriage returns) and Comments (single-line `//` and multi-line `/* ... */`) are stripped before tokenization. Newlines (`\n`) increment the line tracker.

### 4. Syntactic Specifications (Phase 2: Parser)

- **Statement Grammar:** Enforces structural rules (e.g., `ifStmt → if ( expr ) block [ else block ]`).
- **Operator Precedence (Lowest to Highest):** Assignment, Logical OR, Logical AND, Equality, Relational, Additive, Multiplicative, Unary.
- **Lambda Expressions:** The parser recognizes anonymous function syntax such as `(a, b) -> a + b` and single-parameter lambdas like `x -> x + 1`. Lambda bodies may be either a block or a single expression.

### 5. Semantic Specifications (Phase 3: Logic & Types)

- **Scope Rules:** Every `{ ... }` block introduces a new localized environment stack. Inner variables may shadow outer variables. Lookups search from the innermost block outward.
- **Error Handling Protocol:** All errors are routed to the centralized `ErrorHandler` and strictly require line and column coordinates.

---

## Part III: Strict Constraints & Edge Cases

### 6. Lexical Constraints

- **Identifier Initiation:** An `IDENTIFIER` must strictly begin with an alphabetical character (`a-z`, `A-Z`) or an underscore (`_`). Identifiers **cannot** begin with a number.
  - _Compiler Behavior:_ A lexeme starting with a digit (e.g., `1variable`) will be scanned as a `CONSTANT` (`1`), followed immediately by an `IDENTIFIER` (`variable`). This triggers a syntax error in the Parser.
- **Decimal Limitations:** A `CONSTANT` may only contain a single decimal point `.`, and it must be immediately followed by a digit.
  - _Compiler Behavior:_ If a second decimal point is encountered, the scanner terminates the constant collection and treats the subsequent decimal as `PUNCTUATION`.
- **Unterminated Literals:** A `LITERAL` string or character must be closed with a matching quote before the end of the line (`\n`). Failure to close throws a fatal Lexer error.
- **Arrow Tokenization:** The lexer must preserve the lambda arrow token (`->`) so the parser can distinguish lambdas from subtraction/greater-than sequences.

### 7. Syntactic Constraints

- **Variable Declaration Grammar:** The grammar for a declaration is strictly `TYPE IDENTIFIER [= EXPR] ;`.
  - _Compiler Behavior:_ Statements like `int 5 = 8;` are invalid. Encountering a `CONSTANT` (`5`) after a `KEYWORD` halts the declaration logic and throws a syntax expectation error.
- **Statement Termination:** Every execution statement must be explicitly terminated with a semicolon `;`.
  - _Exception:_ Control flow block structures (`if`, `while`, `for`, and `{...}`) handle their own bounding and do not require trailing semicolons.
- **Condition Enclosure:** The conditionals within `if`, `while`, and `for` statements must be strictly enclosed in special character parentheses `( ... )`.

### 8. Semantic Constraints

- **Valid Assignment Targets (L-Values):** Only user-defined `IDENTIFIER` nodes are valid L-values (Left-Hand Side targets) for assignment operations. The parser might build an `ASSIGN` tree for `5 = x`, but the semantic analyzer must reject it.
- **Reserved Keyword Collisions:** Attempting to declare `int public = 5;` is strictly prohibited. The Parser expects an `IDENTIFIER`; receiving a `KEYWORD` throws a syntax error.
- **Lambda Typing:** Lambda expressions are treated as callable values. The analyzer permits `var` declarations initialized with lambdas and infers the lambda type from the initializer.

### 9. Type Coercion & Promotion

- **Implicit Promotion (Widening):** In an operation involving an `int` and a `double` (e.g., `5 + 3.14`), the `int` is implicitly promoted to a `double`.
- **Explicit Casting Requirement (Narrowing):** Assigning a floating-point value to an integer variable is invalid unless an explicit cast is provided.
- **String Concatenation:** If the additive operator `+` is used where at least one operand is a `String`, the other operand is implicitly coerced into a `String`.

### 10. Function/Method Resolution

- **Definition Check:** The method identifier must exist prior to invocation.
- **Arity Matching:** The number of arguments provided must exactly match the number of parameters defined in the signature.
- **Parameter Type Matching:** Each argument provided must be type-compatible with the corresponding parameter.
- **Return Path Validation:** If a method has a non-void return type, all logical execution paths must conclude with a `return` statement outputting a compatible type.
- **Lambda Invocation:** Stored lambda values are resolved through method-call lowering so a variable bound to a lambda may be invoked with call syntax after generation.

### 11. Control Flow Constraints (Semantic)

Java enforces strict rules on how execution flows through a program. The Semantic Analyzer must catch illogical pathways.

- **Boolean Condition Enclosure:** The evaluated expression inside `if ( expr )`, `while ( expr )`, and `for ( ; expr ; )` statements must strictly resolve to a `boolean` data type.
  - _Compiler Behavior:_ Unlike C or Python, Java does not treat integers (like `1` or `0`) or strings as "truthy" or "falsy." Passing an `int` to a conditional (e.g., `if (5) { ... }`) triggers a fatal semantic type-mismatch error.
- **Unreachable Code Detection:** Any statement immediately following a `return` statement within the exact same block scope is logically unreachable.
  - _Compiler Behavior:_ The Semantic Analyzer flags statements placed after a `return` as dead code and throws an "Unreachable Statement" error to prevent logical dead ends.

### 12. Constant Boundaries (Lexical/Semantic)

Numeric literals have hardware-defined limits. The compiler must validate that hardcoded values fit into memory.

- **Integer Overflow Prevention:** The standard `int` type in Java is a 32-bit signed integer.
  - _Compiler Behavior:_ If the Lexer or Semantic Analyzer encounters a numeric `CONSTANT` that exceeds the maximum positive value (`2147483647`) or minimum negative value (`-2147483648`), it throws an "Integer Number Too Large" error before attempting to store it in memory.

### 13. Operator Edge Cases (Semantic)

_Implementation Note: The current semantic analyzer does not perform a range check on numeric literals; this validation is deferred to the optimization phase._
Certain mathematical operations are fundamentally illegal and must be caught during compilation if explicitly hardcoded.

- **Constant Division by Zero:** Division (`/`) or Modulo (`%`) where the right-hand operand is explicitly the `CONSTANT` `0`.
  - _Compiler Behavior:_ If the AST contains a `BINARY_OP` where the operator is `/` or `%` and the right child is evaluated as `0`, the Semantic Analyzer throws an "Arithmetic Exception: / by zero" error rather than allowing the program to crash at runtime.

---

## Part IV: Code Generation Specifications

The compiler translates the AST into a high-level, Three-Address Code (TAC) representation, which is then realized as a list of professional-grade, JVM-like `Instruction` objects.

### 14. Three-Address Code (TAC) Principles

- **14.1 TAC Model:** All expressions are decomposed into a sequence of fundamental operations, each with at most one operator. The canonical form is `result = operand1 op operand2`.
- **14.2 Temporary Variables:** The generator creates fresh temporary variables (e.g., `t0`, `t1`, `t2`) to hold the result of every intermediate sub-expression. This enforces the single-operator rule and simplifies code generation.
- **14.3 Linear Instruction List:** The generator performs a depth-first traversal of the AST to produce a flat, linear list of `Instruction` objects.
- **14.4 Register-Style Access:** Operands are treated as named addresses (variables or temporaries). Variable reads simply use the name as a source address, and writes are explicit `COPY` or `STORE` instructions. There is no reliance on an implicit operand stack for arithmetic.
- **14.5 Control Flow:** All control flow is managed with explicit `LABEL` instructions and conditional or unconditional jumps (e.g., `JUMP_IF_FALSE`, `JUMP`).
- **14.6 Method Calls:** Method arguments are specified via `ARG` instructions, followed by a `CALL` (for static) or `CALL_VIRTUAL` (for instance) instruction that may store its result in a temporary.
- **14.7 Short-Circuiting:** Logical `&&` and `||` operators are implemented using conditional jumps to skip evaluation of the right-hand side.
- **14.8 Bridge to Professional Bytecode:** This high-level TAC representation is the input for generating professional-grade bytecode. Each TAC instruction is mapped to one or more type-specific opcodes and metadata structures as detailed in **Part V**.

---

## Part V: Professional-Grade Code Generation Specifications

### 15. Resource & Boundary Analysis

- **15.1 Maximum Stack Depth Calculation:** For every method, the Code Generator MUST calculate the peak operand stack height. This is determined by simulating the stack effect of each instruction: $h_{max} = \max(h_{current} + \text{stack\_delta})$.
- **15.2 Local Variable Slot Allocation:** Variable names MUST be mapped to integer indices (slots). The generator MUST calculate `MaxLocals`, accounting for the fact that `long` and `double` types occupy two slots, while other types occupy one.
- **15.3 Implicit 'this' Reference:** In instance methods, slot 0 MUST be reserved for the `this` reference, shifting all user-defined parameters and local variables to higher indices.

### 16. Constant Pool & Memory Optimization

- **16.1 Literal Deduplication:** The Code Generator MUST NOT embed large literals (Strings, Classes, long constants) directly in the instruction stream. These MUST be moved to a central Constant Pool.
- **16.2 LDC Instruction Usage:** Access to the Constant Pool MUST use the `LDC` (Load Constant) instruction family, referencing entries by their pool index rather than their raw value.
- **16.3 String Interning:** All unique string literals encountered during generation MUST be interned within the Constant Pool to ensure that identical strings share the same memory address.

### 17. Type-Specific Opcode Selection

- **17.1 Type-Prefixing Mandatory:** The generic opcodes defined in Phase 4 (ADD, LOAD, STORE) MUST be replaced with type-prefixed versions. The generator MUST select the prefix based on the type resolved during Semantic Analysis:
  - `i` for `int`, `boolean`, `byte`, `short`, `char`
  - `l` for `long`
  - `f` for `float`
  - `d` for `double`
  - `a` for references (Objects, Arrays)
- **17.2 Specialized Zero-Operand Instructions:** For common constants (0, 1, 2, null), the generator SHOULD use specialized, operand-less instructions (e.g., `ICONST_0`, `ACONST_NULL`) to reduce the size of the bytecode.

### 18. Verification & Control Flow Metadata

- **18.1 Stack Map Frame Generation:** For every jump target (LABEL), the Code Generator MUST emit a Stack Map Frame. This metadata MUST describe the type-state of the local variables and the operand stack at that specific point to allow for linear-time bytecode verification.
- **18.2 Exception Table Mapping:** Rather than using jumps for error handling, `try-catch` blocks MUST be recorded in an Exception Table. Each entry MUST define the range $[start\_pc, end\_pc)$, the `handler_pc`, and the `catch_type`.
- **18.3 Dead Code Elimination:** The generator SHOULD identify and omit instructions that are logically unreachable (e.g., code following an unconditional `return`), as these will fail verification in a strict JVM environment.

### 19. Advanced Arithmetic & Conversion

- **19.1 Explicit Conversion Instructions:** When the Semantic Analyzer identifies an implicit widening (e.g., `int` to `double`), the Code Generator MUST emit an explicit conversion instruction (e.g., `I2D`) to maintain stack type-safety.
- **19.2 Precision Management:** Operations involving floating-point numbers MUST follow strict IEEE 754 rules, ensuring that `fadd` and `dadd` are used correctly based on the required precision.

---

## Part VI: Advanced Optimization & Runtime Integration

### 20. Compile-Time Optimization (The "Smarter" Generator)

- **20.1 Constant Folding:** The Code Generator SHOULD identify expressions consisting entirely of literals (e.g., `2 + 3`) and emit the final result (`PUSH 5`) instead of the arithmetic instructions. This reduces runtime CPU cycles.
- **20.2 Peephole Optimization:** The generator SHOULD scan the instruction stream for inefficient sequences. For example, a `STORE x` followed immediately by a `LOAD x` can often be optimized into a `DUP` followed by a `STORE x`.
- **20.3 Strength Reduction:** Expensive operations SHOULD be replaced with cheaper equivalents. For example, multiplying an integer by 2 SHOULD be translated as a bitwise left shift (`ISHL 1`) rather than a full multiplication (`IMUL`).
- **20.4 Lambda Method Emission:** Pending lambda bodies MUST be emitted as generated methods even in top-level scripts, not only inside class declarations.

### 21. Debugging & Traceability Metadata

- **21.1 LineNumberTable Generation:** The generator MUST maintain a mapping between bytecode offsets and source code line numbers. This is critical for generating meaningful stack traces when a program crashes at runtime.
- **21.2 LocalVariableTable:** To support debuggers, the generator SHOULD emit a table mapping slot indices back to their original names (e.g., "Slot 1 is 'counter'"). Without this, debuggers can only show raw slot numbers.
- **21.3 SourceFile Attribute:** Every compiled class MUST include an attribute identifying the original source file name (e.g., `App.java`), ensuring the JVM can locate the code for debugging.[cite: 1]
- **21.4 Editor-Aware Indentation:** The UI should preserve indentation state consistently across auto-indent, folding, and settings changes so the editor remains predictable when code is reformatted.

### 22. Implicit Lifecycle & Initialization

- **22.1 Default Constructor Generation (`<init>`):** If a class definition in the AST does not explicitly define a constructor, the Code Generator MUST synthesize a default, no-argument constructor that invokes the superclass constructor.
- **22.2 Static Initializer (`<clinit>`):** Any static variable initializations (e.g., `static int x = 5;`) MUST be moved into a special `<clinit>` method that is executed exactly once when the class is loaded by the JVM.
- **22.3 Field Defaulting:** The generator MUST ensure that all fields are initialized to their default values (e.g., `0` for numeric, `null` for references) before any method execution begins.

### 23. Standard Naming & Descriptors

- **23.1 Method Descriptor Encoding:** Professional bytecode does not store names like `int`. It uses JVM type descriptors. The generator MUST translate types into the following format:
  - `int` → `I`
  - `double` → `D`
  - `String` → `Ljava/lang/String;`
  - `void` → `V`
- **23.2 Signature Mangling:** For overloaded methods, the generator MUST generate a unique internal signature (e.g., `foo(I)V` vs `foo(D)V`) so the runtime can differentiate between them during invocation.

---

## Part VII: Bytecode Optimization

### 24. Static Optimization Rules

- **24.1 Constant Folding:** If an expression consists entirely of constants (e.g., `5 + 10 * 2`), the compiler MUST evaluate it during the compilation phase and emit a single `PUSH 25` instruction instead of multiple arithmetic operations.
- **24.2 Constant Propagation:** If a variable is assigned a constant value and never modified before its next use, the compiler SHOULD replace the `LOAD <name>` instruction with a direct `PUSH` of that constant.
- **24.3 Dead Code Elimination:** The compiler MUST identify and remove code that cannot be reached (e.g., code after a `return` or inside an `if(false)` block) to reduce the final binary size.

### 25. Peephole Optimization

- **25.1 Redundant Instruction Removal:** The optimizer MUST look for and remove "neutral" instruction sequences, such as:
  - `STORE x` immediately followed by `LOAD x` (replace with `DUP, STORE x`).
  - `ADD` where one operand is `0`.
  - `MUL` where one operand is `1`.
- **25.2 Jump-to-Jump Optimization:** If a `JUMP` instruction targets a label that contains only another `JUMP`, the first instruction SHOULD be updated to target the final destination directly.

---

## Part VIII: Runtime & Virtual Machine (VM) Specifications

The generated `Instruction` list is executed by a register-based interpreter.

### 26. The Execution Engine

- **26.1 Execution Model:** The VM executes the `Instruction` list sequentially, advancing a Program Counter (PC).
- **26.2 Data Storage:** The VM maintains a `Map` to store the current values of all named variables and temporary registers (e.g., `x`, `t0`, `t1`). Operands are read from this map, and results are written back into it.
- **26.3 Program Counter (PC):** The VM MUST maintain a `PC` that points to the index of the current instruction in the list. Jump instructions work by directly modifying the PC.
- **26.4 Call Stack:** To support method calls, the VM maintains a call stack. Each stack frame contains its own data storage map for local variables and a return address (the PC of the caller).

### 27. Standard Library (Built-in Functions)

- **27.1 System Interop:** The compiler MUST provide a set of pre-defined method signatures for basic I/O (e.g., `print`, `println`, `readInt`).
- **27.2 Native Method Mapping:** Instructions like `PRINT` MUST map directly to the host language's standard output (e.g., `System.out.println` in Java).
- **27.3 Lambda-Friendly Runtime:** Callable values backed by generated lambda methods must be invokable through the same method-call path used by normal methods.

---

## Part IX: Debugging & Metadata

### 28. Line Number Mapping

- **28.1 Source Mapping:** The compiler SHOULD generate a `LineNumberTable` that maps bytecode offsets to original source code line numbers.
- **28.2 Error Reporting (Runtime):** If a runtime error occurs (like division by zero), the VM MUST use the `LineNumberTable` to report the error's location in the user's original `.java` or `.txt` file rather than just the bytecode index.

### 29. Symbol Export

- **29.1 AST Visualization:** For grading and debugging, the compiler MUST support exporting the AST into a structured format (like JSON or a `.dot` file for Graphviz).
- **29.2 Bytecode Disassembler:** The compiler SHOULD include a utility to print the generated `List<Instruction>` in a human-readable "assembly" format, similar to the `javap` tool.
