package com.protegey.sdk;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Monitoring alerts — {@code /partner-api/alerts}.
 *
 * <p>{@code status}: {@code OPEN}, {@code CONFIRMED}, {@code MORE_INFO_REQUESTED},
 * {@code DISMISSED}. {@code disposition}: {@code CONFIRMED_FRAUD}, {@code FALSE_POSITIVE},
 * {@code NO_ACTION}, {@code SAR_FILED}, {@code ESCALATED} or null. {@code decisionVerdict}:
 * {@code BLOCK}, {@code STEP_UP}, {@code ESCALATE}, {@code ALERT} or null. {@code ruleSeverity}
 * stays lowercase ({@code review}|{@code block}|{@code step_up}|{@code escalate}).
 *
 * <p>List/get are sandbox-scoped; update, convert-to-case and flag-transaction act on any alert or
 * transaction of your account by id, whichever key you use.
 */
public class AlertsModule {
    private static final String BASE = "/partner-api/alerts";

    private final Client http;

    AlertsModule(Client http) {
        this.http = http;
    }

    /**
     * Lists alerts, newest first. Optional {@code query} keys: {@code page}, {@code limit},
     * {@code status}, {@code disposition}, {@code alertNumber}, {@code ruleCode},
     * {@code externalCustomerId}, {@code transactionId}, {@code dateFrom}, {@code dateTo}. The
     * backend only accepts lowercase {@code status}/{@code disposition} filters; this method
     * lowercases them for you, so {@code "OPEN"} works.
     */
    public Map<String, Object> list(Map<String, Object> query) {
        Map<String, Object> q = query != null ? new HashMap<>(query) : new HashMap<>();
        lowercase(q, "status");
        lowercase(q, "disposition");
        return http.get(BASE, q);
    }

    /** One alert with rule context ({@code ruleName}, {@code ruleSeverity}, ...) and a {@code transaction} snapshot. */
    public Map<String, Object> get(String alertId) {
        return http.get(BASE + "/" + Client.segment(alertId));
    }

    /**
     * Updates an alert. Keys (all optional, {@code null} clears): {@code status},
     * {@code disposition}, {@code assignedToUserId}, {@code investigationNotes}, {@code dueAt}
     * (ISO 8601). Always fires {@code alert.status_changed}.
     */
    public Map<String, Object> update(String alertId, Map<String, Object> changes) {
        return http.patch(BASE + "/" + Client.segment(alertId), changes);
    }

    /**
     * Opens a case from this alert (disposition becomes {@code ESCALATED}). Returns
     * {@code {alert, case}}.
     */
    public Map<String, Object> convertToCase(String alertId) {
        return http.post(BASE + "/" + Client.segment(alertId) + "/convert-to-case", null);
    }

    /**
     * Manually flags a transaction (rule {@code MANUAL-FLAG-001}). {@code transactionId} must be
     * the Protegey transaction uuid, not your external id. Idempotent: returns the existing open
     * alert if there is one.
     */
    public Map<String, Object> flagTransaction(String transactionId) {
        Map<String, Object> body = new HashMap<>();
        body.put("transactionId", transactionId);
        return http.post(BASE + "/flag-transaction", body);
    }

    private static void lowercase(Map<String, Object> q, String key) {
        Object value = q.get(key);
        if (value instanceof String) q.put(key, ((String) value).toLowerCase(Locale.ROOT));
    }
}
