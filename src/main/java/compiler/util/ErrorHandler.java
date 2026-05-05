package compiler.util;

import java.util.ArrayList;
import java.util.List;

/* UTILITY CLASS: Error Handler
Description: A centralized logbook that collects lexical errors for the GUI to display.
Member Note: "The Lexer reports errors here. The GUI will call getErrors() after 
    scanning to display them to the user. Always call clear() before a new scan!" */

public class ErrorHandler {
    private static final List<String> errors = new ArrayList<>();
    private static final List<String> warnings = new ArrayList<>();

    // Called by the Lexer when it hits the Trap State or an unterminated string
    public static void reportError(String message, int line, int col) {
        String formattedError = String.format("[Line %d, Col %d] %s", line, col, message);
        errors.add(formattedError);
    }

    public static void reportWarning(String message, int line, int col) {
        String formattedWarning = String.format("[Line %d, Col %d] %s", line, col, message);
        warnings.add(formattedWarning);
    }

    // Called by the GUI to check if it needs to display the error panel
    public static boolean hasErrors() {
        return !errors.isEmpty();
    }

    // Called by the GUI to get the list of errors to print in a JTextArea
    public static List<String> getErrors() {
        return new ArrayList<>(errors); // Return a copy for safety
    }

    public static List<String> getWarnings() {
        return new ArrayList<>(warnings);
    }

    // Convenience alias for backward compatibility.
    public static void report(String message, int line, int col) {
        reportError(message, line, col);
    }

    // CRITICAL: Called by the GUI's "Analyze" button BEFORE running the Lexer
    public static void clear() {
        errors.clear();
        warnings.clear();
    }
}