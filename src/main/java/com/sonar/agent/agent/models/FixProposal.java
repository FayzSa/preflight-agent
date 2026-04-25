package com.sonar.agent.agent.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FixProposal(
        @JsonProperty("filename") String filename,
        @JsonProperty("original_snippet") String originalSnippet,
        @JsonProperty("fixed_snippet") String fixedSnippet,
        @JsonProperty("issue_description") String issueDescription,
        @JsonProperty("severity") String severity,
        @JsonProperty("category") String category
) {}
