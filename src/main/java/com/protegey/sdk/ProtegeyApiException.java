package com.protegey.sdk;

import java.util.Map;

/**
 * Thrown on any non-2xx response (or a transport failure, with {@code status} 0).
 *
 * <p>{@link #getErrorCode()} carries the machine-readable code Protegey sends on gating errors —
 * {@code PARTNER_SUSPENDED} or {@code PARTNER_CONTRACT_REQUIRED} (both 403, mutating calls only) —
 * and is {@code null} otherwise. Validation errors (400) join Nest's message array with ", ".
 */
public class ProtegeyApiException extends RuntimeException {
    public static final String PARTNER_SUSPENDED = "PARTNER_SUSPENDED";
    public static final String PARTNER_CONTRACT_REQUIRED = "PARTNER_CONTRACT_REQUIRED";

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

    /** {@code PARTNER_SUSPENDED}, {@code PARTNER_CONTRACT_REQUIRED}, or {@code null}. */
    public String getErrorCode() {
        return errorCode;
    }

    /** The parsed error body (empty if it wasn't JSON). */
    public Map<String, Object> getBody() {
        return body;
    }
}
