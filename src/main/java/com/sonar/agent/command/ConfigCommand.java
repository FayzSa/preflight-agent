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
    private static final Path CONFIG_DIR  = Paths.get(System.getProperty("user.home"), ".aifix");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.properties");

    @ShellMethod(key = "config-set-key", value = "Store your Anthropic API key in ~/.aifix/config.properties")
    public String configSetApiKey(
            @ShellOption(value = "--api-key", help = "Anthropic API key (sk-ant-...)") String apiKey
    ) {
        if (!apiKey.startsWith("sk-")) {
            return "[33mWarning: Key does not look like an Anthropic key (expected sk-ant-...).[0m\n"
                    + set("spring.ai.anthropic.api-key", apiKey);
        }
        return set("spring.ai.anthropic.api-key", apiKey);
    }

    @ShellMethod(key = "config-set", value = "Set any configuration key (e.g. --key spring.ai.anthropic.chat.options.model --value claude-3-5-haiku-20241022)")
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
        if (Files.exists(CONFIG_FILE)) {
            try (var reader = Files.newBufferedReader(CONFIG_FILE)) {
                props.load(reader);
            }
        }
        return props;
    }

    private void save(Properties props) throws IOException {
        Files.createDirectories(CONFIG_DIR);
        try (var writer = Files.newBufferedWriter(CONFIG_FILE)) {
            props.store(writer, "ai-fix configuration — do not share this file");
        }
        log.debug("Config saved to {}", CONFIG_FILE);
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
