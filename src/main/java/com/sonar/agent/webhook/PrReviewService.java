package com.sonar.agent.webhook;

import com.sonar.agent.agent.AnalyzerGraph;
import com.sonar.agent.agent.models.AnalysisResponse;
import com.sonar.agent.agent.models.DiffResult;
import com.sonar.agent.agent.models.FixProposal;
import com.sonar.agent.tools.GitOperationsTool;
import com.sonar.agent.webhook.model.PullRequestEvent;
import com.sonar.agent.webhook.model.ReviewRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Profile("webhook")
public class PrReviewService {

    private static final Logger log = LoggerFactory.getLogger(PrReviewService.class);

    private final AnalyzerGraph analyzerGraph;
    private final GitOperationsTool gitTool;
    private final GitHubApiClient gitHubApiClient;

    public PrReviewService(AnalyzerGraph analyzerGraph,
                           GitOperationsTool gitTool,
                           GitHubApiClient gitHubApiClient) {
        this.analyzerGraph = analyzerGraph;
        this.gitTool = gitTool;
        this.gitHubApiClient = gitHubApiClient;
    }

    public void review(PullRequestEvent event) {
        String owner  = event.repository().owner().login();
        String repo   = event.repository().name();
        int prNumber  = event.pullRequest().number();

        log.info("Reviewing PR#{} in {}/{}", prNumber, owner, repo);

        try {
            String rawDiff = gitHubApiClient.fetchPullRequestDiff(owner, repo, prNumber);

            if (rawDiff == null || rawDiff.isBlank()) {
                log.info("PR#{} has no diff — skipping review", prNumber);
                return;
            }

            DiffResult diff = gitTool.buildFromRawDiff(rawDiff);
            AnalysisResponse response = analyzerGraph.analyze(diff);

            ReviewRequest review = response.hasIssues()
                    ? ReviewRequest.requestChanges(buildIssueBody(response.issues(), event))
                    : ReviewRequest.comment(buildCleanBody(event));

            gitHubApiClient.postReview(owner, repo, prNumber, review);
            log.info("Review posted on PR#{}: {} issue(s) found", prNumber, response.issues().size());

        } catch (Exception e) {
            log.error("Failed to review PR#{} in {}/{}: {}", prNumber, owner, repo, e.getMessage(), e);
        }
    }

    // ── Review body builders ──────────────────────────────────────────────────

    String buildIssueBody(List<FixProposal> issues, PullRequestEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("## AI Code Review — ").append(issues.size()).append(" Critical Issue(s) Found\n\n");

        sb.append("| # | Severity | Category | File |\n");
        sb.append("|---|----------|----------|------|\n");
        for (int i = 0; i < issues.size(); i++) {
            FixProposal p = issues.get(i);
            sb.append("| ").append(i + 1)
              .append(" | **").append(p.severity()).append("**")
              .append(" | ").append(p.category())
              .append(" | `").append(p.filename()).append("` |\n");
        }

        sb.append("\n---\n");

        for (int i = 0; i < issues.size(); i++) {
            FixProposal p = issues.get(i);
            sb.append("\n### [").append(i + 1).append("] ")
              .append(p.severity()).append(" — ").append(p.category()).append("\n")
              .append("**File:** `").append(p.filename()).append("`\n\n")
              .append("**Issue:** ").append(p.issueDescription()).append("\n\n")
              .append("**Before:**\n```\n").append(p.originalSnippet()).append("\n```\n\n")
              .append("**After:**\n```\n").append(p.fixedSnippet()).append("\n```\n\n")
              .append("---\n");
        }

        sb.append("\n*Scanned by [ai-fix](https://github.com/fayzsa/sonar_agent) · Gemini 2.5 Flash*");
        return sb.toString();
    }

    String buildCleanBody(PullRequestEvent event) {
        return "## AI Code Review — No Critical Issues Found\n\n"
                + "No critical security vulnerabilities or bugs were detected in this PR.\n\n"
                + "*Scanned by [ai-fix](https://github.com/fayzsa/sonar_agent) · Gemini 2.5 Flash*";
    }
}
