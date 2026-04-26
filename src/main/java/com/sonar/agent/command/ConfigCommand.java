package com.sonar.agent.command;

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
 * Manages local API key storage at ~/.aifix/config.properties.
 * Values are loaded at startup via application.yml property placeholders.
 */
@ShellComponent
public class ConfigCommand {

    private static final Logger log = LoggerFactory.getLogger(ConfigCommand.class);

    @ShellMethod(key = "config-set-key", value = "Store your Google AI API key in ~/.aifix/config.properties")
    public String configSetApiKey(
            @ShellOption(value = "--api-key", help = "Google AI API key (AIza...)") String apiKey
    ) {
        if (!apiKey.startsWith("AIza")) {
            return "[33mWarning: Key does not look like a Google AI key (expected AIza...).[0m\n"
                    + set("spring.ai.google.genai.api-key", apiKey);
        }
        return set("spring.ai.google.genai.api-key", apiKey);
    }

    @ShellMethod(key = "config-set", value = "Set any configuration key (e.g. --key spring.ai.google.genai.chat.options.model --value gemini-2.5-flash)")
    public String configSet(
            @ShellOption(value = "--key",   help = "Configuration property key")   String key,
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
                        + "  Use 'config-set-key --api-key <key>' to get started.";
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

    // ── Overridable for testing ───────────────────────────────────────────────

    protected Path configDir()  { return Paths.get(System.getProperty("user.home"), ".aifix"); }
    protected Path configFile() { return configDir().resolve("config.properties"); }

    // ── Private ───────────────────────────────────────────────────────────────

    private String set(String key, String value) {
        try {
            Properties props = load();
            props.setProperty(key, value);
            save(props);
            String display = isSecret(key) ? mask(value) : value;
            return "[32m✓ Set " + key + " = " + display + "[0m\n"
                    + "  Restart the application for the change to take effect.";
        } catch (IOException e) {
            return "[31mFailed to save config: " + e.getMessage() + "[0m";
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
            props.store(writer, "ai-fix configuration — do not share this file");
        }
        log.debug("Config saved to {}", configFile());
    }

    private boolean isSecret(String key) {
        String lower = key.toLowerCase();
        return lower.contains("key") || lower.contains("secret") || lower.contains("token") || lower.contains("password");
    }

    private String mask(String value) {
        if (value == null || value.length() <= 8) return "****";
        return value.substring(0, 7) + "..." + value.substring(value.length() - 4);
    }
}
