package com.sonar.agent;

import com.sonar.agent.agent.AnalyzerGraph;
import com.sonar.agent.agent.models.AnalysisResponse;
import com.sonar.agent.agent.models.DiffResult;
import com.sonar.agent.agent.models.FixProposal;
import com.sonar.agent.agent.review.ReviewOrchestrator;
import com.sonar.agent.tools.FileSystemTool;
import com.sonar.agent.tools.GitOperationsTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyzerGraphTest {

    @Mock GitOperationsTool gitTool;
    @Mock FileSystemTool fileSystemTool;
    @Mock ReviewOrchestrator reviewOrchestrator;

    AnalyzerGraph analyzerGraph;

    @BeforeEach
    void setUp() {
        analyzerGraph = new AnalyzerGraph(gitTool, fileSystemTool, reviewOrchestrator);
    }

    @Test
    void analyze_returnsEmptyResponse_whenNoDiff() {
        when(gitTool.getUncommittedChanges(anyString()))
                .thenReturn(new DiffResult("", List.of()));

        AnalysisResponse response = analyzerGraph.analyze("/some/repo");

        assertThat(response.hasIssues()).isFalse();
        verifyNoInteractions(reviewOrchestrator);
    }

    @Test
    void analyze_delegatesDiffToReviewOrchestrator() {
        var diff = new DiffResult(
                "+String query = \"SELECT * FROM users WHERE id = \" + userId;",
                List.of(new DiffResult.FileDiff(
                        "UserService.java",
                        "+String query = \"SELECT * FROM users WHERE id = \" + userId;"
                ))
        );

        FixProposal issue = new FixProposal(
                "UserService.java",
                "String query = \"SELECT * FROM users WHERE id = \" + userId;",
                "String query = \"SELECT * FROM users WHERE id = ?\";",
                "SQL injection vulnerability via string concatenation",
                "CRITICAL",
                "SECURITY"
        );

        when(gitTool.getUncommittedChanges(anyString())).thenReturn(diff);
        when(reviewOrchestrator.review(diff)).thenReturn(new AnalysisResponse(List.of(issue)));

        AnalysisResponse response = analyzerGraph.analyze("/some/repo");

        assertThat(response.hasIssues()).isTrue();
        assertThat(response.issues()).containsExactly(issue);
        verify(reviewOrchestrator).review(diff);
    }

    @Test
    void analyze_returnsEmptyResponse_whenOrchestratorReturnsNoIssues() {
        var diff = new DiffResult(
                "+int x = 1;",
                List.of(new DiffResult.FileDiff("Main.java", "+int x = 1;"))
        );

        when(gitTool.getUncommittedChanges(anyString())).thenReturn(diff);
        when(reviewOrchestrator.review(diff)).thenReturn(new AnalysisResponse(List.of()));

        AnalysisResponse response = analyzerGraph.analyze("/some/repo");

        assertThat(response.hasIssues()).isFalse();
    }

    @Test
    void applyFixes_delegatesToFileSystemTool() {
        FixProposal fix = new FixProposal(
                "App.java", "old code", "new code",
                "SQL injection", "CRITICAL", "SECURITY"
        );
        AnalysisResponse response = new AnalysisResponse(List.of(fix));

        when(fileSystemTool.applyFix(fix, "/repo")).thenReturn(true);

        List<Boolean> results = analyzerGraph.applyFixes(response, "/repo");

        assertThat(results).containsExactly(true);
        verify(fileSystemTool).applyFix(fix, "/repo");
    }
}
