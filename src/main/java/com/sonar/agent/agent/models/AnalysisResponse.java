package com.sonar.agent.agent.models;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AnalysisResponse(
        @JsonProperty("issues") List<FixProposal> issues
) {
    public boolean hasIssues() {
        return issues != null && !issues.isEmpty();
    }
}
