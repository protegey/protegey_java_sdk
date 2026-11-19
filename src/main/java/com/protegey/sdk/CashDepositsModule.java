package com.protegey.sdk;

import java.util.Map;

/**
 * Cash deposits — {@code /partner-api/cash-deposits}. A cash deposit is recorded as a monitored
 * cash CREDIT, so it runs through the same rules (structuring, large cash, ...) and raises the same
 * alerts as a transaction. Sandbox-scoped like transactions.
 *
 * <p>{@code decision}: {@code CLEAR} | {@code REVIEW} | {@code BLOCKED}. {@code channel} comes back
 * UPPERCASE ({@code BRANCH}, {@code ATM}, {@code POS}, {@code ONLINE}, {@code MOBILE_APP},
 * {@code USSD}, {@code AGENT}, {@code API}, {@code CALL_CENTER}) and is accepted in any casing.
 */
public class CashDepositsModule {
    private static final String BASE = "/partner-api/cash-deposits";

    private final Client http;

    CashDepositsModule(Client http) {
        this.http = http;
    }

    /**
     * Records a cash deposit.
     *
     * <p>Required keys: {@code externalCustomerId}, {@code amount} (positive number),
     * {@code currency} (ISO 4217). Optional: {@code externalTransactionId} (your reference —
     * generated when omitted; send it to make retries safe: reusing it throws a 409
     * {@link ProtegeyApiException}), {@code customerName}, {@code depositedAt} (ISO 8601, defaults
     * to the time of the call), {@code channel}, {@code branchId}, {@code agentId},
     * {@code location}, {@code depositorName} (who physically brought the cash),
     * {@code metadata} (object).
     *
     * <p>Returns {@code {cashDepositId, externalTransactionId, decision, riskScore, alerts[]}} —
     * each alert: {@code id, alertNumber, ruleCode, status, decisionVerdict, triggeredAt}.
     */
    public Map<String, Object> record(Map<String, Object> input) {
        return http.post(BASE, input);
    }

    /**
     * Lists cash deposits, newest {@code depositedAt} first. Returns
     * {@code {data, total, page, limit, totalPages}}.
     *
     * <p>Optional {@code query} keys: {@code page}, {@code limit} (1–100), {@code dateFrom},
     * {@code dateTo} (ISO 8601, on depositedAt), {@code externalCustomerId}, {@code minAmount},
     * {@code maxAmount}, {@code currency}, {@code channel}, {@code decision}, {@code branchId},
     * {@code agentId}.
     *
     * <p>Each row: {@code cashDepositId, externalTransactionId, externalCustomerId, customerName,
     * amount} (a decimal <em>string</em>, e.g. {@code "2500000.00"}){@code , currency, channel,
     * branchId, agentId, location, depositorName, metadata, decision, riskScore, depositedAt,
     * createdAt}.
     */
    public Map<String, Object> list(Map<String, Object> query) {
        return http.get(BASE, query);
    }

    /**
     * One cash deposit by Protegey {@code cashDepositId} or your {@code externalTransactionId} —
     * same fields as a {@link #list} row plus up to 50 {@code alerts}. 404 when not found.
     */
    public Map<String, Object> get(String id) {
        return http.get(BASE + "/" + Client.segment(id));
    }
}
