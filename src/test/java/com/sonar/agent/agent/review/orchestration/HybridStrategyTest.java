package com.sonar.agent.agent.review.orchestration;

import com.sonar.agent.agent.models.DiffResult;
import com.sonar.agent.agent.models.FixProposal;
import com.sonar.agent.agent.review.dimension.PerformanceDimension;
import com.sonar.agent.agent.review.dimension.SecurityDimension;
import com.sonar.agent.agent.review.prompt.PromptBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HybridStrategyTest {

    @Mock LlmReviewClient llmReviewClient;

    @Test
    void review_runsSelectedDimensionsAndDeduplicatesHighSeverityResults() {
        HybridStrategy strategy = new HybridStrategy(
                List.of(new SecurityDimension(), new PerformanceDimension()),
                new PromptBuilder(),
                llmReviewClient
        );
        DiffResult diff = new DiffResult(
                "+String query = \"select * from users where id = \" + id;",
                List.of(new DiffResult.FileDiff("UserService.java", "+String query = ..."))
        );
        FixProposal issue = new FixProposal(
                "UserService.java",
                "String query = \"select * from users where id = \" + id;",
                "String query = \"select * from users where id = ?\";",
                "SQL injection vulnerability",
                "CRITICAL",
                "SECURITY"
        );
        FixProposal lowSeverity = new FixProposal(
                "UserService.java",
                "query",
                "query",
                "Minor suggestion",
                "MEDIUM",
                "PERFORMANCE"
        );

        when(llmReviewClient.review(anyString(), anyString()))
                .thenReturn(List.of(issue, issue, lowSeverity));

        List<FixProposal> issues = strategy.review(diff, "Java");

        assertThat(issues).containsExactly(issue);
    }

    @Test
    void review_returnsEmptyListWhenNoDimensionMatches() {
        HybridStrategy strategy = new HybridStrategy(
                List.of(new SecurityDimension(), new PerformanceDimension()),
                new PromptBuilder(),
                llmReviewClient
        );
        DiffResult diff = new DiffResult(
                "+String title = \"Hello\";",
                List.of(new DiffResult.FileDiff("Message.java", "+String title = \"Hello\";"))
        );

        assertThat(strategy.review(diff, "Java")).isEmpty();
    }
}
