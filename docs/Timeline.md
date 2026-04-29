## **The Building Blocks**
**Goal:** Establish the data models and the basic UI shell.

| **Team A: Backend & Utils** | **Team B: Frontend** |
| :--- | :--- |
| **Day 1:** Implement `Tokens.java` and all 7 subclasses. Define common fields (lexeme, type, line). | **Day 1:** Create `MainGUI.java`. Set up the `BorderLayout` with placeholders for Input and Output. |
| **Day 2:** Build `CharMatcher.java`. Write manual logic for `isAlpha`, `isDigit`, `isOperator`, etc. | **Day 2:** Implement `CodeArea.java`. Add the `JScrollPane` and basic `JTextArea` configuration. |
| **Day 3:** Set up `SymbolTable.java`. Create the `HashMap` to store unique identifiers and their counts. | **Day 3:** Implement **Drag & Drop** functionality so text files can be loaded into the `CodeArea`. |
| **Day 4:** Build `TokenFactory.java`. Ensure it can generate any of the 7 subclasses based on input. | **Day 4:** Sync Point: Ensure the `JTable` columns match the fields defined in Team A's `Tokens` class. |

---

## **The Logic Engine**
**Goal:** Get the scanner running and connect it to the interface.

| **Team A: Backend & Utils** | **Team B: Frontend** |
| :--- | :--- |
| **Day 1:** Implement `Lexer.java` state logic. Create the `index` pointer and the `skipWhitespaceAndComments` helper. | **Day 1:** Build `ResultTable.java`. Customizing the `DefaultTableModel` to handle dynamic row additions. |
| **Day 2:** Write the `peek()` lookahead logic. Handle multi-character operators (like `==` or `!=`). | **Day 2:** Create the "Tokenize" button listener. It should grab text and call a dummy Lexer method. |
| **Day 3:** Develop the main `getNextToken()` loop. This is the "brain" that calls `CharMatcher` and `TokenFactory`. | **Day 3:** Connect the real Lexer. When clicked, the button should now populate the `JTable` with real tokens. |
| **Day 4:** Finalize Alphanumeric logic. Ensure it distinguishes between `Keywords` and `Identifiers`. | **Day 4:** Sync Point: Test a simple "Hello World" code snippet to see if it moves from Input to Table. |

---

## **Polish & Error Handling**
**Goal:** Handle the "messy" parts of coding and make the UI look professional.

| **Team A: Backend & Utils** | **Team B: Frontend** |
| :--- | :--- |
| **Day 1:** Implement `ErrorHandler.java`. Logic to catch characters that don't match any of the 7 categories. | **Day 1:** Add the **Stats Panel**. Display Total Tokens, Total Lexemes, and Error counts. |
| **Day 2:** Refine `Literal` (String) scanning. Ensure the scanner handles text inside double quotes correctly. | **Day 2:** Implement **Table Colors**. (e.g., Keywords = Blue, Errors = Red) using a `TableCellRenderer`. |
| **Day 3:** Finalize `SymbolTable` count logic. Ensure duplicate identifiers only show once but with high "occurrences." | **Day 3:** UI/UX Polish. Add line numbers to the `CodeArea` and a "Clear" button to reset the app. |
| **Day 4:** Stress Test. Run complex Java files through the app and fix any index-out-of-bounds crashes. | **Day 4:** Final Presentation Prep. Ensure the `README` and `Summary.md` are up to date in the repo. |

