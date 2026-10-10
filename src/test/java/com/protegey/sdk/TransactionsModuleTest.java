package com.protegey.sdk;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private String startServerCapturingRequestBody(int status, String responseBody, AtomicReference<String> capturedBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/partner-api/transactions", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
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
    void reportForwardsChannelCounterpartyInstitutionCodeAndCounterpartyCountry() throws IOException {
        AtomicReference<String> captured = new AtomicReference<>();
        String baseUrl = startServerCapturingRequestBody(201,
            "{\"transactionId\":\"tx-1\",\"decision\":\"clear\",\"riskScore\":0,\"alerts\":[],\"deviceAction\":null}", captured);
        Protegey protegey = new Protegey("key", baseUrl, HttpClient.newHttpClient());

        var wire = new java.util.HashMap<String, Object>();
        wire.put("externalTransactionId", "tx-1");
        wire.put("externalCustomerId", "cust-1");
        wire.put("direction", "DEBIT");
        wire.put("amount", 2000000);
        wire.put("transactionType", "wire");
        wire.put("channel", "online");
        wire.put("counterpartyInstitutionCode", "ECOBKTGTG");
        wire.put("counterpartyCountry", "SN");
        protegey.transactions.report(wire);

        String body = captured.get();
        assertTrue(body.contains("\"channel\":\"online\""));
        assertTrue(body.contains("\"counterpartyInstitutionCode\":\"ECOBKTGTG\""));
        assertTrue(body.contains("\"counterpartyCountry\":\"SN\""));
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
