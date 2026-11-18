package com.protegey.sdk;

import java.util.Map;

/** Behavioral biometrics events — {@code /partner-api/behavioral-events}. Sandbox-scoped. */
public class BehavioralEventsModule {
    private final Client http;

    BehavioralEventsModule(Client http) {
        this.http = http;
    }

    /**
     * Scores one session's behavior against the customer's baseline. Required keys:
     * {@code externalCustomerId}, {@code sessionId}. Optional maps: {@code keystroke}
     * ({@code avgHoldTimeMs}, {@code avgInterKeyLatencyMs}, {@code typingSpeedCharsPerSec},
     * {@code errorRate}), {@code touch} ({@code avgTapPressure}, {@code avgSwipeVelocity},
     * {@code scrollBehaviorScore}, {@code mouseAcceleration}), {@code navigation}
     * ({@code screenSequence}: list of strings), {@code session} ({@code loginHourBucket} 0–23,
     * {@code loginDayOfWeek} 0–6, {@code sessionDurationMs}).
     *
     * <p>Returns {@code {status (LEARNING|SCORED), deviationScore, confidenceTier
     * (LOW|MEDIUM|HIGH|null), stepUpRecommended, escalatedAlertId}}.
     */
    public Map<String, Object> record(Map<String, Object> event) {
        return http.post("/partner-api/behavioral-events", event);
    }
}
