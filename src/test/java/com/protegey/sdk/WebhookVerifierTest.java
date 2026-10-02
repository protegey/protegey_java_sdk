package com.protegey.sdk;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookVerifierTest {

    private String sign(String timestamp, String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : raw) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    @Test
    void acceptsACorrectlySignedPayload() throws Exception {
        String secret = "whsec_test123";
        String timestamp = "1700000000";
        String payload = "{\"event\":\"kyc.session.updated\"}";
        String signature = sign(timestamp, payload, secret);

        assertTrue(WebhookVerifier.verify(payload, timestamp, signature, secret));
    }

    @Test
    void rejectsATamperedPayload() throws Exception {
        String secret = "whsec_test123";
        String timestamp = "1700000000";
        String signature = sign(timestamp, "{\"event\":\"original\"}", secret);

        assertFalse(WebhookVerifier.verify("{\"event\":\"tampered\"}", timestamp, signature, secret));
    }

    @Test
    void rejectsAWrongSecret() throws Exception {
        String timestamp = "1700000000";
        String payload = "{\"event\":\"kyc.session.updated\"}";
        String signature = sign(timestamp, payload, "whsec_correct");

        assertFalse(WebhookVerifier.verify(payload, timestamp, signature, "whsec_wrong"));
    }
}
