package com.sonar.agent.agent.review.orchestration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonar.agent.ai.AiClient;
import com.sonar.agent.agent.models.AnalysisResponse;
import com.sonar.agent.agent.models.FixProposal;
import com.sonar.agent.agent.review.prompt.OutputSchema;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LlmReviewClient {

    private static final Logger log = LoggerFactory.getLogger(LlmReviewClient.class);

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    public LlmReviewClient(AiClient aiClient, ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
    }

    public List<FixProposal> review(String systemPrompt, String userMessage) {
        String llmResponse = aiClient.generate(systemPrompt, userMessage);
        return parseResponse(llmResponse).issues();
    }

    private AnalysisResponse parseResponse(String llmResponse) {
        try {
            String json = extractJson(llmResponse);
            AnalysisResponse response = objectMapper.readValue(json, OutputSchema.responseType());
            return response.issues() == null ? new AnalysisResponse(List.of()) : response;
        } catch (JsonProcessingException e) {
            log.error("Failed to parse LLM response: {}", e.getMessage());
            log.debug("Raw LLM response:\n{}", llmResponse);
            return new AnalysisResponse(List.of());
        }
    }

    private String extractJson(String response) {
        if (response == null || response.isBlank()) {
            return "{}";
        }

        if (response.contains("```json")) {
            int start = response.indexOf("```json") + 7;
            int end = response.lastIndexOf("```");
            if (end > start) {
                return response.substring(start, end).trim();
            }
        }
        if (response.contains("```")) {
            int start = response.indexOf("```") + 3;
            int end = response.lastIndexOf("```");
            if (end > start) {
                return response.substring(start, end).trim();
            }
        }

        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }

        return response.trim();
    }
}
