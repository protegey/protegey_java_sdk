package com.protegey.sdk;

import java.util.HashMap;
import java.util.Map;

/**
 * 3D liveness and re-verification sessions — {@code /partner-api/liveness/sessions}. A separate,
 * paid product, billed per check where liveness was proven; KYC is unaffected. Sandbox-scoped like
 * KYC: a sandbox key only sees sandbox sessions.
 *
 * <p>Two modes: {@code LIVENESS} (a selfie-only 3D liveness check) and {@code REVERIFICATION}
 * (liveness, then a face match against the face of that customer's latest KYC verification).
 *
 * <p>Status values on the wire are UPPERCASE: {@code PENDING}, {@code COMPLETED},
 * {@code EXPIRED}. {@code livenessProven} is a boolean or null (no result yet); {@code matchLevel}
 * is an int or null — higher means a stronger match; 0 means a different person; {@code matched} is a boolean
 * or null and only set on a re-verification. {@code amount} is a decimal string in {@code currency}
 * ({@code USD}), the sum of the billable {@code checks}.
 */
public class LivenessModule {
    /** A selfie-only 3D liveness check. */
    public static final String MODE_LIVENESS = "LIVENESS";
    /** Liveness, then a face match against the customer's latest KYC face. */
    public static final String MODE_REVERIFICATION = "REVERIFICATION";
    /**
     * {@link ProtegeyApiException#getErrorCode()} on a 400 from {@link #startReverification}: no
     * 3D record (KYC face) exists for this customer yet — run a KYC session first.
     */
    public static final String NO_3D_ENROLLMENT = ProtegeyApiException.NO_3D_ENROLLMENT;

    private static final String BASE = "/partner-api/liveness/sessions";

    private final Client http;

    LivenessModule(Client http) {
        this.http = http;
    }

    /**
     * Starts a 3D liveness session ({@code mode: LIVENESS}). {@code externalUserId} is optional —
     * pass null to omit it. Returns {@code {sessionId, mode, status, externalUserId, url,
     * captureUrl, expiresInMinutes}} — {@code url} is the hosted capture page to send to (or open
     * for) your end user; its token is single-use and valid for 30 minutes.
     */
    public Map<String, Object> startSession(String externalUserId) {
        return create(MODE_LIVENESS, externalUserId, null);
    }

    /**
     * Starts a re-verification session ({@code mode: REVERIFICATION}) for one of your customers:
     * liveness, then a face match against the face of their latest KYC verification. Same
     * response as {@link #startSession}. Throws a 400 {@link ProtegeyApiException} with
     * {@code errorCode} {@link #NO_3D_ENROLLMENT} if that customer has no KYC face yet.
     */
    public Map<String, Object> startReverification(String externalUserId) {
        return startReverification(externalUserId, null);
    }

    /**
     * Same as {@link #startReverification(String)}, matching against a specific KYC verification
     * ({@code referenceVerificationId}) instead of the latest one. Pass null to use the latest.
     */
    public Map<String, Object> startReverification(String externalUserId, String referenceVerificationId) {
        return create(MODE_REVERIFICATION, externalUserId, referenceVerificationId);
    }

    /**
     * Lists sessions, newest first. Returns {@code {data, total, page, limit, totalPages}}; rows
     * are sessions without {@code checks}.
     *
     * <p>Optional {@code query} keys: {@code page}, {@code limit} (1–100), {@code externalUserId}
     * (exact match on your own customer id), {@code mode} ({@code LIVENESS} |
     * {@code REVERIFICATION}).
     */
    public Map<String, Object> listSessions(Map<String, Object> query) {
        return http.get(BASE, query);
    }

    /**
     * Full session detail: {@code id, sessionId, mode, status, externalUserId,
     * referenceEnrollmentId, livenessProven, matchLevel, matched, duplicateFaceEnrollmentIds}
     * (not currently populated), {@code attempts, isSandbox, completedAt, createdAt,
     * billableChecks, amount, currency} and {@code checks} — one per capture attempt:
     * {@code {id, livenessProven, matchLevel, billable, unitPrice, currency, createdAt}}. Poll it
     * until {@code status} is {@code COMPLETED} or {@code EXPIRED}.
     */
    public Map<String, Object> getSession(String sessionId) {
        return http.get(BASE + "/" + Client.segment(sessionId));
    }

    private Map<String, Object> create(String mode, String externalUserId, String referenceVerificationId) {
        Map<String, Object> body = new HashMap<>();
        body.put("mode", mode);
        if (externalUserId != null) body.put("externalUserId", externalUserId);
        if (referenceVerificationId != null) body.put("referenceVerificationId", referenceVerificationId);
        return http.post(BASE, body);
    }
}
