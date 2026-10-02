package com.protegey.sdk;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Thin HTTP wrapper shared by every SDK module — one place that knows about auth, base URL and
 * error shape. Accepts an injectable {@link HttpClient} so tests can point it at a local test
 * server instead of making a real network call.
 *
 * <p>{@code baseUrl} is deliberately required, with NO built-in default: confirm the current value
 * with Protegey before you ship (it can differ between environments and change independently of
 * this package's version).
 */
public class Client {
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient http;

    public Client(String apiKey, String baseUrl) {
        this(apiKey, baseUrl, HttpClient.newHttpClient());
    }

    public Client(String apiKey, String baseUrl, HttpClient httpClient) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("Protegey: apiKey is required");
        }
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new IllegalArgumentException(
                "Protegey: baseUrl is required — point it at your Protegey API environment (e.g. https://api.protegey.com)");
        }
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = httpClient;
    }

    public Map<String, Object> post(String path, Map<String, Object> body) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
            .build();
        return send(request);
    }

    public Map<String, Object> get(String path) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .header("x-api-key", apiKey)
            .GET()
            .build();
        return send(request);
    }

    private Map<String, Object> send(HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ProtegeyApiException(0, e.getMessage());
        }

        Map<String, Object> data;
        try {
            data = GSON.fromJson(response.body(), MAP_TYPE);
        } catch (Exception e) {
            data = null;
        }

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            Object rawMessage = data != null ? data.get("message") : null;
            String message;
            if (rawMessage instanceof List) {
                message = ((List<?>) rawMessage).stream().map(String::valueOf).collect(Collectors.joining(", "));
            } else {
                message = rawMessage != null ? rawMessage.toString() : "Request failed";
            }
            throw new ProtegeyApiException(status, message);
        }

        return data != null ? data : Map.of();
    }
}
