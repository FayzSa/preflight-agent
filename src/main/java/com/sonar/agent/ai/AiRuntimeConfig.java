package com.sonar.agent.ai;

public record AiRuntimeConfig(
        AiProvider provider,
        String apiKey,
        String model,
        int maxOutputTokens,
        double temperature
) {}
