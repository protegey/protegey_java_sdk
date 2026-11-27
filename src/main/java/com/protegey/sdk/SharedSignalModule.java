package com.protegey.sdk;

import java.util.Map;

/**
 * Shared fraud signal network — {@code /partner-api/shared-signal/check}. Requires the network to
 * be enabled for your account (and kept on for 7 consecutive days) in Organization Settings.
 * Reporting and retracting signals are portal-only.
 */
public class SharedSignalModule {
    private final Client http;

    SharedSignalModule(Client http) {
        this.http = http;
    }

    /**
     * Checks identifiers against signals reported by other institutions. Provide at least one of
     * {@code phoneNumber}, {@code email}, {@code deviceFingerprint} (a device {@code visitorId}),
     * {@code governmentId}, {@code personIdentityId}. Optional {@code externalCustomerId}: on a
     * match, your own risk profile for that customer is bumped.
     *
     * <p>Returns {@code {flagged, category, reportedDaysAgo, flagConfidence (0–100|null),
     * matchCount, groupMatchCount, matches[{category, reportedDaysAgo, groupEntity}]}};
     * {@code category} is {@code CONFIRMED_FRAUD} | {@code IDENTITY_THEFT} |
     * {@code MONEY_LAUNDERING} | {@code OTHER} (null when not flagged). The reporting institution
     * is never disclosed, except a sister entity of your own partner group: {@code groupEntity} is
     * then its name (null for the public network, which stays anonymous) and
     * {@code groupMatchCount} counts those reports (0 when you are in no group). Both are optional
     * (absent on older API versions).
     */
    public Map<String, Object> check(Map<String, Object> identifiers) {
        return http.post("/partner-api/shared-signal/check", identifiers);
    }
}
