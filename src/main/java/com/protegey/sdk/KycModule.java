package com.protegey.sdk;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Identity verification (KYC) sessions — {@code /partner-api/kyc/sessions}. Sandbox-scoped: a
 * sandbox key only sees sandbox sessions (quota: 10 new sandbox sessions per UTC day).
 *
 * <p>Status values on the wire are UPPER_SNAKE_CASE: {@code NOT_STARTED}, {@code IN_PROGRESS},
 * {@code AWAITING_USER}, {@code IN_REVIEW}, {@code APPROVED}, {@code DECLINED},
 * {@code RESUBMITTED}, {@code ABANDONED}, {@code EXPIRED}, {@code KYC_EXPIRED}. {@code outcome} is
 * {@code SUCCESS} | {@code MANUAL} | {@code FAILED} | null.
 */
public class KycModule {
    private static final String BASE = "/partner-api/kyc/sessions";

    private final Client http;

    KycModule(Client http) {
        this.http = http;
    }

    /**
     * Starts an identity verification session for one of your end users. Returns
     * {@code {sessionId, url}} — {@code url} is the hosted capture page; its token is single-use
     * and valid for 30 minutes.
     */
    public Map<String, Object> startSession(String externalUserId) {
        return startSession(externalUserId, null, null);
    }

    /**
     * Same as {@link #startSession(String)}, with optional {@code email}/{@code phone} — never
     * Protegey-verified, trusted as given. Feeds identity-resolution's cross-partner matching.
     * Pass {@code null} for either one to omit it.
     */
    public Map<String, Object> startSession(String externalUserId, String email, String phone) {
        Map<String, Object> body = new HashMap<>();
        body.put("externalUserId", externalUserId);
        if (email != null) body.put("email", email);
        if (phone != null) body.put("phone", phone);
        return http.post(BASE, body);
    }

    /**
     * Lists sessions, newest first. Returns {@code {data, total, page, limit, totalPages}}.
     *
     * <p>Optional {@code query} keys: {@code page}, {@code limit} (1–100), {@code search} (full
     * name or externalUserId), {@code status}. The backend's {@code status} filter only accepts
     * Title Case ({@code "In Review"}); this method converts the UPPER_SNAKE_CASE values you get
     * in responses ({@code "IN_REVIEW"}) for you.
     */
    public Map<String, Object> listSessions(Map<String, Object> query) {
        Map<String, Object> q = query != null ? new HashMap<>(query) : new HashMap<>();
        Object status = q.get("status");
        if (status instanceof String) {
            q.put("status", toTitleCaseStatus((String) status));
        }
        return http.get(BASE, q);
    }

    /**
     * Full session detail: {@code status}, {@code outcome}, {@code decision} (null until a result
     * exists), extracted identity fields, scores and base64 photos ({@code documentPhoto},
     * {@code documentBackPhoto}, {@code selfiePhoto}, {@code documentFaceCrop} — may arrive a
     * couple of minutes after the decision). Use it as a polling fallback for the
     * {@code kyc.status_changed} webhook (which carries {@code externalUserId}, not this id).
     */
    public Map<String, Object> getSession(String sessionId) {
        return http.get(BASE + "/" + Client.segment(sessionId));
    }

    /**
     * Corrects extracted fields and/or overrides the status. Keys (all optional):
     * {@code fullName}, {@code externalUserId}, {@code country} (ISO alpha-2),
     * {@code documentType}, {@code idDocumentNumber}, {@code sex}, {@code dateOfBirth},
     * {@code placeOfBirth}, {@code profession}, {@code dateOfIssue}, {@code dateOfExpiration},
     * {@code status} ({@code APPROVED} | {@code DECLINED} | {@code IN_REVIEW}),
     * {@code reviewNote}. Returns {@code {sessionId, externalUserId, status, outcome, decision}}.
     * No webhook fires for a manual override.
     */
    public Map<String, Object> updateSession(String sessionId, Map<String, Object> changes) {
        return http.patch(BASE + "/" + Client.segment(sessionId), changes);
    }

    /**
     * Issues a fresh 30-minute capture link and sets the status to {@code RESUBMITTED}. Returns
     * {@code {sessionId, url}}. Does not count against the sandbox quota.
     */
    public Map<String, Object> resendSession(String sessionId) {
        return http.post(BASE + "/" + Client.segment(sessionId) + "/resend", null);
    }

    static String toTitleCaseStatus(String status) {
        if (!status.equals(status.toUpperCase(Locale.ROOT))) {
            return status; // already Title Case (or something we leave to the server to judge)
        }
        StringBuilder sb = new StringBuilder();
        for (String word : status.split("[_ ]+")) {
            if (word.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(word.charAt(0)).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }
}
