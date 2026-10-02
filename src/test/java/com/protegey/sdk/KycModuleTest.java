package com.protegey.sdk;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Uses a real local HttpServer (JDK built-in, no mocking library needed) instead of a real
 * network call to Protegey's API. */
class KycModuleTest {
    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/partner-api/kyc/sessions", exchange -> {
            String response;
            if ("POST".equals(exchange.getRequestMethod())) {
                response = "{\"sessionId\":\"sess_abc\",\"url\":\"https://verify.didit.me/session/abc\"}";
            } else {
                response = "{\"sessionId\":\"sess_1\",\"status\":\"Approved\"}";
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void startSessionReturnsTheHostedUrl() {
        Protegey protegey = new Protegey("test-api-key", baseUrl, HttpClient.newHttpClient());

        var result = protegey.kyc.startSession("cust-1");

        assertEquals("sess_abc", result.get("sessionId"));
        assertEquals("https://verify.didit.me/session/abc", result.get("url"));
    }

    @Test
    void getSessionReturnsTheCurrentStatus() {
        Protegey protegey = new Protegey("test-api-key", baseUrl, HttpClient.newHttpClient());

        var result = protegey.kyc.getSession("sess/1");

        assertEquals("Approved", result.get("status"));
    }
}
