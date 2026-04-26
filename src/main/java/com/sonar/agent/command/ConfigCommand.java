package com.sonar.agent.command;

import com.sonar.agent.ai.AiProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Manages local configuration storage at ~/.aifix/config.properties.
 */
@ShellComponent
public class ConfigCommand {

    private static final Logger log = LoggerFactory.getLogger(ConfigCommand.class);

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";

    @ShellMethod(key = "config-select-ai", value = "Select which AI provider to use: gemini, openai, or claude")
    public String configSelectAi(
        @ShellOption(value = "--provider", help = "AI provider: gemini, openai, or claude") String provider
    ) {
        try {
            AiProvider selected = AiProvider.from(provider);
            return set("ai-fix.ai.provider", selected.id());
        } catch (IllegalArgumentException e) {
            return red(e.getMessage());
        }
    }

    @ShellMethod(key = "config-set-key", value = "Store an API key for an AI provider")
    public String configSetApiKey(
        @ShellOption(value = "--provider", help = "AI provider: gemini, openai, or claude") String provider,
        @ShellOption(value = "--api-key", help = "Provider API key or token") String apiKey
    ) {
        try {
            AiProvider selected = AiProvider.from(provider);
            String warning = keyWarning(selected, apiKey);
            return warning + set("ai-fix.ai.%s.api-key".formatted(selected.id()), apiKey);
        } catch (IllegalArgumentException e) {
            return red(e.getMessage());
        }
    }

    @ShellMethod(key = "config-set-model", value = "Set the model used by an AI provider")
    public String configSetModel(
        @ShellOption(value = "--provider", help = "AI provider: gemini, openai, or claude") String provider,
        @ShellOption(value = "--model", help = "Model name for that provider") String model
    ) {
        try {
            AiProvider selected = AiProvider.from(provider);
            return set("ai-fix.ai.%s.model".formatted(selected.id()), model);
        } catch (IllegalArgumentException e) {
            return red(e.getMessage());
        }
    }

    @ShellMethod(key = "config-set", value = "Set any configuration key")
    public String configSet(
        @ShellOption(value = "--key", help = "Configuration property key") String key,
        @ShellOption(value = "--value", help = "Configuration property value") String value
    ) {
        return set(key, value);
    }

    @ShellMethod(key = "config-show", value = "Display current configuration (secrets are masked)")
    public String configShow() {
        try {
            Properties props = load();
            if (props.isEmpty()) {
                return "No configuration saved yet.\n"
                    + "  Run: config-select-ai --provider gemini\n"
                    + "  Then: config-set-key --provider gemini --api-key <key>";
            }

            StringBuilder sb = new StringBuilder("Saved configuration (~/.aifix/config.properties):\n");
            props.forEach((k, v) -> {
                String display = isSecret(k.toString()) ? mask(v.toString()) : v.toString();
                sb.append("  ").append(k).append(" = ").append(display).append("\n");
            });
            return sb.toString();
        } catch (IOException e) {
            return "Failed to read config: " + e.getMessage();
        }
    }

    protected Path configDir() {
        return Paths.get(System.getProperty("user.home"), ".aifix");
    }

    protected Path configFile() {
        return configDir().resolve("config.properties");
    }

    private String set(String key, String value) {
        try {
            Properties props = load();
            props.setProperty(key, value);
            save(props);
            String display = isSecret(key) ? mask(value) : value;
            return green("Set " + key + " = " + display) + "\n"
                + "  This value is used on the next scan, fix, or webhook review.";
        } catch (IOException e) {
            return red("Failed to save config: " + e.getMessage());
        }
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

    private void save(Properties props) throws IOException {
        Files.createDirectories(configDir());
        try (var writer = Files.newBufferedWriter(configFile())) {
            props.store(writer, "ai-fix configuration - do not share this file");
        }
        log.debug("Config saved to {}", configFile());
    }

    private String keyWarning(AiProvider provider, String apiKey) {
        boolean suspicious = switch (provider) {
            case GEMINI -> !apiKey.startsWith("AIza");
            case OPENAI -> !apiKey.startsWith("sk-");
            case CLAUDE -> !apiKey.startsWith("sk-ant-");
        };
        if (!suspicious) {
            return "";
        }
        return yellow("Warning: key does not look like a typical %s API key.".formatted(provider.id())) + "\n";
    }

    private boolean isSecret(String key) {
        String lower = key.toLowerCase();
        return lower.contains("key") || lower.contains("secret") || lower.contains("token") || lower.contains("password");
    }

    private String mask(String value) {
        if (value == null || value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 7) + "..." + value.substring(value.length() - 4);
    }

    private String red(String text) {
        return ANSI_RED + text + ANSI_RESET;
    }

    private String green(String text) {
        return ANSI_GREEN + text + ANSI_RESET;
    }

    private String yellow(String text) {
        return ANSI_YELLOW + text + ANSI_RESET;
    }
}
