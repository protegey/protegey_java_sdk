package com.protegey.sdk;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Verifies the HMAC-SHA256 signature Protegey attaches to every outbound webhook delivery, so you
 * can trust that a request claiming to be from Protegey actually is.
 *
 * <p>Protegey signs {@code "{timestamp}.{rawBody}"} with your webhook secret (HMAC-SHA256, hex
 * digest) and sends it as the {@code X-Signature} header, alongside the same {@code timestamp}
 * (unix seconds) as {@code X-Timestamp}. Pass the <em>raw</em> request body — not a
 * re-encoded/re-serialized version of it, which can produce a different byte sequence and always
 * fail to match.
 *
 * <p>Event {@code type} values: {@link #KYC_STATUS_CHANGED}, {@link #KYB_STATUS_CHANGED},
 * {@link #ALERT_CREATED}, {@link #ALERT_STATUS_CHANGED}, {@link #TRANSACTION_BLOCKED},
 * {@link #WEBHOOK_TEST}. Deliveries are retried (up to 5 attempts over ~3h) — deduplicate on
 * {@code eventId}, and ignore types you don't know.
 *
 * <p>{@code kyc.status_changed} = {@code {type, eventId, externalUserId, status, recommendation,
 * sessionId, decision}} — fired on an automatic result and on a manual status change (dashboard or
 * {@link KycModule#updateSession}); {@code sessionId} is the id {@code startSession()} returned.
 * {@code kyb.status_changed} = {@code {type, eventId, clientId, status, submissionStatus,
 * decision}}. Every status-like value ({@code status}, {@code submissionStatus},
 * {@code decision}, {@code decision.manualReview.previousStatus/newStatus},
 * {@code recommendation}) is UPPER_SNAKE_CASE.
 */
public final class WebhookVerifier {
    public static final String SIGNATURE_HEADER = "X-Signature";
    public static final String TIMESTAMP_HEADER = "X-Timestamp";

    public static final String KYC_STATUS_CHANGED = "kyc.status_changed";
    public static final String KYB_STATUS_CHANGED = "kyb.status_changed";
    public static final String ALERT_CREATED = "alert.created";
    public static final String ALERT_STATUS_CHANGED = "alert.status_changed";
    public static final String TRANSACTION_BLOCKED = "transaction.blocked";
    public static final String WEBHOOK_TEST = "webhook.test";

    private WebhookVerifier() {
    }

    /** Constant-time check of {@code signature} against {@code HMAC_SHA256(secret, timestamp + "." + payload)}. */
    public static boolean verify(String payload, String timestamp, String signature, String secret) {
        if (payload == null || timestamp == null || signature == null || secret == null || secret.isEmpty()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] rawHmac = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
            String expected = toHex(rawHmac);

            return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Same as {@link #verify(String, String, String, String)}, and additionally rejects deliveries
     * whose {@code X-Timestamp} is more than {@code toleranceSeconds} away from now (replay
     * protection). Keep the tolerance generous enough for clock skew.
     */
    public static boolean verify(String payload, String timestamp, String signature, String secret, long toleranceSeconds) {
        if (!verify(payload, timestamp, signature, secret)) return false;
        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        return Math.abs(System.currentTimeMillis() / 1000L - ts) <= toleranceSeconds;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
