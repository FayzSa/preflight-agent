package com.sonar.agent.ai;

public interface AiClient {

    String generate(String systemPrompt, String userMessage);
}
