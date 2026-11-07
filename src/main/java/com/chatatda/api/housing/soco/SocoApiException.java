package com.chatatda.api.housing.soco;

public class SocoApiException extends RuntimeException {

    public SocoApiException(String message) {
        super(message);
    }

    public SocoApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
