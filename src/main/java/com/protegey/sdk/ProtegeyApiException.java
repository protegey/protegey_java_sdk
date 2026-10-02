package com.protegey.sdk;

public class ProtegeyApiException extends RuntimeException {
    private final int status;

    public ProtegeyApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
