package com.sonar.agent;

import com.sonar.agent.command.ConfigCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigCommandTest {

    // Use a temp config dir so tests don't pollute the real ~/.aifix
    private static final Path TEST_CONFIG_DIR = Paths.get(
            System.getProperty("java.io.tmpdir"), "aifix-test-" + ProcessHandle.current().pid()
    );
    private static final Path TEST_CONFIG_FILE = TEST_CONFIG_DIR.resolve("config.properties");

    ConfigCommand configCommand;

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(TEST_CONFIG_DIR);
        // Point ConfigCommand at the temp directory by overriding the static paths via reflection
        configCommand = new TestableConfigCommand();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (Files.exists(TEST_CONFIG_FILE)) Files.delete(TEST_CONFIG_FILE);
        if (Files.exists(TEST_CONFIG_DIR)) Files.delete(TEST_CONFIG_DIR);
    }

    @Test
    void configSetApiKey_storesKeyInFile() {
        String result = configCommand.configSetApiKey("AIzaTestKey1234");

        assertThat(result).contains("spring.ai.google.genai.api-key");
        assertThat(fileContents()).contains("spring.ai.google.genai.api-key");
        assertThat(fileContents()).contains("AIzaTestKey1234");
    }

    @Test
    void configSetApiKey_warnOnNonGoogleKey() {
        String result = configCommand.configSetApiKey("sk-notAGoogleKey");

        assertThat(result).contains("Warning");
    }

    @Test
    void configSet_storesArbitraryKey() {
        String result = configCommand.configSet("spring.ai.google.genai.chat.options.model", "gemini-2.5-flash");

        assertThat(result).contains("gemini-2.5-flash");
        assertThat(fileContents()).contains("spring.ai.google.genai.chat.options.model");
        assertThat(fileContents()).contains("gemini-2.5-flash");
    }

    @Test
    void configShow_displaysStoredProperties() {
        configCommand.configSet("spring.ai.google.genai.chat.options.model", "gemini-2.5-flash");
        configCommand.configSetApiKey("AIzaSecretKey5678");

        String result = configCommand.configShow();

        assertThat(result).contains("spring.ai.google.genai.chat.options.model");
        assertThat(result).contains("gemini-2.5-flash");
        // API key should be masked
        assertThat(result).doesNotContain("AIzaSecretKey5678");
        assertThat(result).contains("...");
    }

    @Test
    void configShow_returnsHelpMessage_whenEmpty() {
        String result = configCommand.configShow();

        assertThat(result).contains("No configuration saved yet");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String fileContents() {
        try {
            return Files.exists(TEST_CONFIG_FILE) ? Files.readString(TEST_CONFIG_FILE) : "";
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Subclass that redirects config storage to the temp test directory.
     */
    static class TestableConfigCommand extends ConfigCommand {
        @Override
        protected Path configDir() { return TEST_CONFIG_DIR; }

        @Override
        protected Path configFile() { return TEST_CONFIG_FILE; }
    }
}
