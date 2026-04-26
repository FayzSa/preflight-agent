package com.preflight.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.preflight.webhook.model.PullRequestEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookControllerTest {

    @Mock PrReviewService reviewService;

    WebhookController controller;
    WebhookProperties properties;

    @BeforeEach
    void setUp() {
        properties = new WebhookProperties();
        ReflectionTestUtils.setField(properties, "secret", "test-secret");
        ReflectionTestUtils.setField(properties, "githubToken", "ghp_test");
        controller = new WebhookController(reviewService, properties, new ObjectMapper());
    }

    @Test
    void handleWebhook_returns401_whenSignatureInvalid() {
        ResponseEntity<String> response = controller.handleWebhook(
                "pull_request", "sha256=invalidsig", prPayload("opened", 1));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void handleWebhook_ignoresNonPrEvents() {
        String payload = "{}";
        ResponseEntity<String> response = controller.handleWebhook(
                "push", validSig(payload), payload);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("Ignored");
        verifyNoInteractions(reviewService);
    }

    @Test
    void handleWebhook_ignoresClosedPrAction() {
        String payload = prPayload("closed", 5);
        ResponseEntity<String> response = controller.handleWebhook(
                "pull_request", validSig(payload), payload);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("Ignored");
        verifyNoInteractions(reviewService);
    }

    @Test
    void handleWebhook_queuesReview_forOpenedPr() throws InterruptedException {
        String payload = prPayload("opened", 99);
        ResponseEntity<String> response = controller.handleWebhook(
                "pull_request", validSig(payload), payload);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).contains("99");

        // Give the virtual thread time to call the service
        Thread.sleep(200);
        verify(reviewService, times(1)).review(any(PullRequestEvent.class));
    }

    @Test
    void handleWebhook_queuesReview_forSynchronizePr() throws InterruptedException {
        String payload = prPayload("synchronize", 12);
        ResponseEntity<String> response = controller.handleWebhook(
                "pull_request", validSig(payload), payload);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        Thread.sleep(200);
        verify(reviewService, times(1)).review(any(PullRequestEvent.class));
    }

    @Test
    void isValidSignature_returnsTrue_forCorrectHmac() {
        String payload = "hello world";
        String sig = validSig(payload);
        assertThat(controller.isValidSignature(payload, sig)).isTrue();
    }

    @Test
    void isValidSignature_returnsFalse_forWrongSecret() {
        String payload = "hello world";
        assertThat(controller.isValidSignature(payload, "sha256=wronghex")).isFalse();
    }

    @Test
    void isValidSignature_returnsTrue_whenSecretNotConfigured() {
        ReflectionTestUtils.setField(properties, "secret", "");
        assertThat(controller.isValidSignature("anything", "sha256=whatever")).isTrue();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String prPayload(String action, int number) {
        return """
                {
                  "action": "%s",
                  "pull_request": {
                    "number": %d,
                    "title": "Test PR",
                    "html_url": "https://github.com/o/r/pull/%d",
                    "diff_url": "https://github.com/o/r/pull/%d.diff"
                  },
                  "repository": {
                    "name": "r",
                    "full_name": "o/r",
                    "owner": { "login": "o" }
                  }
                }
                """.formatted(action, number, number, number);
    }

    private String validSig(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
