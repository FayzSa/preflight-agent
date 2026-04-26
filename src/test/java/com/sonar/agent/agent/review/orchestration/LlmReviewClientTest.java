package com.sonar.agent.agent.review.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonar.agent.ai.AiClient;
import com.sonar.agent.agent.models.FixProposal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmReviewClientTest {

    @Test
    void review_extractsJsonFromMarkdownFence() {
        String response = """
                ```json
                {
                  "issues": [{
                    "filename": "App.java",
                    "original_snippet": "bad()",
                    "fixed_snippet": "good()",
                    "issue_description": "Unsafe command execution",
                    "severity": "CRITICAL",
                    "category": "SECURITY"
                  }]
                }
                ```
                """;
        LlmReviewClient client = new LlmReviewClient(fakeAiClient(response), new ObjectMapper());

        List<FixProposal> issues = client.review("system", "user");

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).category()).isEqualTo("SECURITY");
    }

    @Test
    void review_returnsEmptyListWhenJsonCannotBeParsed() {
        LlmReviewClient client = new LlmReviewClient(fakeAiClient("not json"), new ObjectMapper());

        assertThat(client.review("system", "user")).isEmpty();
    }

    private AiClient fakeAiClient(String response) {
        return (systemPrompt, userMessage) -> response;
    }
}
