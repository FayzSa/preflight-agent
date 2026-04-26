package com.sonar.agent.agent.review.orchestration;

import com.sonar.agent.agent.models.DiffResult;
import com.sonar.agent.agent.models.FixProposal;
import com.sonar.agent.agent.review.dimension.ReviewDimension;
import com.sonar.agent.agent.review.prompt.PromptBuilder;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@Primary
public class HybridStrategy implements ReviewStrategy {

    private final List<ReviewDimension> dimensions;
    private final PromptBuilder promptBuilder;
    private final LlmReviewClient llmReviewClient;

    public HybridStrategy(List<ReviewDimension> dimensions,
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

        List<CompletableFuture<List<FixProposal>>> calls = selectedDimensions.stream()
                .map(dimension -> CompletableFuture.supplyAsync(() -> reviewDimension(diff, language, dimension)))
                .toList();

        List<FixProposal> merged = calls.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .filter(this::hasRequiredSeverity)
                .sorted(Comparator.comparing(FixProposal::filename, Comparator.nullsLast(String::compareTo)))
                .toList();

        return deduplicate(merged);
    }

    private List<FixProposal> reviewDimension(DiffResult diff, String language, ReviewDimension dimension) {
        String systemPrompt = promptBuilder.buildSystemPrompt(List.of(dimension), language);
        String userMessage = promptBuilder.buildUserMessage(diff);
        return llmReviewClient.review(systemPrompt, userMessage);
    }

    private boolean hasRequiredSeverity(FixProposal proposal) {
        return "CRITICAL".equals(proposal.severity()) || "HIGH".equals(proposal.severity());
    }

    private List<FixProposal> deduplicate(List<FixProposal> proposals) {
        Map<String, FixProposal> unique = new LinkedHashMap<>();
        for (FixProposal proposal : proposals) {
            String key = String.join("\u0001",
                    valueOrEmpty(proposal.filename()),
                    valueOrEmpty(proposal.originalSnippet()),
                    valueOrEmpty(proposal.category()));
            unique.putIfAbsent(key, proposal);
        }
        return List.copyOf(unique.values());
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
