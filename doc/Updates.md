# Compiler Project - Updates & Completion Status

## ✅ Completed Components

### 1. Lexer (Tokenizer)

- Full lexical analyzer with character-by-character scanning
- Comment handling (single-line `//` comments)
- Whitespace and comment skipping
- Token generation with position tracking (line and column numbers)
- Symbol table management and reset functionality
- Integration with ErrorHandler for error tracking

### 2. Token Models & Factories

- **Token Type Classes:**
  - `Keyword` - for reserved keywords
  - `Operator` - for operators (+, -, \*, /, etc.)
  - `Identifier` - for variable/function names
  - `Literal` - for string and character literals
  - `Constant` - for numeric constants
  - `Punctuation` - for punctuation marks
  - `SpecialChar` - for special characters
  - `Tokens` - main token class
- `TokenFactory` - token creation utility
- `SymbolTable` - identifier and symbol management

### 3. Parser (Recursive Descent Parser)

- Full grammar implementation for Java subset including:
  - **Statements:** variable declarations, if/else, while, for, blocks, return, print, switch/case
  - **Expressions:** assignments, ternary (`? :`), logical operations, comparisons, arithmetic
  - **Operators:**
    - Assignment: `=`, `+=`, `-=`, `*=`, `/=`
    - Ternary: `? :`
    - Logical: `||`, `&&`
    - Comparison: `==`, `!=`, `<`, `>`, `<=`, `>=`
    - Arithmetic: `+`, `-`, `*`, `/`, `%`
    - Unary: `!`, `-`, `++`, `--`
  - Proper operator precedence and associativity
  - **Smart Error Detection:** Typo detection for type names (e.g., `nt` → suggests `int`)
    - Uses Levenshtein edit distance to suggest corrections
    - Edit distance threshold of 1 for accuracy
  - Error recovery and panic mode for graceful failure
  - Full type keyword support including `String` and user-defined types

### 4. Abstract Syntax Tree (AST)

- `ASTNode` - base class for all syntax tree nodes
- `ASTExporter` - exports/displays AST structure
- Support for all statement and expression types

### 5. Utility Components

- `CharMatcher` - character classification and pattern matching
- `ErrorHandler` - centralized error tracking and reporting

### 6. User Interface (UI)

- `MainGUI` - main application window and interface
- `CodeArea` - code editor component
- `ResultTable` - results and token display table

### 7. Build & Project Configuration

- Maven-based project structure (`pom.xml`)
- Organized package hierarchy
- Test framework setup (`AppTest.java`)
- Compiled classes in `target/` directory

---

## ⚠️ In Progress / Partially Complete

### 1. Semantic Analysis (`sematics/` folder)

- ✅ **Type Checking:** Full type inference and compatibility checking
  - Primitive types: `int`, `double`, `float`, `boolean`, `char`, `byte`, `short`, `long`
  - Reference types: `String` and user-defined classes
  - Numeric type widening (e.g., `int` → `double`)
- ✅ **Variable Declaration Validation:** Duplicate definition detection, scope tracking
- ✅ **Assignment Validation:** Type compatibility checking with error reporting
- ✅ **Expression Type Inference:** Full support for all expression types
  - Binary operations with type checking
  - Unary operations
  - **Ternary expressions** with incompatible branch detection
  - Method calls with signature matching
  - Array access
  - Casting
- ✅ **Scope & Symbol Management:** Variable definition and lookup
- ✅ **Control Flow Validation:**
  - Loop depth tracking for `break`/`continue` validation
  - Return type checking
  - Boolean condition validation for control structures
- ✅ **Smart Error Suppression:** Sentinel Type Strategy
  - Uses `"type_error"` sentinel to prevent duplicate error reporting
  - Eliminates "Double Jeopardy" (reporting the same error twice)
  - Root cause errors reported at source, secondary errors suppressed
- ⚠️ **Not Yet Implemented:**
  - Method overload resolution (basic single-signature support only)
  - Array type tracking
  - Generic types
  - Lambda expressions
  - Exception type checking

---

## 🎯 Recent Improvements (April 30, 2026)

### Parser Enhancements

- Fixed ternary operator (`?`, `:`) parsing by recognizing them as `OPERATOR` tokens
- Added `"String"` to recognized type keywords in `isTypeKeyword()`
- Implemented intelligent type typo detection using Levenshtein edit distance
  - Example: `nt a = 10;` → suggests `int` with edit distance ≤ 1
- Error recovery with panic mode for graceful failure handling

### Semantic Analysis Improvements

- Implemented **Sentinel Type Strategy** for clean error reporting
  - Returns `"type_error"` sentinel when expression validation fails
  - Prevents duplicate error cascades ("Double Jeopardy")
  - Only root cause errors are reported to the user
- Enhanced ternary expression type checking
  - Detects incompatible branch types
  - Reports error at ternary site, not at initialization
- Full variable scope tracking with symbol table integration
- Comprehensive loop control validation (`break`/`continue`)

### Compiler UX

- ✨ **Smart:** Detects and suggests type name typos
- ✨ **Clean:** Eliminates redundant error reporting
- ✨ **Professional:** Reports only the root cause, not cascading secondary errors

---

### ⚠️ In Progress / Partially Complete

### 3. Code Generation (codegen/ folder)

- ✅ **TAC-Based Code Generation (Core Implementation Completed):**
  - Implemented full AST → Three-Address Code (TAC) translation using `BytecodeGenerator`
  - Supports recursive depth-first traversal of AST nodes
  - `visit()` returns a TAC address (temp variable, named variable, or literal) for every expression node
  - Complex expressions are automatically broken into single-operator steps using fresh temporaries (t0, t1, t2, …)
  - Instructions are represented using `Instruction` with up to four fields: `result`, `arg1`, `op`, `arg2`
- ✅ **Expression Code Generation:**
  - Arithmetic operations (+, -, *, /, %) each produce one TAC instruction: `t1 = left op right`
  - Unary operations (-, !, ~, ++, --) fully supported with explicit temp results
  - Binary comparisons (==, !=, <, >, <=, >=) produce boolean temp results
  - Logical operators (&&, ||) implemented using short-circuit jump logic with temp result variables
  - Postfix operators (x++, x--) correctly save the original value into a temp before updating
- ✅ **Variable Handling:**
  - Variable declaration with initializer emits: `varName = <expr result>` (COPY instruction)
  - Variable declaration without initializer emits: `varName = null` (LOAD_CONST)
  - Variable reads return the variable name directly as a TAC address — no extra instruction emitted
  - Compound assignments (+=, -=, *=, /=, %=) generate: `t = var op expr`, then `var = t`
- ✅ **Control Flow Generation:**
  - if-else statements use `ifFalse <cond> goto ELSE_n` and labeled blocks
  - while loops use start/end labels with conditional jump
  - do-while loops execute body before condition check
  - for loops decomposed into:
    - initialization
    - condition check with `ifFalse` jump
    - body execution
    - FOR_CONTINUE label for update section
    - FOR_END label for exit
  - break and continue implemented using tracked loop labels (`currentBreakLabel`, `currentContinueLabel`) with context saved and restored on nesting
  - ⚠️ switch statements — generator logic implemented (uses temp comparison: `t = switchVal == caseVal; ifTrue t goto CASE_n`), pending parser fix for CASE node values
- ✅ **Function / Method Support:**
  - Method declarations use `begin_method name:returnType` and `end_method name`
  - Parameters declared using `param <name>`
  - Arguments to call sites emitted as individual `arg <value>` instructions before the call
  - Method calls support both:
    - `call <name> <argCount>` (static)
    - `callvirtual <obj> <name> <argCount>` (instance)
  - Return values handled using `return` (void) and `return <value>`
- ✅ **Object & Memory Operations:**
  - Object creation: arguments emitted as `arg` instructions, then `result = new <type>(<n> args)`
  - Field read: `result = object.field` (FIELD_LOAD)
  - Field write: `object.field = src` (FIELD_STORE)
  - Array read: `result = base[index]` (ARRAY_LOAD)
  - Array write: `base[index] = src` (ARRAY_STORE)
- ✅ **Utility Features:**
  - Automatic temp variable generation (`newTemp()`) for all intermediate values
  - Automatic label generation (`newLabel()`) for all control flow structures
  - Centralized `emit()` system for instruction creation
  - Ternary expressions (`? :`) supported with temp result and label-based branching

### 4. Virtual Machine (vm/ folder)

  - ❌ Not yet implemented
  - TAC is currently generated but not executed

### 🎯 Recent Improvements (Code Generation Layer)

- **Switched from Stack-Based to Three-Address Code (TAC)**
  - Replaced stack opcodes (PUSH, POP, DUP, ADD, etc.) with explicit TAC instructions
  - `Instruction` class redesigned with `result`, `arg1`, `op`, `arg2` fields and factory methods (e.g. `Instruction.binary(...)`, `Instruction.copy(...)`, `Instruction.loadConst(...)`)
  - `toString()` on instructions now prints proper TAC notation (e.g. `t1 = 3 * 2`)
  - `visit()` in `BytecodeGenerator` now returns a String TAC address instead of relying on an implicit stack
- **BytecodeGenerator Refactor**
  - All handler methods updated to receive and return TAC addresses
  - Added `addressOf()` helper to read an lvalue node as a TAC address without emitting extra instructions
  - Added `storeInto()` helper to emit the correct store instruction for any lvalue (identifier, array slot, or field)
  - Added `binaryOpcode()` and `compoundOpcode()` lookup helpers for clean operator mapping
- **Control Flow Enhancements**
  - Improved for-loop translation with explicit FOR_START, FOR_CONTINUE, and FOR_END labels
  - Fixed structured jump handling for nested loops
  - Ensured correct save/restore of previous loop context (`currentBreakLabel`, `currentContinueLabel`) on every loop entry/exit
- **Expression Evaluation Improvements**
  - Each sub-expression result stored in a fresh temp, enforcing single-operator TAC form
  - Short-circuit evaluation for `&&` and `||` uses temp result variable written in both branches
  - Postfix increment/decrement correctly separates the "before" value (returned) from the updated value (stored back)


## 4 Code Optimizer — Update Notes

## ✅ Completed Components

### Suggested Improvements

- ✅ **Suggest 1 — Multi-pass architecture**
  - All optimizations previously ran in a single recursive sweep; constants folded in child nodes did not re-trigger simplifications on the parent in the same pass (e.g. `(2 + 3) * 0` never fully resolved)
  - `optimize()` now loops with a `changed` flag: repeats until a full pass produces no changes, guaranteeing all cascading simplifications are resolved

- ✅ **Suggest 2 — Track which optimizations fired**
  - Optimizer previously returned only an `ASTNode` with no diagnostic info about what changed
  - Public method now returns `OptimizeResult` (carries `node`, `changed`, `ruleApplied`, and `ruleCounts` map); a private `fire(String rule)` helper records every optimization site and accumulates counts across all passes

- ✅ **Suggest 3 — Preserve source coordinates on synthesized nodes**
  - `simplifyBoolean()` and `simplifyAlgebraic()` returned raw child nodes whose coordinates pointed to the sub-expression, not the optimized site
  - `copyCoords(ASTNode target, ASTNode site)` helper added; all surviving child returns from algebraic/boolean simplification now go through it, stamping the parent's line and column

- ✅ **Suggest 4 — `BLOCK` dead-code sweep**
  - Optimizer traversed all children of a `BLOCK` unconditionally — statements after `RETURN`, `BREAK`, or `CONTINUE` were passed to the code generator as live nodes
  - `eliminateDeadStatementsInBlock()` now dispatched for every `BLOCK`; scans child list, finds the first terminal statement, truncates everything after it in O(n)

- ✅ **Suggest 5 — Fix `removeTrailingZero()` to preserve floating-point type**
  - `removeTrailingZero()` silently converted whole-number float results to integer strings (e.g. `3.0 - 1.0` → `"2"` instead of `"2.0"`), causing the `NUMBER` node to be inferred as `int` downstream
  - Replaced with `formatDouble(double value, boolean isFloatContext)`; when `isFloatContext` is `true` and the result is a whole number, `.0` is always appended

---

### Missing Optimizations

- ✅ **Missing 1 — Unary constant folding**
  - `UNARY_OP` nodes were never simplified even when the operand was a compile-time literal (`!true`, `-5`, `!!x` all passed through unoptimized)
  - `foldUnary()` added and called from `optimizeNode()`; handles `!true/false` → boolean flip, `-NUMBER` → negated value, and `~~x`/`!!x` → `x` (double-negation elimination)

- ✅ **Missing 2 — Constant-condition `while(true)` not handled**
  - `eliminateFalseWhile()` only removed `while(false)`; `while(true)` produced no annotation and folded conditions were never re-checked on the while node
  - Replaced with `eliminateConstantWhile()`: `while(false)` returns `null` (node removed), `while(true)` attaches `setAttribute("infinite_loop", "true")` for downstream phases

- ✅ **Missing 3 — Dead code after `return` / `break` / `continue` in a `BLOCK`**
  - Optimizer never pruned statements following a terminal statement within the same block, leaving dead nodes for all downstream phases to process
  - Covered by Suggest 4 — `eliminateDeadStatementsInBlock()` handles this case

- ✅ **Missing 4 — Strength reduction**
  - No AST-level strength reduction existed; `x * 2`, `x * 4`, `x / 8` etc. were never converted to shifts even though Rule 20.3 requires it
  - `strengthReduce()` added: detects multiply/divide where one operand is a compile-time power-of-two integer literal; replaces with `x << n` or `x >> n` using `powerOfTwoShift()` and `makeShift()`

- ✅ **Missing 5 — Common subexpression elimination (CSE)**
  - Identical pure expressions appearing multiple times in the same scope (e.g. `a + b` twice) were computed twice with no sharing
  - `eliminateCommonSubexpression()` added as the last pass in `optimizeNode()`; uses a `cseTable` map with canonical string keys (commutative operators normalized lexicographically); first occurrence annotated with `cse_key`, second replaced with a `TEMP_REF` node; side-effecting nodes excluded via `isPureExpression()`

---

### Warnings

- ✅ **Warning 1 — Integer overflow silently produces wrong folded constants**
  - `evaluateNumericBinary()` used `long` arithmetic throughout, so `2000000000 + 2000000000` folded to `"4000000000"` — valid as `long` but an overflow for `int` — with no range check against the operands' inferred type
  - `inferNumericType()` added to infer the narrowest type covering both operands; `checkIntegerRange()` tests the folded result against it; on violation, `evaluateNumericBinary()` returns `"OVERFLOW:<detail>"` and `foldConstants()` leaves the node unfolded with `setAttribute("fold_overflow", detail)` so Rule 12 still sees the original expression

- ✅ **Warning 2 — `removeTrailingZero()` loses floating-point type information**
  - `3.0 - 1.0` folded to `"2"` instead of `"2.0"`, causing the `NUMBER` node to be inferred as `int` by the semantic analyzer and code generator — a silent type-narrowing introduced by the optimizer
  - Covered by Suggest 5 — `removeTrailingZero()` deleted and replaced with `formatDouble()`

- ✅ **Warning 3 — Comparison operators not folded for numeric literals**
  - `evaluateNumericBinary()` only handled arithmetic operators; `if (5 > 3)` was never folded to `if (true)`, so `eliminateConstantIf()` never fired for numeric comparisons even when the condition was statically known
  - All six comparison operators (`<`, `>`, `<=`, `>=`, `==`, `!=`) added to both the `long` and `double` branches of `evaluateNumericBinary()`; results returned as `"true"`/`"false"` and wrapped in a `LITERAL` node so `eliminateConstantIf()` can act on them

---

### Bug Fixes

- ✅ **Bug 1 — Mutating `getChildren()` in place during iteration**
  - `optimizeNode()` called `children.remove(i)` and `i--` on the list returned by `getChildren()`; if the list is the node's internal reference, removing a null-optimized node shifted indices and caused the loop to silently skip the next sibling
  - Traversal now builds a fresh `ArrayList<ASTNode>` and calls `node.setChildren(newList)` after the loop — no in-place mutation occurs

- ✅ **Bug 2 — Ternary elimination returning wrapper node instead of expression**
  - `eliminateConstantTernary()` returned `thenWrapper` or `elseWrapper` directly (structural `THEN`/`ELSE` container nodes), not the expression inside them, causing crashes in every downstream phase that expected an expression node
  - Wrapper is now unwrapped before returning: `thenWrapper.getChildren().get(0)` (the actual expression) is returned; same unwrap applied to `elseWrapper` for the `false` branch

- ✅ **Bug 3 — `simplifyAlgebraic()` silently drops side effects on identity and zero rules**
  - Identity rules (`x * 1`, `x + 0`, `x - 0`, `x / 1`) returned the non-literal operand without checking purity — `1 * bar()` was fine incidentally, but the guard was never explicit; multiply-by-zero (`x * 0` → `NUMBER "0"`) discarded both operands entirely, silently dropping any side effects (e.g. `sideEffect() * 0`)
  - All identity rules now include `isPureExpression(survivingOperand)` before returning; multiply-by-zero requires `isPureExpression(left) && isPureExpression(right)` before collapsing — if either side has effects, the node is left untouched. `isPureExpression()` (already used by CSE) recursively returns `false` for any subtree containing `CALL` or `ASSIGN`

---


## Last Updated

May 3, 2026

---

## Recent Work (May 4, 2026)

- Added a runnable `compiler.vm.InterpreterTest` harness and verified the interpreter with a small TAC program; it executed successfully and printed `5`.
- Extended `Interpreter` to cover more TAC opcodes, including loads/stores, arrays, field access, object creation, and basic call handling.
- Added interpreter metadata pre-scan for labels, method ranges, and exception table entries.
- Ran a Java-only compile check for main sources with `javac`; main sources compiled successfully, with one unchecked-cast warning in `Interpreter.java`.
- Attempted to run Maven tests, but `mvn` is not available in the current environment. A direct `javac` compile of test sources also failed because JUnit is not on the classpath.

## Current Gaps / Risks

- `Interpreter` still uses a simplified runtime model for objects and method dispatch; user-defined method invocation is only partially wired.
- Exception handling is parsed but not fully enforced against catch types yet.
- `Interpreter.java` has a small unchecked-cast warning that should be narrowed or suppressed locally.
- `BytecodeGenerator.java` still contains many IDE style hints and a few runtime error paths that would benefit from clearer validation or tests.
- Test execution is blocked until Maven or the required JUnit jars are installed.