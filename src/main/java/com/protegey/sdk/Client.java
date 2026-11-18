package com.protegey.sdk;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * Thin HTTP wrapper shared by every SDK module — one place that knows about auth, base URL and
 * error shape. Accepts an injectable {@link HttpClient} so tests can point it at a local test
 * server instead of making a real network call.
 *
 * <p>Auth is the {@code x-api-key} header. Live vs sandbox is decided server-side by which key you
 * use — there is no header or key prefix that selects the mode.
 *
 * <p>{@code baseUrl} is deliberately required, with NO built-in default: confirm the current value
 * with Protegey before you ship (it can differ between environments and change independently of
 * this package's version). Paths are {@code /partner-api/...} with no version prefix.
 */
public class Client {
    // serializeNulls: PATCH bodies use an explicit null to clear a field (e.g. unassigning an alert).
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
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
        return send(withBody("POST", path, body));
    }

    public Map<String, Object> patch(String path, Map<String, Object> body) {
        return send(withBody("PATCH", path, body));
    }

    public Map<String, Object> get(String path) {
        return get(path, null);
    }

    /** GET with query parameters; {@code null} values are skipped. */
    public Map<String, Object> get(String path, Map<String, ?> query) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path + queryString(query)))
            .header("x-api-key", apiKey)
            .GET()
            .build();
        return send(request);
    }

    /** URL-encodes one path segment (an id). */
    static String segment(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Protegey: id is required");
        }
        return encode(value);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String queryString(Map<String, ?> query) {
        if (query == null || query.isEmpty()) return "";
        StringJoiner joiner = new StringJoiner("&", "?", "");
        joiner.setEmptyValue("");
        for (Map.Entry<String, ?> entry : query.entrySet()) {
            if (entry.getValue() == null) continue;
            Object value = entry.getValue();
            // Gson-style numbers (e.g. 2.0) must go out as "2" for int query params.
            String text = value instanceof Double && ((Double) value) % 1 == 0
                ? String.valueOf(((Double) value).longValue())
                : String.valueOf(value);
            joiner.add(encode(entry.getKey()) + "=" + encode(text));
        }
        return joiner.toString();
    }

    private HttpRequest withBody(String method, String path, Map<String, Object> body) {
        return HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .method(method, HttpRequest.BodyPublishers.ofString(GSON.toJson(body != null ? body : Map.of())))
            .build();
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
            Object errorCode = data != null ? data.get("errorCode") : null;
            throw new ProtegeyApiException(status, message, errorCode != null ? errorCode.toString() : null, data);
        }

        return data != null ? data : Map.of();
    }
}
