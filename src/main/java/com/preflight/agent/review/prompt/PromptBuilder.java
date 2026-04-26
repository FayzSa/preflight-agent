package com.preflight.agent.review.prompt;

import com.preflight.agent.models.DiffResult;
import com.preflight.agent.review.dimension.ReviewDimension;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class PromptBuilder {

    public String buildSystemPrompt(Collection<ReviewDimension> dimensions, String language) {
        List<PromptSection> sections = List.of(
            new PromptSection.Role("""
                You are a Senior Staff Engineer and Security Expert with 20+ years of experience.
                You are conducting a critical pre-commit code review.
                """),
            new PromptSection.Task("""
                Analyze the provided git diff and identify ONLY severe issues in the enabled
                review dimensions.
                """),
            new PromptSection.DimensionFocus(buildDimensionFocus(dimensions)),
            new PromptSection.Rules("""
                STRICT RULES:
                - ONLY report issues with severity CRITICAL or HIGH.
                - ONLY analyze lines from the diff that start with '+' (newly added/modified lines).
                - Do NOT report style issues, naming conventions, minor improvements, or speculative concerns.
                - The original_snippet MUST be the exact verbatim text from the diff without the leading '+'.
                - The fixed_snippet MUST be a drop-in replacement for the original_snippet.
                - For SOLID, do not report unless the violation is directly visible within the diff itself.
                """),
            new PromptSection.OutputSchema(OutputSchema.schemaText())
        );

        String prompt = sections.stream()
            .map(PromptSection::render)
            .collect(Collectors.joining("\n"));

        if (language == null || language.isBlank() || "Unknown".equals(language)) {
            return prompt;
        }

        return prompt + "\nAdditional context: This code is written in %s. Apply language-specific security and reliability best practices."
            .formatted(language);
    }

    public String buildUserMessage(DiffResult diff) {
        return """
            Analyze the following git diff for critical issues in the enabled review dimensions.
            Focus only on the added/modified lines (lines starting with '+').
            
            ```diff
            %s
            ```
            
            Respond ONLY with the JSON object as instructed. No explanation, no markdown.
            """.formatted(diff.rawDiff());
    }

    private String buildDimensionFocus(Collection<ReviewDimension> dimensions) {
        if (dimensions == null || dimensions.isEmpty()) {
            return "Enabled dimensions: none. Return {\"issues\": []}.";
        }

        return dimensions.stream()
            .map(dimension -> """
                Dimension: %s
                Focus: %s
                Severity policy: %s
                """.formatted(dimension.id(), dimension.focus().strip(), dimension.severityRules()))
            .collect(Collectors.joining("\n"));
    }
}
