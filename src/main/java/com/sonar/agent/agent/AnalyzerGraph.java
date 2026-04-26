package com.sonar.agent.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonar.agent.agent.models.AnalysisResponse;
import com.sonar.agent.agent.models.DiffResult;
import com.sonar.agent.agent.models.FixProposal;
import com.sonar.agent.tools.FileSystemTool;
import com.sonar.agent.tools.GitOperationsTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates the full Analyze → Format → Patch workflow.
 *
 * Graph steps:
 *   1. Extract  – GitOperationsTool fetches git diff -U5
 *   2. Analyze  – LLM reviews the diff under the Senior Staff Engineer persona
 *   3. Format   – Response is parsed into a typed AnalysisResponse
 *   4. Patch    – FileSystemTool applies each FixProposal to the local file
 */
@Service
public class AnalyzerGraph {

    private static final Logger log = LoggerFactory.getLogger(AnalyzerGraph.class);

    private final ChatClient chatClient;
    private final GitOperationsTool gitTool;
    private final FileSystemTool fileSystemTool;
    private final ObjectMapper objectMapper;

    public AnalyzerGraph(ChatClient chatClient,
                         GitOperationsTool gitTool,
                         FileSystemTool fileSystemTool,
                         ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.gitTool = gitTool;
        this.fileSystemTool = fileSystemTool;
        this.objectMapper = objectMapper;
    }

    // ── Step 1 + 2 + 3: Extract → Analyze → Format ───────────────────────────

    public AnalysisResponse analyze(String workingDirectory) {
        DiffResult diff = gitTool.getUncommittedChanges(workingDirectory);

        if (!diff.hasChanges()) {
            log.info("No uncommitted changes detected.");
            return new AnalysisResponse(List.of());
        }

        log.info("Diff captured: {} file(s) modified", diff.fileDiffs().size());
        return analyze(diff);
    }

    public AnalysisResponse analyze(DiffResult diff) {
        String language = detectLanguage(diff);
        String systemPrompt = buildSystemPrompt(language);
        String userMessage = buildUserMessage(diff.rawDiff());

        log.info("Sending diff to LLM for analysis (language: {})...", language);

        String llmResponse = chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content();

        return parseResponse(llmResponse);
    }

    // ── Step 4: Patch ─────────────────────────────────────────────────────────

    public List<Boolean> applyFixes(AnalysisResponse response, String workingDirectory) {
        return response.issues().stream()
                .map(issue -> fileSystemTool.applyFix(issue, workingDirectory))
                .toList();
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private String detectLanguage(DiffResult diff) {
        List<String> languages = diff.fileDiffs().stream()
                .map(DiffResult.FileDiff::filename)
                .map(this::mapExtensionToLanguage)
                .filter(lang -> !"Unknown".equals(lang))
                .distinct()
                .toList();

        if (languages.isEmpty()) return "Unknown";
        if (languages.size() == 1) return languages.get(0);
        return String.join(", ", languages);
    }

    private String mapExtensionToLanguage(String filename) {
        if (filename.endsWith(".java"))                    return "Java";
        if (filename.endsWith(".kt"))                      return "Kotlin";
        if (filename.endsWith(".js"))                      return "JavaScript";
        if (filename.endsWith(".ts") || filename.endsWith(".tsx")) return "TypeScript";
        if (filename.endsWith(".py"))                      return "Python";
        if (filename.endsWith(".go"))                      return "Go";
        if (filename.endsWith(".rs"))                      return "Rust";
        if (filename.endsWith(".cs"))                      return "C#";
        if (filename.endsWith(".php"))                     return "PHP";
        if (filename.endsWith(".rb"))                      return "Ruby";
        return "Unknown";
    }

    private String buildSystemPrompt(String language) {
        String prompt = SystemPrompts.SENIOR_STAFF_ENGINEER;
        if (!"Unknown".equals(language)) {
            prompt += SystemPrompts.LANGUAGE_SPECIFIC_SUFFIX.formatted(language);
        }
        return prompt;
    }

    private String buildUserMessage(String rawDiff) {
        return """
                Analyze the following git diff for critical security vulnerabilities and bugs.
                Focus only on the added/modified lines (lines starting with '+').

                ```diff
                %s
                ```

                Respond ONLY with the JSON object as instructed. No explanation, no markdown.
                """.formatted(rawDiff);
    }

    private AnalysisResponse parseResponse(String llmResponse) {
        try {
            String json = extractJson(llmResponse);
            return objectMapper.readValue(json, AnalysisResponse.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse LLM response: {}", e.getMessage());
            log.debug("Raw LLM response:\n{}", llmResponse);
            return new AnalysisResponse(List.of());
        }
    }

    private String extractJson(String response) {
        if (response == null || response.isBlank()) return "{}";

        // Strip markdown code fences if present
        if (response.contains("```json")) {
            int start = response.indexOf("```json") + 7;
            int end = response.lastIndexOf("```");
            if (end > start) return response.substring(start, end).trim();
        }
        if (response.contains("```")) {
            int start = response.indexOf("```") + 3;
            int end = response.lastIndexOf("```");
            if (end > start) return response.substring(start, end).trim();
        }

        // Find outermost JSON object bounds
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }

        return response.trim();
    }
}
