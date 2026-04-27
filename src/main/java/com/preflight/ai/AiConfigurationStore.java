package com.preflight.ai;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Properties;

@Component
public class AiConfigurationStore {

    public static final String PROVIDER_KEY = "ai-fix.ai.provider";
    public static final String MAX_OUTPUT_TOKENS_KEY = "ai-fix.ai.max-output-tokens";
    public static final String TEMPERATURE_KEY = "ai-fix.ai.temperature";

    public AiRuntimeConfig loadRuntimeConfig() {
        try {
            Properties props = load();
            String providerValue = firstNonBlank(
                props.getProperty(PROVIDER_KEY),
                System.getenv("AI_FIX_AI_PROVIDER")
            );
            if (isBlank(providerValue)) {
                throw new IllegalStateException("""
                    AI provider is not selected.
                    Run: config-select-ai --provider gemini
                    Then set its key: config-set-key --provider gemini --api-key <key>
                    Supported providers: gemini, gemma, openai, claude.
                    """);
            }

            AiProvider provider = AiProvider.from(providerValue);
            String providerId = provider.id();
            String apiKey = firstNonBlank(
                props.getProperty("ai-fix.ai.%s.api-key".formatted(providerId)),
                providerApiKeyFromEnvironment(provider)
            );
            if (isBlank(apiKey)) {
                throw new IllegalStateException("""
                    API key is not configured for %s.
                    Run: config-set-key --provider %s --api-key <key>
                    """.formatted(providerId, providerId));
            }

            String model = firstNonBlank(
                props.getProperty("ai-fix.ai.%s.model".formatted(providerId)),
                System.getenv("AI_FIX_%s_MODEL".formatted(providerId.toUpperCase(Locale.ROOT))),
                defaultModel(provider)
            );

            int maxOutputTokens = parseInt(firstNonBlank(
                props.getProperty(MAX_OUTPUT_TOKENS_KEY),
                System.getenv("AI_FIX_AI_MAX_OUTPUT_TOKENS"),
                "8096"
            ));
            double temperature = parseDouble(firstNonBlank(
                props.getProperty(TEMPERATURE_KEY),
                System.getenv("AI_FIX_AI_TEMPERATURE"),
                "0.1"
            ));

            return new AiRuntimeConfig(provider, apiKey, model, maxOutputTokens, temperature);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read AI configuration: " + e.getMessage(), e);
        }
    }

    protected Path configDir() {
        return Paths.get(System.getProperty("user.home"), ".aifix");
    }

    protected Path configFile() {
        return configDir().resolve("config.properties");
    }

    private Properties load() throws IOException {
        Properties props = new Properties();
        if (Files.exists(configFile())) {
            try (var reader = Files.newBufferedReader(configFile())) {
                props.load(reader);
            }
        }
        return props;
    }

    private String providerApiKeyFromEnvironment(AiProvider provider) {
        return switch (provider) {
            case GEMINI -> firstNonBlank(
                System.getenv("AI_FIX_GEMINI_API_KEY"),
                System.getenv("GEMINI_API_KEY"),
                System.getenv("GOOGLE_AI_API_KEY")
            );
            case GEMMA -> firstNonBlank(
                System.getenv("AI_FIX_GEMMA_API_KEY"),
                System.getenv("GEMMA_API_KEY"),
                System.getenv("GOOGLE_AI_API_KEY")
            );
            case OPENAI -> firstNonBlank(
                System.getenv("AI_FIX_OPENAI_API_KEY"),
                System.getenv("OPENAI_API_KEY")
            );
            case CLAUDE -> firstNonBlank(
                System.getenv("AI_FIX_CLAUDE_API_KEY"),
                System.getenv("ANTHROPIC_API_KEY")
            );
        };
    }

    private String defaultModel(AiProvider provider) {
        return switch (provider) {
            case GEMINI -> "gemini-2.5-flash";
            case GEMMA -> "gemma-4-31b-it";
            case OPENAI -> "gpt-4.1-mini";
            case CLAUDE -> "claude-sonnet-4-20250514";
        };
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid integer AI configuration value: " + value, e);
        }
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid decimal AI configuration value: " + value, e);
        }
    }
}
