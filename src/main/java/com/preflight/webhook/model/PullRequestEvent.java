package com.preflight.webhook.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PullRequestEvent(
        String action,
        @JsonProperty("pull_request") PullRequest pullRequest,
        Repository repository
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PullRequest(
            int number,
            String title,
            @JsonProperty("html_url") String htmlUrl,
            @JsonProperty("diff_url") String diffUrl
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Repository(
            String name,
            @JsonProperty("full_name") String fullName,
            Owner owner
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Owner(String login) {}
    }

    public boolean isReviewable() {
        return "opened".equals(action)
                || "synchronize".equals(action)
                || "reopened".equals(action);
    }
}
