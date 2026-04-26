package com.preflight.webhook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("webhook")
public class WebhookProperties {

    @Value("${ai-fix.webhook.secret:}")
    private String secret;

    @Value("${ai-fix.webhook.github-token:}")
    private String githubToken;

    public String secret()      { return secret; }
    public String githubToken() { return githubToken; }
}
