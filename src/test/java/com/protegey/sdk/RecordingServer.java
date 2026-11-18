package com.protegey.sdk;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/** Local JDK HttpServer that records the last request and replies with a fixed status/body. */
final class RecordingServer implements AutoCloseable {
    final HttpServer server;
    volatile String method;
    volatile String pathAndQuery;
    volatile String body;
    volatile String apiKey;
    volatile int status = 200;
    volatile String response = "{}";

    RecordingServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            method = exchange.getRequestMethod();
            pathAndQuery = exchange.getRequestURI().getRawPath()
                + (exchange.getRequestURI().getRawQuery() != null ? "?" + exchange.getRequestURI().getRawQuery() : "");
            body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            apiKey = exchange.getRequestHeaders().getFirst("x-api-key");
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    RecordingServer reply(int status, String response) {
        this.status = status;
        this.response = response;
        return this;
    }

    String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
