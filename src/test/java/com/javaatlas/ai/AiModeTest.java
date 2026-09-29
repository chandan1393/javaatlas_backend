package com.javaatlas.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class AiModeTest {

    @Test
    void fillsOptionsAndDefaults() {
        String prompt = AiMode.MODERNIZE.system(Map.of("target", "21"));
        assertTrue(prompt.contains("Java 21"));
        assertFalse(prompt.contains("{target}"));
        assertTrue(AiMode.QUIZ.system(Map.of()).contains("5 multiple-choice"));
    }

    @Test
    void unknownModesAreRejected() {
        assertTrue(AiMode.from("review").isPresent());
        assertTrue(AiMode.from("system").isEmpty());
    }
}
