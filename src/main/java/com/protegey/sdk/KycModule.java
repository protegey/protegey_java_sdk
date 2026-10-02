package com.protegey.sdk;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
        return http.post("/partner-api/kyc/sessions", Map.of("externalUserId", externalUserId));
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
