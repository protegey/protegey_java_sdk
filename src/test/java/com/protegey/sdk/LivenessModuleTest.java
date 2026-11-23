package com.protegey.sdk;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Uses a real local HttpServer (JDK built-in, no mocking library needed) instead of a real
 * network call to Protegey's API. */
class LivenessModuleTest {
    private static final String CREATED = "{\"sessionId\":\"liv_1\",\"mode\":\"LIVENESS\",\"status\":\"PENDING\","
        + "\"externalUserId\":\"cust-1\",\"url\":\"https://kyc.protegey.com/liveness?token=abc\","
        + "\"captureUrl\":\"https://kyc.protegey.com/liveness?token=abc\",\"expiresInMinutes\":30}";

    private RecordingServer server;
    private Protegey protegey;

    @BeforeEach
    void start() throws IOException {
        server = new RecordingServer();
        protegey = new Protegey("test-api-key", server.baseUrl(), HttpClient.newHttpClient());
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void startSessionSendsLivenessModeAndReturnsTheHostedUrl() {
        server.reply(201, CREATED);

        var result = protegey.liveness.startSession("cust-1");

        assertEquals("POST", server.method);
        assertEquals("/partner-api/liveness/sessions", server.pathAndQuery);
        assertEquals("test-api-key", server.apiKey);
        assertTrue(server.body.contains("\"mode\":\"LIVENESS\""));
        assertTrue(server.body.contains("\"externalUserId\":\"cust-1\""));
        assertFalse(server.body.contains("referenceVerificationId"));
        assertEquals("liv_1", result.get("sessionId"));
        assertEquals("https://kyc.protegey.com/liveness?token=abc", result.get("url"));
        assertEquals(30.0, result.get("expiresInMinutes"));
    }

    @Test
    void startSessionWithoutExternalUserIdOmitsIt() {
        server.reply(201, CREATED);

        protegey.liveness.startSession(null);

        assertEquals("{\"mode\":\"LIVENESS\"}", server.body);
    }

    @Test
    void startReverificationSendsReverificationMode() {
        server.reply(201, CREATED.replace("\"LIVENESS\"", "\"REVERIFICATION\""));

        var result = protegey.liveness.startReverification("cust-1");

        assertEquals("POST", server.method);
        assertEquals("/partner-api/liveness/sessions", server.pathAndQuery);
        assertTrue(server.body.contains("\"mode\":\"REVERIFICATION\""));
        assertTrue(server.body.contains("\"externalUserId\":\"cust-1\""));
        assertFalse(server.body.contains("referenceVerificationId"));
        assertEquals("REVERIFICATION", result.get("mode"));

        protegey.liveness.startReverification("cust-1", "kyc_42");
        assertTrue(server.body.contains("\"referenceVerificationId\":\"kyc_42\""));
    }

    @Test
    void reverificationWithoutKycFaceExposesNo3dEnrollment() {
        server.reply(400, "{\"errorCode\":\"NO_3D_ENROLLMENT\",\"message\":\"No 3D record (KYC) exists for this user — run a KYC session first\"}");

        ProtegeyApiException ex = assertThrows(ProtegeyApiException.class,
            () -> protegey.liveness.startReverification("cust-unknown"));

        assertEquals(400, ex.getStatus());
        assertEquals(LivenessModule.NO_3D_ENROLLMENT, ex.getErrorCode());
        assertEquals("NO_3D_ENROLLMENT", ProtegeyApiException.NO_3D_ENROLLMENT);
        assertTrue(ex.getMessage().startsWith("No 3D record (KYC) exists for this user"));
    }

    @Test
    void getSessionParsesResultAndChecks() {
        server.reply(200, "{\"id\":\"u1\",\"sessionId\":\"liv_1\",\"mode\":\"REVERIFICATION\",\"status\":\"COMPLETED\","
            + "\"externalUserId\":\"cust-1\",\"referenceEnrollmentId\":\"enr_9\",\"livenessProven\":true,"
            + "\"matchLevel\":15,\"matched\":true,\"duplicateFaceEnrollmentIds\":null,\"attempts\":1,"
            + "\"isSandbox\":false,\"completedAt\":\"2026-11-23T10:00:00.000Z\",\"createdAt\":\"2026-11-23T09:58:00.000Z\","
            + "\"billableChecks\":1,\"amount\":\"0.50\",\"currency\":\"USD\",\"checks\":[{\"id\":\"c1\","
            + "\"livenessProven\":true,\"matchLevel\":15,\"billable\":true,\"unitPrice\":\"0.50\",\"currency\":\"USD\","
            + "\"createdAt\":\"2026-11-23T10:00:00.000Z\"}]}");

        var result = protegey.liveness.getSession("liv/1");

        assertEquals("GET", server.method);
        assertEquals("/partner-api/liveness/sessions/liv%2F1", server.pathAndQuery);
        assertEquals("COMPLETED", result.get("status"));
        assertEquals(true, result.get("livenessProven"));
        assertEquals(15.0, result.get("matchLevel"));
        assertEquals(true, result.get("matched"));
        assertNull(result.get("duplicateFaceEnrollmentIds"));
        assertEquals("0.50", result.get("amount"));
        List<?> checks = (List<?>) result.get("checks");
        assertEquals(1, checks.size());
        assertEquals(true, ((Map<?, ?>) checks.get(0)).get("billable"));
    }

    @Test
    void listSessionsForwardsFilters() {
        server.reply(200, "{\"data\":[{\"sessionId\":\"liv_1\",\"mode\":\"LIVENESS\",\"status\":\"PENDING\"}],"
            + "\"total\":1,\"page\":2,\"limit\":10,\"totalPages\":1}");
        Map<String, Object> q = new HashMap<>();
        q.put("page", 2);
        q.put("limit", 10);
        q.put("externalUserId", "cust-1");
        q.put("mode", "LIVENESS");

        var page = protegey.liveness.listSessions(q);

        assertEquals("GET", server.method);
        assertTrue(server.pathAndQuery.startsWith("/partner-api/liveness/sessions?"));
        assertTrue(server.pathAndQuery.contains("page=2"));
        assertTrue(server.pathAndQuery.contains("limit=10"));
        assertTrue(server.pathAndQuery.contains("externalUserId=cust-1"));
        assertTrue(server.pathAndQuery.contains("mode=LIVENESS"));
        assertEquals(1, ((List<?>) page.get("data")).size());
    }
}
