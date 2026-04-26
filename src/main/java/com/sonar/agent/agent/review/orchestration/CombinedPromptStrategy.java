package com.sonar.agent.agent.review.orchestration;

import com.sonar.agent.agent.models.DiffResult;
import com.sonar.agent.agent.models.FixProposal;
import com.sonar.agent.agent.review.dimension.ReviewDimension;
import com.sonar.agent.agent.review.prompt.PromptBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CombinedPromptStrategy implements ReviewStrategy {

    private final List<ReviewDimension> dimensions;
    private final PromptBuilder promptBuilder;
    private final LlmReviewClient llmReviewClient;

    public CombinedPromptStrategy(List<ReviewDimension> dimensions,
                                  PromptBuilder promptBuilder,
                                  LlmReviewClient llmReviewClient) {
        this.dimensions = dimensions;
        this.promptBuilder = promptBuilder;
        this.llmReviewClient = llmReviewClient;
    }

    @Override
    public List<FixProposal> review(DiffResult diff, String language) {
        List<ReviewDimension> selectedDimensions = dimensions.stream()
                .filter(dimension -> dimension.shouldRun(diff))
                .toList();
        if (selectedDimensions.isEmpty()) {
            return List.of();
        }

        String systemPrompt = promptBuilder.buildSystemPrompt(selectedDimensions, language);
        String userMessage = promptBuilder.buildUserMessage(diff);
        return llmReviewClient.review(systemPrompt, userMessage);
    }
}
