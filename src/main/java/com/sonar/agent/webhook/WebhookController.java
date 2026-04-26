package com.sonar.agent.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonar.agent.webhook.model.PullRequestEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@RestController
@Profile("webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final PrReviewService reviewService;
    private final WebhookProperties properties;
    private final ObjectMapper objectMapper;

    public WebhookController(PrReviewService reviewService,
                              WebhookProperties properties,
                              ObjectMapper objectMapper) {
        this.reviewService = reviewService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestHeader(value = "X-GitHub-Event", defaultValue = "") String event,
            @RequestHeader(value = "X-Hub-Signature-256", defaultValue = "") String signature,
            @RequestBody String rawBody
    ) {
        if (!isValidSignature(rawBody, signature)) {
            log.warn("Webhook signature verification failed");
            return ResponseEntity.status(401).body("Invalid signature");
        }

        if (!"pull_request".equals(event)) {
            return ResponseEntity.ok("Ignored: " + event);
        }

        try {
            PullRequestEvent prEvent = objectMapper.readValue(rawBody, PullRequestEvent.class);

            if (!prEvent.isReviewable()) {
                return ResponseEntity.ok("Ignored: action=" + prEvent.action());
            }

            // Process asynchronously on a virtual thread so we return before the LLM call
            Thread.ofVirtual()
                    .name("webhook-pr-" + prEvent.pullRequest().number())
                    .start(() -> reviewService.review(prEvent));

            return ResponseEntity.accepted().body("Review queued for PR#" + prEvent.pullRequest().number());

        } catch (Exception e) {
            log.error("Failed to parse webhook payload: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid payload");
        }
    }

    // ── Signature verification ────────────────────────────────────────────────

    boolean isValidSignature(String payload, String signature) {
        if (properties.secret() == null || properties.secret().isBlank()) {
            log.warn("Webhook secret not configured — skipping signature verification");
            return true;
        }
        if (signature == null || !signature.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = "sha256=" + HexFormat.of().formatHex(hash);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Signature verification error: {}", e.getMessage());
            return false;
        }
    }
}
