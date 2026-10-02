package com.protegey.sdk;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionsModuleTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    private String startServerReturning(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/partner-api/transactions", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort();
    }

    @Test
    void reportReturnsTheDecision() throws IOException {
        String baseUrl = startServerReturning(201,
            "{\"transactionId\":\"tx-1\",\"decision\":\"clear\",\"riskScore\":0,\"alerts\":[],\"deviceAction\":null}");
        Protegey protegey = new Protegey("key", baseUrl, HttpClient.newHttpClient());

        var result = protegey.transactions.report(Map.of(
            "externalTransactionId", "tx-1",
            "externalCustomerId", "cust-1",
            "direction", "DEBIT",
            "amount", 5000,
            "transactionType", "cashout"
        ));

        assertEquals("tx-1", result.get("transactionId"));
        assertEquals("clear", result.get("decision"));
    }

    @Test
    void throwsOnANonSuccessResponse() throws IOException {
        String baseUrl = startServerReturning(422, "{\"message\":\"amount must be positive\"}");
        Protegey protegey = new Protegey("key", baseUrl, HttpClient.newHttpClient());

        ProtegeyApiException ex = assertThrows(ProtegeyApiException.class, () -> protegey.transactions.report(Map.of(
            "externalTransactionId", "tx-1",
            "externalCustomerId", "cust-1",
            "direction", "DEBIT",
            "amount", -1,
            "transactionType", "cashout"
        )));

        assertEquals(422, ex.getStatus());
        assertEquals("amount must be positive", ex.getMessage());
    }
}
