package com.protegey.sdk;

import java.net.http.HttpClient;

/**
 * The Protegey SDK for Java backends — the full partner API ({@code /partner-api/*}), called
 * directly from your server with your own API key, plus a webhook signature verifier.
 *
 * <pre>{@code
 * Protegey protegey = new Protegey("YOUR_API_KEY", "https://api.protegey.com");
 * Map<String, Object> result = protegey.transactions.report(Map.of(
 *     "externalTransactionId", "tx-1",
 *     "externalCustomerId", "cust-1",
 *     "direction", "DEBIT",
 *     "amount", 5000,
 *     "transactionType", "cashout"
 * ));
 * Map<String, Object> session = protegey.kyc.startSession("cust-1");
 * }</pre>
 *
 * <p>Responses are plain {@code Map<String, Object>} (Gson: JSON numbers arrive as
 * {@code Double}), so new fields and new enum values never break deserialization. Status-like
 * values ({@code status}, {@code decision}, {@code action}, {@code category}, ...) are
 * UPPER_SNAKE_CASE on the wire.
 *
 * <p>{@code baseUrl} has no default — confirm the current value with Protegey (production is
 * {@code https://api.protegey.com}; it may differ between environments).
 */
public class Protegey {
    public final TransactionsModule transactions;
    public final KycModule kyc;
    public final KybModule kyb;
    public final AlertsModule alerts;
    public final DeviceEventsModule deviceEvents;
    public final BehavioralEventsModule behavioralEvents;
    public final SanctionsModule sanctions;
    public final SharedSignalModule sharedSignal;

    public Protegey(String apiKey, String baseUrl) {
        this(apiKey, baseUrl, HttpClient.newHttpClient());
    }

    public Protegey(String apiKey, String baseUrl, HttpClient httpClient) {
        Client http = new Client(apiKey, baseUrl, httpClient);
        this.transactions = new TransactionsModule(http);
        this.kyc = new KycModule(http);
        this.kyb = new KybModule(http);
        this.alerts = new AlertsModule(http);
        this.deviceEvents = new DeviceEventsModule(http);
        this.behavioralEvents = new BehavioralEventsModule(http);
        this.sanctions = new SanctionsModule(http);
        this.sharedSignal = new SharedSignalModule(http);
    }
}
