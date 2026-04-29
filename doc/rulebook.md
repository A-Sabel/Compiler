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