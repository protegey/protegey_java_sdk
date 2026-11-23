package com.protegey.sdk;

import java.util.Map;

/**
 * Thrown on any non-2xx response (or a transport failure, with {@code status} 0).
 *
 * <p>{@link #getErrorCode()} carries the machine-readable code Protegey sends on gating errors —
 * {@code PARTNER_SUSPENDED} or {@code PARTNER_CONTRACT_REQUIRED} (both 403, mutating calls only),
 * {@code NO_3D_ENROLLMENT} (400, a re-verification for a customer with no KYC face) — on any status
 * where the error body has an {@code errorCode}, and is {@code null} otherwise. Validation errors (400) join Nest's message array with ", ".
 */
public class ProtegeyApiException extends RuntimeException {
    public static final String PARTNER_SUSPENDED = "PARTNER_SUSPENDED";
    public static final String PARTNER_CONTRACT_REQUIRED = "PARTNER_CONTRACT_REQUIRED";
    public static final String NO_3D_ENROLLMENT = "NO_3D_ENROLLMENT";

    private final int status;
    private final String errorCode;
    private final Map<String, Object> body;

    public ProtegeyApiException(int status, String message) {
        this(status, message, null, null);
    }

    public ProtegeyApiException(int status, String message, String errorCode, Map<String, Object> body) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.body = body != null ? body : Map.of();
    }

    public int getStatus() {
        return status;
    }

    /** {@code PARTNER_SUSPENDED}, {@code PARTNER_CONTRACT_REQUIRED}, {@code NO_3D_ENROLLMENT}, or {@code null}. */
    public String getErrorCode() {
        return errorCode;
    }

    /** The parsed error body (empty if it wasn't JSON). */
    public Map<String, Object> getBody() {
        return body;
    }
}
