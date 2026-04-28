# Project Overview: Lexical Analyzer App (TUP Edition)

## Objective
To build a functional, from-scratch Tokenizer for a Java-like language that visualizes the first phase of a compiler.

## The 7-Token Model
This project classifies all source code into the following categories:
1. **Keywords**: Reserved language words.
2. **Identifiers**: User-defined names (stored in Symbol Table).
3. **Operators**: Math and logic symbols.
4. **Constants**: Fixed numeric values.
5. **Literals**: String data inside quotes.
6. **Punctuation**: Structural separators like `;`.
7. **Special Characters**: Brackets and grouping symbols.

## Technical Architecture
* **Backend**: A Manual Scanner (No Regex API) using a Pointer and Char-Utility logic.
* **Symbol Table**: Tracks identifier occurrences using a HashMap.
* **GUI**: Built with Java Swing, featuring File Drag-and-Drop and real-time statistics (Total Token Count, Lexeme Frequencies).
* **Patterns**: Implements Factory Pattern for token creation and Singleton for the Symbol Table.

To ensure your project stands out at TUP and covers all the requirements we've discussed, here is a comprehensive list of all the features for your **Tokenizer App**. 

I've categorized these into **Backend**, **Frontend**, and **Advanced OOP** features so you can easily assign them to your team members.

---

## 1. Core Backend Features (The Lexer)
These features handle the "Brain" of the application, transforming raw text into categorized data.

* **Manual Character Scanning:** A pointer-based system that iterates through the input string one character at a time (No `java.util.regex` API).
* **7-Category Classification:** 
    1.  **Keywords:** Recognition of reserved words (e.g., `if`, `int`, `while`).
    2.  **Identifiers:** Detection of user-defined names starting with letters/underscores.
    3.  **Operators:** Identification of math and logic symbols.
    4.  **Constants:** Grouping of whole and decimal numbers.
    5.  **Literals:** Handling of String data wrapped in double-quotes (`" "`).
    6.  **Punctuation:** Recognition of structural symbols (`;`, `,`, `.`).
    7.  **Special Characters:** Detection of grouping symbols (`{`, `(`, `[`).
* **Lookahead Logic:** A "peek" function to distinguish single symbols from multi-character ones (e.g., `=` vs `==`).
* **Comment & Whitespace Stripper:** Automatic removal of single-line (`//`) and multi-line (`/* */`) comments and extra spaces before tokenization.

Reference:
    https://docs.oracle.com/javase/specs/jls/se17/html/jls-3.html#jls-3.9
    https://docs.oracle.com/javase/tutorial/java/nutsandbolts/operators.html

---

## 2. Frontend & UX Features (The GUI)
These features focus on your UI/UX requirements and the user-facing side of the app.

* **Code Editor Input:** A `JTextArea` capable of holding thousands of lines of code.
* **File Drag-and-Drop:** Ability to drag `.txt` or `.java` files directly into the input area for instant loading.
* **Line Numbering:** A sidebar that displays line counts, allowing users to track where their code and errors are located.
* **Dynamic Results Table:** A `JTable` that updates in real-time to show:
    * **Lexeme:** The actual text string found.
    * **Token Type:** One of your 7 categories.
    * **Line/Column:** The exact position of the token.
    * **Occurrences:** How many times that specific lexeme has appeared.
* **Statistics Dashboard:** A panel displaying high-level data:
    * Total Token Count.
    * Unique Identifier Count.
    * Execution Time (in milliseconds).
* **Search & Filter:** A search bar to filter the results table (e.g., "Show only Keywords").

---

## 3. Data & Error Management
Features that handle how the application stores information and responds to mistakes.

* **Singleton Symbol Table:** A global `HashMap` that stores all unique identifiers and keeps an active count of their frequencies.
* **Lexical Error Handler:** A dedicated system that catches characters not supported by the language (e.g., `@`, `$` if not part of a literal).
* **Visual Error Reporting:** Highlighting invalid characters in **red** within the results table or displaying them in a dedicated "Error Log" tab.
* **CSV Export:** A button to export the generated token table into a `.csv` file for external use.

---

## 4. Technical OOP Features (Under the Hood)
These are "invisible" features that prove your technical skills for the 2nd-year curriculum.

* **MVC Architecture:** Strict separation between the `backend` (Model/Controller) and `frontend` (View).
* **Factory Pattern Integration:** A `TokenFactory` that centralizes the creation of all token subclasses.
* **Deep Inheritance:** An abstract `Tokens` base class with 7 distinct subclasses, each overriding specific methods for their category.
* **Character Predicate Utility:** A dedicated `CharMatcher` class to handle boolean logic like `isAlpha()`, `isNumeric()`, and `isSpecialOperator()`.

