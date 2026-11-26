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
 * {@code REJECTED}. {@code source} is {@code invitation} | {@code import}; imported applicants
 * also carry {@code externalReference} and {@code importBatchId} (both null otherwise).
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
     * {@code responded}), {@code importId} (only the applicants created by that import). Each row
     * includes {@code submission} (or null).
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

    /**
     * One applicant, including its {@code submission} and {@code kycVerifications} (the identity
     * checks requested with {@link #requestKycVerification}, same shape as
     * {@link #listKycVerifications} rows).
     */
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
     * Imports your existing KYB base — shorthand for {@code importApplicants(applicants, false)}.
     */
    public Map<String, Object> importApplicants(List<Map<String, Object>> applicants) {
        return importApplicants(applicants, false);
    }

    /**
     * Imports 1–1000 already-known businesses ({@code POST .../import}, same columns as the portal's
     * CSV template). Each applicant: {@code legalName}, {@code country} (ISO alpha-2),
     * {@code contactName} (required), {@code contactEmail} and/or {@code contactPhone} (at least
     * one), optional {@code registrationNumber}, {@code tradingName}, {@code legalStatus},
     * {@code incorporationDate} (YYYY-MM-DD), {@code address}, {@code website}, {@code owners}
     * ({@code [{name, ownershipPercent?}]}), {@code directors} ({@code [{name, role?}]}),
     * {@code status} ({@code APPROVED} default | {@code PENDING_REVIEW}), {@code externalReference}.
     * Imported applicants are created already submitted and are never emailed. {@code dryRun}
     * only validates.
     *
     * <p>Returns {@code {totalRows, importedCount, rejectedCount, errors[{row (0-based), field
     * (or null), code, error}], importId (or null)}}; {@code code} is {@code required} |
     * {@code invalid} | {@code contact_required} | {@code unknown_field} | {@code column_count} |
     * {@code duplicate_in_file} | {@code duplicate_existing}. Invalid or duplicate applicants are
     * reported and skipped, never failing the call. Screening runs in the background — follow it
     * with {@link #getImport}.
     */
    public Map<String, Object> importApplicants(List<Map<String, Object>> applicants, boolean dryRun) {
        Map<String, Object> body = new HashMap<>();
        body.put("applicants", applicants);
        if (dryRun) body.put("dryRun", true);
        return http.post(BASE + "/import", body);
    }

    /**
     * Progress of an import's background screening: {@code {importId, status (SCREENING |
     * COMPLETED), source, totalRows, importedCount, rejectedCount, screenedCount, flaggedCount,
     * flaggedClientIds[], createdAt, completedAt (or null)}}. Protegey never auto-rejects a match —
     * review {@code flaggedClientIds} yourself. Throws a 404 for an unknown import.
     */
    public Map<String, Object> getImport(String importId) {
        return http.get("/partner-api/kyb/imports/" + Client.segment(importId));
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

    /**
     * Asks the person behind a submitted application to verify their identity — a standard KYC
     * session linked to the applicant (billed and sandbox-scoped like {@link KycModule#startSession}).
     * Callable any number of times; each call creates a new session.
     */
    public Map<String, Object> requestKycVerification(String clientId) {
        return requestKycVerification(clientId, null);
    }

    /**
     * Same as {@link #requestKycVerification(String)}, with optional {@code options} keys:
     * {@code fullName} (compared with the name on the ID document — {@code kybNameMatch}; defaults
     * to the applicant contact name), {@code email} (Protegey emails the link only when it is set),
     * {@code phone} (defaults to the applicant contact phone), {@code externalUserId}.
     *
     * <p>Returns {@code {sessionId, kybClientId, url, expiresAt, emailedTo}} — {@code expiresAt} is
     * your capture-link lifetime setting, never less than 7 days here; {@code emailedTo} is null when
     * no {@code email} was given (relay {@code url} yourself). Results arrive on
     * {@code kyc.status_changed} (with {@code kybClientId}). Throws a 404 when the applicant does
     * not exist and a 400 when it has not submitted its application yet.
     */
    public Map<String, Object> requestKycVerification(String clientId, Map<String, Object> options) {
        Map<String, Object> body = options != null ? new HashMap<>(options) : new HashMap<>();
        return http.post(BASE + "/" + Client.segment(clientId) + "/kyc-sessions", body);
    }

    /**
     * Every identity check requested for an applicant, newest first: {@code {data}}, each row
     * {@code sessionId, status, outcome, recommendation, expectedName, documentFullName, nameMatch
     * (MATCH | MISMATCH | null), livenessScore, faceMatchScore, livemode, createdAt, updatedAt}. Use
     * {@link KycModule#getSession} for images and full detail.
     */
    public Map<String, Object> listKycVerifications(String clientId) {
        return http.get(BASE + "/" + Client.segment(clientId) + "/kyc-sessions");
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
