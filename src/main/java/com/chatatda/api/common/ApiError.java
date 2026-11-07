package com.chatatda.api.common;

public record ApiError(String code, String message, Object details) {

    public static ApiError of(ErrorCode code, String message, Object details) {
        return new ApiError(code.name(), message, details);
    }
}
