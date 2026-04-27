package com.preflight.ai;

import java.util.Arrays;

public enum AiProvider {
    GEMINI("gemini"),
    GEMMA("gemma"),
    OPENAI("openai"),
    CLAUDE("claude");

    private final String id;

    AiProvider(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static AiProvider from(String value) {
        return Arrays.stream(values())
            .filter(provider -> provider.id.equalsIgnoreCase(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Unsupported AI provider '%s'. Choose one of: gemini, gemma, openai, claude.".formatted(value)));
    }
}
