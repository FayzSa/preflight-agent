package com.sonar.agent.agent.review.orchestration;

import com.sonar.agent.agent.models.DiffResult;
import com.sonar.agent.agent.models.FixProposal;
import com.sonar.agent.agent.review.dimension.ReviewDimension;
import com.sonar.agent.agent.review.prompt.PromptBuilder;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class PerDimensionStrategy implements ReviewStrategy {

    private final List<ReviewDimension> dimensions;
    private final PromptBuilder promptBuilder;
    private final LlmReviewClient llmReviewClient;

    public PerDimensionStrategy(List<ReviewDimension> dimensions,
                                PromptBuilder promptBuilder,
                                LlmReviewClient llmReviewClient) {
        this.dimensions = dimensions;
        this.promptBuilder = promptBuilder;
        this.llmReviewClient = llmReviewClient;
    }

    @Override
    public List<FixProposal> review(DiffResult diff, String language) {
        List<CompletableFuture<List<FixProposal>>> calls = dimensions.stream()
            .filter(dimension -> dimension.shouldRun(diff))
            .map(dimension -> CompletableFuture.supplyAsync(() -> reviewDimension(diff, language, dimension)))
            .toList();

        return calls.stream()
            .map(CompletableFuture::join)
            .flatMap(List::stream)
            .sorted(Comparator.comparing(FixProposal::filename, Comparator.nullsLast(String::compareTo)))
            .toList();
    }

    private List<FixProposal> reviewDimension(DiffResult diff, String language, ReviewDimension dimension) {
        String systemPrompt = promptBuilder.buildSystemPrompt(List.of(dimension), language);
        String userMessage = promptBuilder.buildUserMessage(diff);
        return llmReviewClient.review(systemPrompt, userMessage);
    }
}
