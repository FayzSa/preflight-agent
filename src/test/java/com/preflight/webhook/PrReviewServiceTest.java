package com.preflight.webhook;

import com.preflight.agent.AnalyzerGraph;
import com.preflight.agent.models.AnalysisResponse;
import com.preflight.agent.models.DiffResult;
import com.preflight.agent.models.FixProposal;
import com.preflight.tools.GitOperationsTool;
import com.preflight.webhook.model.PullRequestEvent;
import com.preflight.webhook.model.ReviewRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrReviewServiceTest {

    @Mock AnalyzerGraph analyzerGraph;
    @Mock GitOperationsTool gitTool;
    @Mock GitHubApiClient gitHubApiClient;

    PrReviewService service;

    @BeforeEach
    void setUp() {
        service = new PrReviewService(analyzerGraph, gitTool, gitHubApiClient);
    }

    @Test
    void review_postsRequestChanges_whenIssuesFound() {
        PullRequestEvent event = buildEvent("opened", "owner", "repo", 42);
        DiffResult diff = new DiffResult("+int x = 1;",
                List.of(new DiffResult.FileDiff("App.java", "+int x = 1;")));

        FixProposal issue = new FixProposal(
                "App.java", "old code", "new code", "SQL injection", "CRITICAL", "SECURITY");

        when(gitHubApiClient.fetchPullRequestDiff("owner", "repo", 42)).thenReturn("+int x = 1;");
        when(gitTool.buildFromRawDiff(anyString())).thenReturn(diff);
        when(analyzerGraph.analyze(diff)).thenReturn(new AnalysisResponse(List.of(issue)));

        service.review(event);

        ArgumentCaptor<ReviewRequest> captor = ArgumentCaptor.forClass(ReviewRequest.class);
        verify(gitHubApiClient).postReview(eq("owner"), eq("repo"), eq(42), captor.capture());

        ReviewRequest posted = captor.getValue();
        assertThat(posted.event()).isEqualTo("REQUEST_CHANGES");
        assertThat(posted.body()).contains("1 Critical Issue(s) Found");
        assertThat(posted.body()).contains("App.java");
        assertThat(posted.body()).contains("CRITICAL");
        assertThat(posted.body()).contains("SQL injection");
    }

    @Test
    void review_postsComment_whenNoIssues() {
        PullRequestEvent event = buildEvent("synchronize", "owner", "repo", 7);
        DiffResult diff = new DiffResult("+int x = 1;",
                List.of(new DiffResult.FileDiff("App.java", "+int x = 1;")));

        when(gitHubApiClient.fetchPullRequestDiff("owner", "repo", 7)).thenReturn("+int x = 1;");
        when(gitTool.buildFromRawDiff(anyString())).thenReturn(diff);
        when(analyzerGraph.analyze(diff)).thenReturn(new AnalysisResponse(List.of()));

        service.review(event);

        ArgumentCaptor<ReviewRequest> captor = ArgumentCaptor.forClass(ReviewRequest.class);
        verify(gitHubApiClient).postReview(eq("owner"), eq("repo"), eq(7), captor.capture());

        assertThat(captor.getValue().event()).isEqualTo("COMMENT");
        assertThat(captor.getValue().body()).contains("No Critical Issues Found");
    }

    @Test
    void review_skips_whenDiffIsBlank() {
        PullRequestEvent event = buildEvent("opened", "owner", "repo", 3);

        when(gitHubApiClient.fetchPullRequestDiff("owner", "repo", 3)).thenReturn("");

        service.review(event);

        verifyNoInteractions(analyzerGraph);
        verify(gitHubApiClient, never()).postReview(any(), any(), anyInt(), any());
    }

    @Test
    void review_doesNotThrow_whenApiCallFails() {
        PullRequestEvent event = buildEvent("opened", "owner", "repo", 1);
        when(gitHubApiClient.fetchPullRequestDiff(any(), any(), anyInt()))
                .thenThrow(new RuntimeException("network error"));

        service.review(event);

        verify(gitHubApiClient, never()).postReview(any(), any(), anyInt(), any());
    }

    @Test
    void buildIssueBody_includesSummaryTableAndDetails() {
        FixProposal issue = new FixProposal(
                "Svc.java", "bad code", "good code", "XSS via output", "HIGH", "SECURITY");
        PullRequestEvent event = buildEvent("opened", "o", "r", 1);

        String body = service.buildIssueBody(List.of(issue), event);

        assertThat(body).contains("1 Critical Issue(s) Found");
        assertThat(body).contains("| **HIGH**");
        assertThat(body).contains("`Svc.java`");
        assertThat(body).contains("XSS via output");
        assertThat(body).contains("bad code");
        assertThat(body).contains("good code");
        assertThat(body).contains("ai-fix");
    }

    @Test
    void buildCleanBody_containsPassMessage() {
        PullRequestEvent event = buildEvent("opened", "o", "r", 1);
        String body = service.buildCleanBody(event);
        assertThat(body).contains("No Critical Issues Found");
        assertThat(body).contains("ai-fix");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PullRequestEvent buildEvent(String action, String owner, String repo, int number) {
        return new PullRequestEvent(
                action,
                new PullRequestEvent.PullRequest(number, "Test PR",
                        "https://github.com/" + owner + "/" + repo + "/pull/" + number,
                        "https://github.com/" + owner + "/" + repo + "/pull/" + number + ".diff"),
                new PullRequestEvent.Repository(repo, owner + "/" + repo,
                        new PullRequestEvent.Repository.Owner(owner))
        );
    }
}
