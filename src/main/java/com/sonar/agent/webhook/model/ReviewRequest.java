package com.sonar.agent.webhook.model;

public record ReviewRequest(String body, String event) {
    public static ReviewRequest comment(String body)         { return new ReviewRequest(body, "COMMENT"); }
    public static ReviewRequest requestChanges(String body)  { return new ReviewRequest(body, "REQUEST_CHANGES"); }
}
