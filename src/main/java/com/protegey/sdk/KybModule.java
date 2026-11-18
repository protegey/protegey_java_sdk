package com.protegey.sdk;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Business (KYB) applicants — {@code /partner-api/kyb/applicants}. Not sandbox-scoped: KYB has no
 * sandbox/live split.
 *
 * <p>Applicant {@code status}: {@code INVITED}, {@code PENDING_REVIEW},
 * {@code MORE_INFO_REQUIRED}, {@code ACTIVE}, {@code REJECTED}. {@code submission.status}:
 * {@code PENDING}, {@code SUBMITTED}, {@code MORE_INFO_REQUIRED}, {@code APPROVED},
 * {@code REJECTED}.
 *
 * <p>Internal fields the backend currently leaks ({@code tokenHash}, {@code documentFilePath})
 * are stripped from every response this module returns.
 */
public class KybModule {
    private static final String BASE = "/partner-api/kyb/applicants";
    private static final List<String> INTERNAL_FIELDS = List.of("tokenHash", "documentFilePath");

    public static final String APPROVE = "APPROVE";
    public static final String REJECT = "REJECT";
    public static final String REQUEST_MORE_INFO = "REQUEST_MORE_INFO";

    private final Client http;

    KybModule(Client http) {
        this.http = http;
    }

    /**
     * Lists applicants, newest first. Optional {@code query} keys: {@code page}, {@code limit},
     * {@code search}, {@code status} (any case), {@code stage} ({@code invited} |
     * {@code responded}). Each row includes {@code submission} (or null).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> listApplicants(Map<String, Object> query) {
        Map<String, Object> page = http.get(BASE, query);
        Object data = page.get("data");
        if (data instanceof List) {
            for (Object row : (List<Object>) data) {
                if (row instanceof Map) strip((Map<String, Object>) row);
            }
        }
        return page;
    }

    /** One applicant, including its {@code submission}. */
    public Map<String, Object> getApplicant(String clientId) {
        return strip(http.get(BASE + "/" + Client.segment(clientId)));
    }

    /**
     * Invites a business. At least one of {@code contactEmail} / {@code contactPhone} is required
     * (pass {@code null} for the other); an email is only sent when {@code contactEmail} is set.
     * Returns the applicant (without {@code submission}) plus {@code applicationUrl} — the link to
     * hand to the applicant yourself (e.g. by SMS when you only have a phone number).
     */
    public Map<String, Object> invite(String contactName, String contactEmail, String contactPhone) {
        return invite(contactName, contactEmail, contactPhone, null);
    }

    /** Same as {@link #invite(String, String, String)}, with a KYB form {@code templateId}. */
    public Map<String, Object> invite(String contactName, String contactEmail, String contactPhone, String templateId) {
        if (contactEmail == null && contactPhone == null) {
            throw new IllegalArgumentException("Protegey: contactEmail or contactPhone is required");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("contactName", contactName);
        if (contactEmail != null) body.put("contactEmail", contactEmail);
        if (contactPhone != null) body.put("contactPhone", contactPhone);
        if (templateId != null) body.put("templateId", templateId);
        return strip(http.post(BASE, body));
    }

    /**
     * Rotates the invitation link. Only allowed while the application is still pending or awaiting
     * more info. Returns the <em>submission</em> object (not the applicant) plus
     * {@code applicationUrl}.
     */
    public Map<String, Object> resendInvitation(String clientId) {
        return strip(http.post(BASE + "/" + Client.segment(clientId) + "/resend-invitation", null));
    }

    /**
     * Runs sanctions/PEP screening of the business and each declared owner, and stores the result
     * on the submission. Returns {@code {business, owners[], screenedAt, provider, providers,
     * defaultListChecked, degraded, fallbackReason?}}; each {@code decision} is {@code BLOCKED} |
     * {@code REVIEW} | {@code CLEAR}.
     */
    public Map<String, Object> screen(String clientId) {
        return http.get(BASE + "/" + Client.segment(clientId) + "/screen");
    }

    /** Approves a submitted application (applicant becomes {@code ACTIVE}). */
    public Map<String, Object> approve(String clientId) {
        return decide(clientId, APPROVE, null);
    }

    /** Rejects a submitted application. {@code reason} is required (min 5 chars). */
    public Map<String, Object> reject(String clientId, String reason) {
        return decide(clientId, REJECT, reason);
    }

    /**
     * Sends a submitted application back for more information. {@code note} is required (min 5
     * chars). The response carries a fresh {@code applicationUrl}.
     */
    public Map<String, Object> requestMoreInfo(String clientId, String note) {
        return decide(clientId, REQUEST_MORE_INFO, note);
    }

    /**
     * {@code PATCH .../decide}. {@code decision} is {@code APPROVE} | {@code REJECT} |
     * {@code REQUEST_MORE_INFO}; {@code reasonOrNote} is sent as {@code reason} for REJECT and as
     * {@code note} for REQUEST_MORE_INFO. Returns the applicant (without {@code submission});
     * {@code applicationUrl} is present only for REQUEST_MORE_INFO. Fires
     * {@code kyb.status_changed}.
     */
    public Map<String, Object> decide(String clientId, String decision, String reasonOrNote) {
        Map<String, Object> body = new HashMap<>();
        body.put("decision", decision);
        if (reasonOrNote != null) {
            body.put(REQUEST_MORE_INFO.equalsIgnoreCase(decision) ? "note" : "reason", reasonOrNote);
        }
        return strip(http.patch(BASE + "/" + Client.segment(clientId) + "/decide", body));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> strip(Map<String, Object> obj) {
        if (obj.isEmpty()) return obj;
        INTERNAL_FIELDS.forEach(obj::remove);
        Object submission = obj.get("submission");
        if (submission instanceof Map) {
            INTERNAL_FIELDS.forEach(((Map<String, Object>) submission)::remove);
        }
        return obj;
    }
}
