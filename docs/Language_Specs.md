### Current Lexical Specifications (v1.0)

**1. IDENTIFIERS**
* **Rule:** Must start with a letter (uppercase or lowercase) or an underscore. Subsequent characters can be letters, digits, or underscores.
* **Regex:** `[a-zA-Z_][a-zA-Z0-9_]*`

**2. KEYWORDS**
* **Rule:** Reserved identifiers that hold special syntactic meaning. (These are defined in your `SymbolTable`).
* **Current Supported Set:** Based on your test code, this includes typical Java keywords: `public`, `class`, `int`, `float`, `String`, `if`, etc.

**3. CONSTANTS (Numeric Literals)**
* **Integer Rule:** A sequence of one or more decimal digits.
* **Floating Point Rule:** A sequence of digits, followed by a single dot `.`, followed by one or more digits.
* **Regex:** `[0-9]+(\.[0-9]+)?`

**4. LITERALS (String and Character)**
* **String Rule:** Any sequence of characters enclosed in double quotes `"..."`. Supports escape sequences (`\n`, `\t`, `\\`, `\"`, `\'`).
* **Character Rule:** A single character or escape sequence enclosed in single quotes `'.'`.
* *Note: Cannot span multiple lines unless explicitly escaped.*

**5. OPERATORS**
* **Arithmetic:** `+`, `-`, `*`, `/`, `%`
* **Relational:** `==`, `!=`, `>`, `<`, `>=`, `<=`
* **Logical:** `&&`, `||`, `!`
* **Assignment:** `=`, `+=`, `-=`, `*=`, `/=`
* **Increment/Decrement:** `++`, `--`
* **Special:** `::` (Method Reference), `...` (VarArgs)

**6. PUNCTUATION & SPECIAL CHARACTERS**
* **Punctuation:** `;`, `,`, `.`
* **Special Symbols:** `(`, `)`, `{`, `}`, `[`, `]`, `@`

**7. WHITESPACE (Ignored)**
* Spaces (` `), Tabs (`\t`), Carriage Returns (`\r`), and Newlines (`\n`) are consumed and ignored by the scanner, except when inside a String Literal.
