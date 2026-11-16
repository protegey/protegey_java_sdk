package com.protegey.sdk;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class KycModule {
    private final Client http;

    KycModule(Client http) {
        this.http = http;
    }

    /**
     * Starts an identity verification session for one of your end users — no manual API call
     * needed. Returns a map with {@code sessionId} and {@code url} (the hosted verification link).
     */
    public Map<String, Object> startSession(String externalUserId) {
        return startSession(externalUserId, null, null);
    }

    /**
     * Same as {@link #startSession(String)}, with optional {@code email}/{@code phone} — never
     * Protegey-verified, trusted as given, same model as any other self-reported field. Feeds
     * identity-resolution's cross-partner matching (MEDIUM-tier). Pass {@code null} for either
     * one to omit it.
     */
    public Map<String, Object> startSession(String externalUserId, String email, String phone) {
        Map<String, Object> body = new HashMap<>();
        body.put("externalUserId", externalUserId);
        if (email != null) body.put("email", email);
        if (phone != null) body.put("phone", phone);
        return http.post("/partner-api/kyc/sessions", body);
    }

    /**
     * Polling fallback for the webhook — call this if you're not sure a webhook delivery ever
     * arrived (best-effort: one retry, no queue). {@code sessionId} is the value returned by
     * {@link #startSession}.
     */
    public Map<String, Object> getSession(String sessionId) {
        String encoded = URLEncoder.encode(sessionId, StandardCharsets.UTF_8).replace("+", "%20");
        return http.get("/partner-api/kyc/sessions/" + encoded);
    }
}
