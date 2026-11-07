package com.chatatda.api.common;

public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient Object details;

    public ApiException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public ApiException(ErrorCode code, String message, Object details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    public ErrorCode code() {
        return code;
    }

    public Object details() {
        return details;
    }
}
