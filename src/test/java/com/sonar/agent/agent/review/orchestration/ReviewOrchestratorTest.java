package com.sonar.agent.agent.review.orchestration;

import com.sonar.agent.agent.models.AnalysisResponse;
import com.sonar.agent.agent.models.DiffResult;
import com.sonar.agent.agent.models.FixProposal;
import com.sonar.agent.agent.review.ReviewOrchestrator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewOrchestratorTest {

    @Test
    void review_detectsLanguageAndWrapsStrategyResults() {
        FixProposal issue = new FixProposal(
                "App.java", "bad", "good", "Bug", "HIGH", "CRITICAL_BUG");
        ReviewStrategy strategy = (diff, language) -> {
            assertThat(language).isEqualTo("Java");
            return List.of(issue);
        };
        ReviewOrchestrator orchestrator = new ReviewOrchestrator(strategy);
        DiffResult diff = new DiffResult(
                "+bad",
                List.of(new DiffResult.FileDiff("App.java", "+bad"))
        );

        AnalysisResponse response = orchestrator.review(diff);

        assertThat(response.issues()).containsExactly(issue);
    }
}
