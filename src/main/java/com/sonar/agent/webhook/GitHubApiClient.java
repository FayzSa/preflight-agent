package com.sonar.agent.webhook;

import com.sonar.agent.webhook.model.ReviewRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Profile("webhook")
public class GitHubApiClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubApiClient.class);
    private static final String GITHUB_API = "https://api.github.com";

    private final RestClient restClient;

    public GitHubApiClient(WebhookProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(GITHUB_API)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("Authorization", "Bearer " + properties.githubToken())
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    public String fetchPullRequestDiff(String owner, String repo, int prNumber) {
        log.debug("Fetching diff for {}/{} PR#{}", owner, repo, prNumber);
        return restClient.get()
                .uri("/repos/{owner}/{repo}/pulls/{number}", owner, repo, prNumber)
                .header("Accept", "application/vnd.github.diff")
                .retrieve()
                .body(String.class);
    }

    public void postReview(String owner, String repo, int prNumber, ReviewRequest review) {
        log.debug("Posting {} review on {}/{} PR#{}", review.event(), owner, repo, prNumber);
        restClient.post()
                .uri("/repos/{owner}/{repo}/pulls/{number}/reviews", owner, repo, prNumber)
                .body(review)
                .retrieve()
                .toBodilessEntity();
    }
}
