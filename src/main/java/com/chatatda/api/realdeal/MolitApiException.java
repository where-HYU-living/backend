package com.chatatda.api.realdeal;

public class MolitApiException extends RuntimeException {

    public MolitApiException(String message) {
        super(message);
    }

    public MolitApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
