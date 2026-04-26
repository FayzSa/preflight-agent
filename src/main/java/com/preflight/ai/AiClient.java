package com.preflight.ai;

public interface AiClient {

    String generate(String systemPrompt, String userMessage);
}
