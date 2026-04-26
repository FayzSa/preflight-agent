package com.preflight.agent.review.prompt;

import com.preflight.agent.models.DiffResult;
import com.preflight.agent.review.dimension.SecurityDimension;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderTest {

    private final PromptBuilder promptBuilder = new PromptBuilder();

    @Test
    void buildSystemPrompt_includesDimensionFocusAndJsonContract() {
        String prompt = promptBuilder.buildSystemPrompt(List.of(new SecurityDimension()), "Java");

        assertThat(prompt).contains("Dimension: SECURITY");
        assertThat(prompt).contains("SQL injection");
        assertThat(prompt).contains("\"issues\"");
        assertThat(prompt).contains("This code is written in Java");
    }

    @Test
    void buildUserMessage_wrapsRawDiff() {
        DiffResult diff = new DiffResult("+String token = request.getHeader(\"token\");", List.of());

        String message = promptBuilder.buildUserMessage(diff);

        assertThat(message).contains("```diff");
        assertThat(message).contains("+String token");
        assertThat(message).contains("Respond ONLY");
    }
}
