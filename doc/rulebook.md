# Compiler Engineering: Unified Rulebook & Specification
**Version:** 1.0.0
**Target Language:** Java

---

## Part I: Architectural Principles

### 1. Architectural "Must-Haves" (The Golden Rules)
* **1.1 Strict Phase Isolation:** Maintain strict boundaries between compilation phases. The Lexer should only know about characters, the Parser only about tokens, and the Semantic Analyzer only about the AST. Decoupling allows you to swap out or upgrade one phase without breaking the entire pipeline.
* **1.2 Graceful Error Recovery:** Implement synchronization-token recovery. When the parser encounters a syntax error, it should log it, skip forward to the next synchronization point (like a semicolon `;` or closing brace `}`), and continue parsing to report *all* errors in a single compilation pass.
* **1.3 Centralized Diagnostics:** Use a single, unified diagnostic engine (like `ErrorHandler`) that captures the exact line, column, and context of an anomaly. 
* **1.4 A Clean, Meaning-Driven AST:** Design the Abstract Syntax Tree to represent the *meaning* of the code, not the exact formatting. Discard superficial tokens (parentheses, commas, semicolons) once their structural purpose is fulfilled by the Parser.

### 2. Architectural "Must-Nots" (The Anti-Patterns)
* **2.1 DO NOT Leak Context to the Lexer:** Don't make the Lexer try to figure out if `x` is an integer, a string, or a class name. Giving the Lexer semantic responsibilities creates circular dependencies.
* **2.2 DO NOT Fail Silently:** Don't ignore unexpected tokens or undefined variables just to keep the compiler running. Silent failures lead to compilers that successfully generate broken machine code.
* **2.3 DO NOT Mutate the AST During Traversal:** Don't change the structure of the AST while the Semantic Analyzer is actively walking through it to avoid infinite loops and dropped nodes.
* **2.4 DO NOT Combine Different Symbol Tables:** Don't use a lexical frequency counter as your semantic scoping environment. Lexical analysis needs a flat, global list; semantic analysis requires a dynamic stack of HashMaps.

---

## Part II: Core Language Specifications

### 3. Lexical Specifications (Phase 1: Scanner)
* **Token Categories:** Keywords, Identifiers, Constants, Literals, Operators, Punctuation, and Special Characters.
* **Ignored Elements:** Whitespace (spaces, tabs, carriage returns) and Comments (single-line `//` and multi-line `/* ... */`) are stripped before tokenization. Newlines (`\n`) increment the line tracker.

### 4. Syntactic Specifications (Phase 2: Parser)
* **Statement Grammar:** Enforces structural rules (e.g., `ifStmt → if ( expr ) block [ else block ]`).
* **Operator Precedence (Lowest to Highest):** Assignment, Logical OR, Logical AND, Equality, Relational, Additive, Multiplicative, Unary.

### 5. Semantic Specifications (Phase 3: Logic & Types)
* **Scope Rules:** Every `{ ... }` block introduces a new localized environment stack. Inner variables may shadow outer variables. Lookups search from the innermost block outward.
* **Error Handling Protocol:** All errors are routed to the centralized `ErrorHandler` and strictly require line and column coordinates.

---

## Part III: Strict Constraints & Edge Cases

### 6. Lexical Constraints
* **Identifier Initiation:** An `IDENTIFIER` must strictly begin with an alphabetical character (`a-z`, `A-Z`) or an underscore (`_`). Identifiers **cannot** begin with a number. 
    * *Compiler Behavior:* A lexeme starting with a digit (e.g., `1variable`) will be scanned as a `CONSTANT` (`1`), followed immediately by an `IDENTIFIER` (`variable`). This triggers a syntax error in the Parser.
* **Decimal Limitations:** A `CONSTANT` may only contain a single decimal point `.`, and it must be immediately followed by a digit. 
    * *Compiler Behavior:* If a second decimal point is encountered, the scanner terminates the constant collection and treats the subsequent decimal as `PUNCTUATION`.
* **Unterminated Literals:** A `LITERAL` string or character must be closed with a matching quote before the end of the line (`\n`). Failure to close throws a fatal Lexer error.

### 7. Syntactic Constraints
* **Variable Declaration Grammar:** The grammar for a declaration is strictly `TYPE IDENTIFIER [= EXPR] ;`.
    * *Compiler Behavior:* Statements like `int 5 = 8;` are invalid. Encountering a `CONSTANT` (`5`) after a `KEYWORD` halts the declaration logic and throws a syntax expectation error.
* **Statement Termination:** Every execution statement must be explicitly terminated with a semicolon `;`. 
    * *Exception:* Control flow block structures (`if`, `while`, `for`, and `{...}`) handle their own bounding and do not require trailing semicolons.
* **Condition Enclosure:** The conditionals within `if`, `while`, and `for` statements must be strictly enclosed in special character parentheses `( ... )`.

### 8. Semantic Constraints
* **Valid Assignment Targets (L-Values):** Only user-defined `IDENTIFIER` nodes are valid L-values (Left-Hand Side targets) for assignment operations. The parser might build an `ASSIGN` tree for `5 = x`, but the semantic analyzer must reject it.
* **Reserved Keyword Collisions:** Attempting to declare `int public = 5;` is strictly prohibited. The Parser expects an `IDENTIFIER`; receiving a `KEYWORD` throws a syntax error.

### 9. Type Coercion & Promotion
* **Implicit Promotion (Widening):** In an operation involving an `int` and a `double` (e.g., `5 + 3.14`), the `int` is implicitly promoted to a `double`.
* **Explicit Casting Requirement (Narrowing):** Assigning a floating-point value to an integer variable is invalid unless an explicit cast is provided. 
* **String Concatenation:** If the additive operator `+` is used where at least one operand is a `String`, the other operand is implicitly coerced into a `String`.

### 10. Function/Method Resolution
* **Definition Check:** The method identifier must exist prior to invocation.
* **Arity Matching:** The number of arguments provided must exactly match the number of parameters defined in the signature.
* **Parameter Type Matching:** Each argument provided must be type-compatible with the corresponding parameter.
* **Return Path Validation:** If a method has a non-void return type, all logical execution paths must conclude with a `return` statement outputting a compatible type.

### 11. Control Flow Constraints (Semantic)
Java enforces strict rules on how execution flows through a program. The Semantic Analyzer must catch illogical pathways.
* **Boolean Condition Enclosure:** The evaluated expression inside `if ( expr )`, `while ( expr )`, and `for ( ; expr ; )` statements must strictly resolve to a `boolean` data type. 
    * *Compiler Behavior:* Unlike C or Python, Java does not treat integers (like `1` or `0`) or strings as "truthy" or "falsy." Passing an `int` to a conditional (e.g., `if (5) { ... }`) triggers a fatal semantic type-mismatch error.
* **Unreachable Code Detection:** Any statement immediately following a `return` statement within the exact same block scope is logically unreachable.
    * *Compiler Behavior:* The Semantic Analyzer flags statements placed after a `return` as dead code and throws an "Unreachable Statement" error to prevent logical dead ends.

### 12. Constant Boundaries (Lexical/Semantic)
Numeric literals have hardware-defined limits. The compiler must validate that hardcoded values fit into memory.
* **Integer Overflow Prevention:** The standard `int` type in Java is a 32-bit signed integer. 
    * *Compiler Behavior:* If the Lexer or Semantic Analyzer encounters a numeric `CONSTANT` that exceeds the maximum positive value (`2147483647`) or minimum negative value (`-2147483648`), it throws an "Integer Number Too Large" error before attempting to store it in memory.

### 13. Operator Edge Cases (Semantic)
Certain mathematical operations are fundamentally illegal and must be caught during compilation if explicitly hardcoded.
* **Constant Division by Zero:** Division (`/`) or Modulo (`%`) where the right-hand operand is explicitly the `CONSTANT` `0`.
    * *Compiler Behavior:* If the AST contains a `BINARY_OP` where the operator is `/` or `%` and the right child is evaluated as `0`, the Semantic Analyzer throws an "Arithmetic Exception: / by zero" error rather than allowing the program to crash at runtime.

---

## Part IV: Code Generation Specifications

### 14. Bytecode Generation (Phase 4: Execution Mapping)
* **14.1 Stack-Based Execution Model:** The Code Generator MUST translate all expressions into a stack-based instruction sequence. Every evaluated value is pushed onto the stack before any operation is performed. Arithmetic, logical, and comparison operations MUST consume operands from the stack and push the resulting value back.
* **14.2 Instruction Emission Rule:** Each Abstract Syntax Tree (AST) node MUST map to one or more bytecode instructions through a depth-first recursive traversal. The generator MUST NOT skip nodes or reorder evaluation unless explicitly defined by operator precedence in the AST.
* **14.3 Evaluation Order Guarantee:** Expression evaluation MUST follow the order defined by the AST structure. Operator precedence and associativity are assumed to already be resolved during parsing. The Code Generator MUST NOT re-evaluate or reinterpret precedence rules.
* **14.4 Variable Access Rules:** Variable reads MUST use the LOAD <name> instruction, and variable writes MUST use the STORE <name> instruction. Any expression assigned to a variable MUST be fully evaluated before the STORE operation is executed.
* **14.5 Assignment Evaluation Strategy:** Simple assignments (=) MUST evaluate the right-hand side before storing the result. Compound assignments (+=, -=, *=, /=, %=) MUST first load the current variable value, apply the operation, and then store the updated result.
* **14.6 Control Flow Translation Rules:** Control structures MUST be translated using labels and jump instructions:
    - if statements MUST use JUMP_IF_FALSE and JUMP instructions with uniquely generated labels.
    - while loops MUST use a start label, condition check, and end label with backward jumps.
    - do-while loops MUST execute the body before evaluating the condition.
    - for loops MUST be decomposed into initialization, condition, update, and body sections with separate labels.
    - switch statements MUST be translated using a jump table pattern, where each case value is compared using DUP, PUSH_CONST, and EQUAL before jumping to the corresponding case label.
* **14.7 Label Generation Constraints:** All labels MUST be uniquely generated using an internal counter system. Labels MUST NOT collide across different control structures. Each label MUST clearly represent its purpose (e.g., WHILE_START, IF_END, FOR_CONTINUE).
* **14.8 Break and Continue Handling:** break and continue statements MUST resolve to the nearest active loop context. The Code Generator MUST maintain internal tracking variables (currentBreakLabel, currentContinueLabel) to ensure correct jump destinations. Usage outside loop contexts MUST trigger a code generation error.
* **14.9 Logical Operator Short-Circuiting:** Logical operators MUST implement short-circuit evaluation:
    - && MUST stop evaluation immediately if the left operand is false.
    - || MUST stop evaluation immediately if the left operand is true.
    - These MUST be implemented using conditional jump instructions rather than direct computation.
* **14.10 Method Invocation Rules:** Method calls MUST push all arguments onto the stack in left-to-right order before invocation. The appropriate invocation instruction MUST be selected based on context:
    - INVOKE_STATIC for static methods
    - INVOKE_VIRTUAL for instance methods
    - Return values MUST remain on the stack after execution.
* **14.11 Object and Array Access Rules:** Object field access MUST use FIELD_LOAD and FIELD_STORE instructions. Array access MUST use ARRAY_LOAD and ARRAY_STORE, where index and reference MUST be evaluated before execution.
* **14.12 Literal and Constant Handling:** Numeric literals MUST use PUSH, while string, boolean, and null values MUST use PUSH_CONST. All literals MUST be pushed onto the stack before participation in any operation.
* **14.13 Execution Integrity Rule:** The Code Generator MUST ensure that every emitted instruction corresponds to a valid execution step. The Code Generator MUST NOT generate unreachable or orphaned instructions outside of explicitly defined control flow constructs.