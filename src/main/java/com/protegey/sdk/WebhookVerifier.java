package com.protegey.sdk;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.MessageDigest;

/**
 * Verifies the HMAC-SHA256 signature Protegey attaches to every outbound webhook delivery, so you
 * can trust that a request claiming to be from Protegey actually is.
 *
 * <p>Protegey signs {@code "{timestamp}.{rawBody}"} with your webhook secret (HMAC-SHA256, hex
 * digest) and sends it as the {@code X-Signature} header, alongside the same {@code timestamp} as
 * {@code X-Timestamp}. Pass the <em>raw</em> request body — not a re-encoded/re-serialized version
 * of it, which can produce a different byte sequence and always fail to match.
 */
public final class WebhookVerifier {
    private WebhookVerifier() {
    }

    public static boolean verify(String payload, String timestamp, String signature, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] rawHmac = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
            String expected = toHex(rawHmac);

            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
