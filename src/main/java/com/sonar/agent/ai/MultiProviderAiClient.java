package com.sonar.agent.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class MultiProviderAiClient implements AiClient {

    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String CONTENT_FIELD_NAME = "content";
    private static final String PARTS_FIELD_NAME = "parts";

    private final AiConfigurationStore configurationStore;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Autowired
    public MultiProviderAiClient(AiConfigurationStore configurationStore, ObjectMapper objectMapper) {
        this(configurationStore, objectMapper, RestClient.create());
    }

    MultiProviderAiClient(AiConfigurationStore configurationStore, ObjectMapper objectMapper, RestClient restClient) {
        this.configurationStore = configurationStore;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    @Override
    public String generate(String systemPrompt, String userMessage) {
        AiRuntimeConfig config = configurationStore.loadRuntimeConfig();
        return switch (config.provider()) {
            case GEMINI -> callGemini(config, systemPrompt, userMessage);
            case OPENAI -> callOpenAi(config, systemPrompt, userMessage);
            case CLAUDE -> callClaude(config, systemPrompt, userMessage);
        };
    }

    private String callGemini(AiRuntimeConfig config, String systemPrompt, String userMessage) {
        String model = config.model().startsWith("models/")
            ? config.model().substring("models/".length())
            : config.model();
        String response = restClient.post()
            .uri("https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent", model)
            .header("x-goog-api-key", config.apiKey())
            .body(buildGeminiRequest(config, systemPrompt, userMessage))
            .retrieve()
            .body(String.class);

        JsonNode root = readTree(response);
        JsonNode parts = root.path("candidates").path(0).path(CONTENT_FIELD_NAME).path(PARTS_FIELD_NAME);
        if (parts.isArray() && !parts.isEmpty()) {
            return parts.get(0).path("text").asText();
        }
        return "";
    }

    private String callOpenAi(AiRuntimeConfig config, String systemPrompt, String userMessage) {
        String response = restClient.post()
            .uri("https://api.openai.com/v1/responses")
            .header("Authorization", "Bearer " + config.apiKey())
            .body(buildOpenAiRequest(config, systemPrompt, userMessage))
            .retrieve()
            .body(String.class);

        JsonNode root = readTree(response);
        String outputText = root.path("output_text").asText(null);
        if (outputText != null) {
            return outputText;
        }

        StringBuilder text = new StringBuilder();
        for (JsonNode item : root.path("output")) {
            for (JsonNode content : item.path("content")) {
                String value = content.path("text").asText(null);
                if (value != null) {
                    text.append(value);
                }
            }
        }
        return text.toString();
    }

    private String callClaude(AiRuntimeConfig config, String systemPrompt, String userMessage) {
        String response = restClient.post()
            .uri("https://api.anthropic.com/v1/messages")
            .header("x-api-key", config.apiKey())
            .header("anthropic-version", ANTHROPIC_VERSION)
            .body(buildClaudeRequest(config, systemPrompt, userMessage))
            .retrieve()
            .body(String.class);

        JsonNode root = readTree(response);
        StringBuilder text = new StringBuilder();
        for (JsonNode content : root.path("content")) {
            if ("text".equals(content.path("type").asText())) {
                text.append(content.path("text").asText());
            }
        }
        return text.toString();
    }

    private ObjectNode buildGeminiRequest(AiRuntimeConfig config, String systemPrompt, String userMessage) {
        ObjectNode request = objectMapper.createObjectNode();
        request.set("systemInstruction", objectMapper.createObjectNode()
            .set("parts", textParts(systemPrompt)));

        ArrayNode contents = objectMapper.createArrayNode();
        contents.add(objectMapper.createObjectNode()
            .put("role", "user")
            .set("parts", textParts(userMessage)));
        request.set("contents", contents);

        request.set("generationConfig", objectMapper.createObjectNode()
            .put("temperature", config.temperature())
            .put("maxOutputTokens", config.maxOutputTokens())
            .put("responseMimeType", "application/json"));
        return request;
    }

    private ObjectNode buildOpenAiRequest(AiRuntimeConfig config, String systemPrompt, String userMessage) {
        return objectMapper.createObjectNode()
            .put("model", config.model())
            .put("instructions", systemPrompt)
            .put("input", userMessage)
            .put("temperature", config.temperature())
            .put("max_output_tokens", config.maxOutputTokens());
    }

    private ObjectNode buildClaudeRequest(AiRuntimeConfig config, String systemPrompt, String userMessage) {
        ArrayNode messages = objectMapper.createArrayNode();
        messages.add(objectMapper.createObjectNode()
            .put("role", "user")
            .put("content", userMessage));

        return objectMapper.createObjectNode()
            .put("model", config.model())
            .put("system", systemPrompt)
            .put("max_tokens", config.maxOutputTokens())
            .put("temperature", config.temperature())
            .set("messages", messages);
    }

    private ArrayNode textParts(String text) {
        ArrayNode parts = objectMapper.createArrayNode();
        parts.add(objectMapper.createObjectNode().put("text", text));
        return parts;
    }

    private JsonNode readTree(String response) {
        try {
            return objectMapper.readTree(response);
        } catch (Exception e) {
            throw new IllegalStateException("AI provider returned invalid JSON: " + e.getMessage(), e);
        }
    }
}
