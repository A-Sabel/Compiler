package com.group3.compiler.utils;

import java.util.ArrayList;
import java.util.List;

/* UTILITY CLASS: Error Handler
Description: A centralized logbook that collects lexical errors for the GUI to display.
Member Note: "The Lexer reports errors here. The GUI will call getErrors() after 
    scanning to display them to the user. Always call clear() before a new scan!" */

public class ErrorHandler {
    private static final List<String> errorLog = new ArrayList<>();

    // Called by the Lexer when it hits the Trap State or an unterminated string
    public static void report(String message, int line, int col) {
        String formattedError = String.format("[Line %d, Col %d] Error: %s", line, col, message);
        errorLog.add(formattedError);
        System.err.println(formattedError); 
    }
    
    // Called by the GUI to check if it needs to display the error panel
    public static boolean hasErrors() {
        return !errorLog.isEmpty();
    }
    // Called by the GUI to get the list of errors to print in a JTextArea
    public static List<String> getErrors() {
        return new ArrayList<>(errorLog); // Return a copy for safety
    }
    // CRITICAL: Called by the GUI's "Analyze" button BEFORE running the Lexer
    public static void clear() {
        errorLog.clear();
    }
}