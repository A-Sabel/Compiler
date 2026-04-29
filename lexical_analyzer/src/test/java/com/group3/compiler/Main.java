package com.group3.compiler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.group3.compiler.backend.Lexer;
import com.group3.compiler.backend.models.Tokens;

public class Main {
    public static void main(String[] args) {
        String testCode = """
                        public class BilliardsApp {
                            int tableCount = 10;
                            float hourlyRate = 150.50;
                            String status = "Available";
                            if (tableCount >= 1 && status == "Available") {
                                System.out.println("Table ready.\\n");
                            }
                        }""";

        System.out.println("--- Starting Lexical Analysis ---");
        Lexer lexer = new Lexer(testCode);
        List<Tokens> tokenStream = lexer.tokenize();

        // 1. Calculate Occurrences for ALL Lexemes and Categories
        Map<String, Integer> lexemeCounts = new HashMap<>();
        Map<String, Integer> categoryCounts = new HashMap<>();

        for (Tokens t : tokenStream) {
            String lexeme = t.getLexeme();
            String category = t.getClass().getSimpleName().toUpperCase();
            
            // Count how many times each specific word/symbol appears
            lexemeCounts.put(lexeme, lexemeCounts.getOrDefault(lexeme, 0) + 1);
            // Count how many tokens belong to each category
            categoryCounts.put(category, categoryCounts.getOrDefault(category, 0) + 1);
        }

        // 2. Print Main Table
        System.out.printf("%-20s %-20s %-10s %-10s %-15s\n", "LEXEME", "CATEGORY", "LINE", "COL", "OCCURRENCES");
        System.out.println("--------------------------------------------------------------------------------");
        for (Tokens t : tokenStream) {
            String category = t.getClass().getSimpleName().toUpperCase();
            int occurrences = lexemeCounts.get(t.getLexeme());

            System.out.printf("%-20s %-20s %-10d %-10d %-15d\n", 
                t.getLexeme(), 
                category, 
                t.getLine(), 
                t.getColumn(),
                occurrences);
        }

        // 3. Print Summary Table
        System.out.println("\n--- Token Category Summary ---");
        System.out.printf("%-20s %-15s\n", "CATEGORY", "COUNT");
        System.out.println("-----------------------------------");
        for (Map.Entry<String, Integer> entry : categoryCounts.entrySet()) {
            System.out.printf("%-20s %-15d\n", entry.getKey(), entry.getValue());
        }
        System.out.println("-----------------------------------");
        
        // 4. Print Total Tokens
        System.out.println("TOTAL TOKENS: " + tokenStream.size());
    }
}