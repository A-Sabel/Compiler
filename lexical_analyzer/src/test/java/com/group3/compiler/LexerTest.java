package com.group3.compiler;

import com.group3.compiler.backend.Lexer;
import com.group3.compiler.backend.models.Tokens;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

public class LexerTest {

    @Test
    public void testVariableDeclaration() {
        Lexer lexer = new Lexer("int count = 5;");
        List<Tokens> tokens = lexer.tokenize();
        assertEquals(5, tokens.size(), "Should produce exactly 5 tokens");
        assertEquals("int", tokens.get(0).getLexeme());
        assertEquals("KEYWORD", tokens.get(0).getClass().getSimpleName().toUpperCase());
        assertEquals("count", tokens.get(1).getLexeme());
        assertTrue(tokens.get(1).getColumn() > 0); 
    }
}