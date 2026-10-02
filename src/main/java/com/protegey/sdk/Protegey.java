package com.protegey.sdk;

import java.net.http.HttpClient;

/**
 * The Protegey SDK for Java backends — transaction reporting and identity verification sessions,
 * called directly from your server with your own API key, plus a webhook signature verifier.
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
 * <p>{@code baseUrl} has no default — confirm the current value with Protegey (it may differ
 * between environments and can change independently of this package's version).
 */
public class Protegey {
    public final TransactionsModule transactions;
    public final KycModule kyc;

    public Protegey(String apiKey, String baseUrl) {
        this(apiKey, baseUrl, HttpClient.newHttpClient());
    }

    public Protegey(String apiKey, String baseUrl, HttpClient httpClient) {
        Client http = new Client(apiKey, baseUrl, httpClient);
        this.transactions = new TransactionsModule(http);
        this.kyc = new KycModule(http);
    }
}
