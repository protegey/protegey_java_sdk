package com.protegey.sdk;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Transaction monitoring — {@code /partner-api/transactions}. Sandbox-scoped (quota: 100 sandbox
 * transactions per UTC day).
 */
public class TransactionsModule {
    private static final String BASE = "/partner-api/transactions";

    private final Client http;

    TransactionsModule(Client http) {
        this.http = http;
    }

    /**
     * Thin, faithful mapping onto {@code POST /partner-api/transactions} — all validation and
     * business logic stays server-side.
     *
     * <p>Required keys: {@code externalTransactionId} (idempotency key — a repeat gets a 409),
     * {@code externalCustomerId}, {@code direction} ({@code "DEBIT"}|{@code "CREDIT"}, exact
     * uppercase), {@code amount} (positive), {@code transactionType}, {@code occurredAt} (ISO 8601 —
     * this method defaults it to now).
     *
     * <p>Optional: {@code currency} (default XOF), {@code customerName} (self-declared, remembered
     * on the customer), {@code segment} ({@code KYC1}|{@code KYC2}|{@code AGENT}|
     * {@code SUPER_AGENT}|{@code MERCHANT}|{@code CORPORATE}, exact uppercase), {@code country},
     * {@code isPep}, {@code counterpartyExternalId}, {@code channel} ({@code branch}|{@code atm}|
     * {@code pos}|{@code online}|{@code mobile_app}|{@code ussd}|{@code agent}|{@code api}|
     * {@code call_center}), {@code counterpartyInstitutionCode}, {@code counterpartyCountry},
     * {@code isCash}, {@code visitorId} (omit it and Protegey auto-matches a device event for this
     * customer from the last ~15 minutes), {@code deviceAttributes} (same shape as
     * {@link DeviceEventsModule#record}), {@code devicePhoneNumber} (your own customer-account
     * identifier), {@code lastPasswordChangeAt} / {@code lastEmailChangeAt} /
     * {@code lastPhoneChangeAt} (ISO 8601 — lets a rule flag activity right after a credential
     * change or SIM swap), {@code accountBalanceBefore} (positive; lets a rule flag an account
     * drain).
     *
     * <p>Returns {@code {transactionId, decision (CLEAR|REVIEW|BLOCKED), riskScore,
     * deviceAction (ALLOW|SOFT_CHALLENGE|HARD_CHALLENGE|BLOCK|null), alerts[]}}.
     */
    public Map<String, Object> report(Map<String, Object> input) {
        Map<String, Object> body = new HashMap<>(input);
        body.putIfAbsent("occurredAt", Instant.now().toString());
        return http.post(BASE, body);
    }

    /**
     * Lists transactions, newest {@code occurredAt} first. Optional {@code query} keys:
     * {@code page}, {@code limit}, {@code direction}, {@code decision}, {@code externalCustomerId},
     * {@code dateFrom}, {@code dateTo} (ISO 8601). Note: {@code amount} in each row is a decimal
     * <em>string</em> (e.g. {@code "15000.00"}).
     */
    public Map<String, Object> list(Map<String, Object> query) {
        return http.get(BASE, query);
    }

    /**
     * One transaction by Protegey {@code transactionId} or your {@code externalTransactionId},
     * with up to 50 alerts. Carries no amount/currency/direction.
     */
    public Map<String, Object> get(String id) {
        return http.get(BASE + "/" + Client.segment(id));
    }
}
