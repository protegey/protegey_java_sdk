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

/** Route, method, query and body checks for every partner-api endpoint (reference: backend prod). */
class PartnerApiTest {
    private RecordingServer server;
    private Protegey protegey;

    @BeforeEach
    void start() throws IOException {
        server = new RecordingServer();
        protegey = new Protegey("test-key", server.baseUrl(), HttpClient.newHttpClient());
    }

    @AfterEach
    void stop() {
        server.close();
    }

    // ---- KYC ----

    @Test
    void kycListConvertsUpperSnakeStatusToTheTitleCaseFilterTheBackendAccepts() {
        server.reply(200, "{\"data\":[{\"sessionId\":\"s1\",\"status\":\"IN_REVIEW\",\"outcome\":\"MANUAL\"}],\"total\":1,\"page\":1,\"limit\":20,\"totalPages\":1}");
        Map<String, Object> q = new HashMap<>();
        q.put("status", "IN_REVIEW");
        q.put("page", 2);
        Map<String, Object> page = protegey.kyc.listSessions(q);

        assertEquals("GET", server.method);
        assertTrue(server.pathAndQuery.startsWith("/partner-api/kyc/sessions?"));
        assertTrue(server.pathAndQuery.contains("status=In%20Review"));
        assertTrue(server.pathAndQuery.contains("page=2"));
        assertEquals("test-key", server.apiKey);
        assertEquals(1, ((List<?>) page.get("data")).size());
    }

    @Test
    void kycStatusConversion() {
        assertEquals("Kyc Expired", KycModule.toTitleCaseStatus("KYC_EXPIRED"));
        assertEquals("Not Started", KycModule.toTitleCaseStatus("NOT_STARTED"));
        assertEquals("In Review", KycModule.toTitleCaseStatus("In Review"));
    }

    @Test
    void kycUpdateAndResend() {
        server.reply(200, "{\"sessionId\":\"s1\",\"status\":\"APPROVED\",\"outcome\":\"MANUAL\",\"decision\":null}");
        Map<String, Object> result = protegey.kyc.updateSession("s1", Map.of("status", "APPROVED", "reviewNote", "ok"));
        assertEquals("PATCH", server.method);
        assertEquals("/partner-api/kyc/sessions/s1", server.pathAndQuery);
        assertTrue(server.body.contains("\"status\":\"APPROVED\""));
        assertEquals("APPROVED", result.get("status"));

        server.reply(201, "{\"sessionId\":\"s1\",\"url\":\"https://kyc.protegey.com/?enrollmentId=s1&token=t\"}");
        protegey.kyc.resendSession("s1");
        assertEquals("POST", server.method);
        assertEquals("/partner-api/kyc/sessions/s1/resend", server.pathAndQuery);
    }

    // ---- KYB ----

    @Test
    void kybInviteWithPhoneOnlyReturnsApplicationUrl() {
        server.reply(201, "{\"id\":\"c1\",\"contactEmail\":null,\"contactPhone\":\"+237690000000\",\"contactName\":\"John\","
            + "\"status\":\"INVITED\",\"applicationUrl\":\"https://portal/client-application?token=raw\"}");
        Map<String, Object> result = protegey.kyb.invite("John", null, "+237690000000");

        assertEquals("POST", server.method);
        assertEquals("/partner-api/kyb/applicants", server.pathAndQuery);
        assertTrue(server.body.contains("\"contactPhone\":\"+237690000000\""));
        assertFalse(server.body.contains("contactEmail"));
        assertEquals("INVITED", result.get("status"));
        assertEquals("https://portal/client-application?token=raw", result.get("applicationUrl"));
    }

    @Test
    void kybInviteRequiresEmailOrPhone() {
        assertThrows(IllegalArgumentException.class, () -> protegey.kyb.invite("John", null, null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void kybResponsesNeverExposeLeakedInternalFields() {
        server.reply(200, "{\"data\":[{\"id\":\"c1\",\"status\":\"PENDING_REVIEW\",\"submission\":{\"id\":\"s1\",\"status\":\"SUBMITTED\","
            + "\"tokenHash\":\"abc\",\"documentFilePath\":\"s3/key\",\"documentFileName\":\"doc.pdf\"}}],\"total\":1,\"page\":1,\"limit\":20,\"totalPages\":1}");
        Map<String, Object> page = protegey.kyb.listApplicants(Map.of("status", "PENDING_REVIEW", "stage", "responded"));
        assertTrue(server.pathAndQuery.contains("status=PENDING_REVIEW"));
        assertTrue(server.pathAndQuery.contains("stage=responded"));
        Map<String, Object> submission = (Map<String, Object>) ((Map<String, Object>) ((List<?>) page.get("data")).get(0)).get("submission");
        assertFalse(submission.containsKey("tokenHash"));
        assertFalse(submission.containsKey("documentFilePath"));
        assertEquals("doc.pdf", submission.get("documentFileName"));

        server.reply(201, "{\"id\":\"s1\",\"status\":\"PENDING\",\"tokenHash\":\"new\",\"applicationUrl\":\"https://portal/x\"}");
        Map<String, Object> resent = protegey.kyb.resendInvitation("c1");
        assertEquals("/partner-api/kyb/applicants/c1/resend-invitation", server.pathAndQuery);
        assertFalse(resent.containsKey("tokenHash"));
        assertEquals("https://portal/x", resent.get("applicationUrl"));
    }

    @Test
    void kybDecideSendsReasonOrNoteUnderTheRightKey() {
        server.reply(200, "{\"id\":\"c1\",\"status\":\"REJECTED\"}");
        protegey.kyb.reject("c1", "Fake documents");
        assertEquals("PATCH", server.method);
        assertEquals("/partner-api/kyb/applicants/c1/decide", server.pathAndQuery);
        assertTrue(server.body.contains("\"decision\":\"REJECT\""));
        assertTrue(server.body.contains("\"reason\":\"Fake documents\""));

        server.reply(200, "{\"id\":\"c1\",\"status\":\"MORE_INFO_REQUIRED\",\"applicationUrl\":\"https://portal/y\"}");
        Map<String, Object> r = protegey.kyb.requestMoreInfo("c1", "Need RCCM");
        assertTrue(server.body.contains("\"decision\":\"REQUEST_MORE_INFO\""));
        assertTrue(server.body.contains("\"note\":\"Need RCCM\""));
        assertEquals("https://portal/y", r.get("applicationUrl"));

        protegey.kyb.approve("c1");
        assertTrue(server.body.contains("\"decision\":\"APPROVE\""));
    }

    @Test
    void kybGetAndScreen() {
        server.reply(200, "{\"business\":{\"decision\":\"REVIEW\"},\"owners\":[],\"degraded\":false}");
        Map<String, Object> s = protegey.kyb.screen("c1");
        assertEquals("GET", server.method);
        assertEquals("/partner-api/kyb/applicants/c1/screen", server.pathAndQuery);
        assertEquals(List.of(), s.get("owners"));

        protegey.kyb.getApplicant("c 1");
        assertEquals("/partner-api/kyb/applicants/c%201", server.pathAndQuery);
    }

    // ---- Transactions ----

    @Test
    void transactionsListAndGet() {
        server.reply(200, "{\"data\":[{\"transactionId\":\"t1\",\"amount\":\"15000.00\",\"decision\":\"CLEAR\"}],\"total\":1,\"page\":1,\"limit\":20,\"totalPages\":1}");
        Map<String, Object> q = new HashMap<>();
        q.put("decision", "BLOCKED");
        q.put("externalCustomerId", "cust-1");
        q.put("dateFrom", null);
        Map<String, Object> page = protegey.transactions.list(q);
        assertTrue(server.pathAndQuery.contains("decision=BLOCKED"));
        assertFalse(server.pathAndQuery.contains("dateFrom"));
        assertEquals("15000.00", ((Map<?, ?>) ((List<?>) page.get("data")).get(0)).get("amount"));

        protegey.transactions.get("tx-ext-1");
        assertEquals("/partner-api/transactions/tx-ext-1", server.pathAndQuery);
    }

    // ---- Alerts ----

    @Test
    void alertsListLowercasesStatusAndDispositionFilters() {
        server.reply(200, "{\"data\":[],\"total\":0,\"page\":1,\"limit\":20,\"totalPages\":1}");
        protegey.alerts.list(Map.of("status", "MORE_INFO_REQUESTED", "disposition", "FALSE_POSITIVE", "alertNumber", 1032));
        assertEquals("GET", server.method);
        assertTrue(server.pathAndQuery.startsWith("/partner-api/alerts?"));
        assertTrue(server.pathAndQuery.contains("status=more_info_requested"));
        assertTrue(server.pathAndQuery.contains("disposition=false_positive"));
        assertTrue(server.pathAndQuery.contains("alertNumber=1032"));
    }

    @Test
    void alertsGetUpdateConvertAndFlag() {
        server.reply(200, "{\"id\":\"a1\",\"status\":\"OPEN\",\"ruleSeverity\":\"review\",\"transaction\":null}");
        protegey.alerts.get("a1");
        assertEquals("/partner-api/alerts/a1", server.pathAndQuery);

        Map<String, Object> changes = new HashMap<>();
        changes.put("status", "CONFIRMED");
        changes.put("assignedToUserId", null);
        protegey.alerts.update("a1", changes);
        assertEquals("PATCH", server.method);
        assertTrue(server.body.contains("\"assignedToUserId\":null"), "explicit null must be sent to clear a field");

        server.reply(201, "{\"alert\":{\"id\":\"a1\",\"disposition\":\"ESCALATED\"},\"case\":{\"id\":\"k1\",\"status\":\"OPEN\",\"priority\":\"medium\"}}");
        Map<String, Object> converted = protegey.alerts.convertToCase("a1");
        assertEquals("POST", server.method);
        assertEquals("/partner-api/alerts/a1/convert-to-case", server.pathAndQuery);
        assertEquals("OPEN", ((Map<?, ?>) converted.get("case")).get("status"));

        server.reply(201, "{\"id\":\"a2\",\"ruleCode\":\"MANUAL-FLAG-001\",\"status\":\"OPEN\"}");
        protegey.alerts.flagTransaction("t-uuid");
        assertEquals("/partner-api/alerts/flag-transaction", server.pathAndQuery);
        assertTrue(server.body.contains("\"transactionId\":\"t-uuid\""));
    }

    // ---- Device / behavioral / sanctions / shared signal ----

    @Test
    void deviceAndBehavioralEvents() {
        server.reply(201, "{\"recorded\":true,\"action\":\"SOFT_CHALLENGE\",\"riskScore\":15}");
        Map<String, Object> d = protegey.deviceEvents.record(Map.of("eventId", "e1", "visitorId", "v1",
            "deviceAttributes", Map.of("isVpnActive", true)));
        assertEquals("/partner-api/device-events", server.pathAndQuery);
        assertTrue(server.body.contains("\"isVpnActive\":true"));
        assertEquals("SOFT_CHALLENGE", d.get("action"));

        server.reply(201, "{\"status\":\"LEARNING\",\"deviationScore\":0,\"confidenceTier\":null,\"stepUpRecommended\":false,\"escalatedAlertId\":null}");
        Map<String, Object> b = protegey.behavioralEvents.record(Map.of("externalCustomerId", "c1", "sessionId", "s1",
            "session", Map.of("loginHourBucket", 3)));
        assertEquals("/partner-api/behavioral-events", server.pathAndQuery);
        assertEquals("LEARNING", b.get("status"));
        assertNull(b.get("confidenceTier"));
    }

    @Test
    void sanctionsTypeIsSentLowercase() {
        server.reply(201, "{\"decision\":\"CLEAR\",\"score\":0,\"pepMatch\":false,\"matches\":[],\"provider\":\"protegey\",\"providers\":[\"protegey\"],\"defaultListChecked\":true,\"degraded\":false}");
        Map<String, Object> r = protegey.sanctions.screen("Jane Doe", "PERSON");
        assertEquals("/partner-api/sanctions/screen", server.pathAndQuery);
        assertTrue(server.body.contains("\"type\":\"person\""));
        assertEquals("CLEAR", r.get("decision"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sharedSignalCheckReturnsAnonymisedMatches() {
        server.reply(201, "{\"flagged\":true,\"category\":\"CONFIRMED_FRAUD\",\"reportedDaysAgo\":3,\"flagConfidence\":37,\"matchCount\":2,"
            + "\"matches\":[{\"category\":\"CONFIRMED_FRAUD\",\"reportedDaysAgo\":3},{\"category\":\"IDENTITY_THEFT\",\"reportedDaysAgo\":40}]}");
        Map<String, Object> r = protegey.sharedSignal.check(Map.of("governmentId", "CM123", "externalCustomerId", "cust-1"));
        assertEquals("/partner-api/shared-signal/check", server.pathAndQuery);
        assertEquals(2.0, r.get("matchCount"));
        Map<String, Object> first = ((List<Map<String, Object>>) r.get("matches")).get(0);
        assertEquals(2, first.size());
        assertEquals("CONFIRMED_FRAUD", first.get("category"));
    }

    // ---- Errors ----

    @Test
    void gatingErrorsExposeErrorCode() {
        server.reply(403, "{\"errorCode\":\"PARTNER_CONTRACT_REQUIRED\",\"message\":\"Your account has no billing contract configured yet.\"}");
        ProtegeyApiException ex = assertThrows(ProtegeyApiException.class,
            () -> protegey.sanctions.screen("x"));
        assertEquals(403, ex.getStatus());
        assertEquals(ProtegeyApiException.PARTNER_CONTRACT_REQUIRED, ex.getErrorCode());
    }

    @Test
    void validationErrorsJoinTheMessageArray() {
        server.reply(400, "{\"statusCode\":400,\"message\":[\"name should not be empty\",\"type must be one of\"],\"error\":\"Bad Request\"}");
        ProtegeyApiException ex = assertThrows(ProtegeyApiException.class, () -> protegey.sanctions.screen(""));
        assertEquals("name should not be empty, type must be one of", ex.getMessage());
        assertNull(ex.getErrorCode());
    }
}
