package com.protegey.sdk;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class TransactionsModule {
    private final Client http;

    TransactionsModule(Client http) {
        this.http = http;
    }

    /**
     * Thin, faithful mapping onto {@code POST /partner-api/transactions} — all validation and
     * business logic stays server-side.
     *
     * <p>Required keys: {@code externalTransactionId}, {@code externalCustomerId},
     * {@code direction} ({@code "DEBIT"}|{@code "CREDIT"}), {@code amount}, {@code transactionType}.
     * Optional: {@code currency}, {@code counterpartyExternalId}, {@code isCash},
     * {@code occurredAt} (defaults to now), {@code segment}, {@code country}, {@code isPep},
     * {@code channel} ({@code "branch"}|{@code "atm"}|{@code "pos"}|{@code "online"}|
     * {@code "mobile_app"}|{@code "ussd"}|{@code "agent"}|{@code "api"}|{@code "call_center"}),
     * {@code counterpartyInstitutionCode} (SWIFT/BIC or a local bank/MNO code),
     * {@code counterpartyCountry} (compare against the customer's own {@code country} in a Pan
     * Studio rule to flag cross-border activity).
     */
    public Map<String, Object> report(Map<String, Object> input) {
        Map<String, Object> body = new HashMap<>(input);
        body.putIfAbsent("occurredAt", Instant.now().toString());
        return http.post("/partner-api/transactions", body);
    }
}
