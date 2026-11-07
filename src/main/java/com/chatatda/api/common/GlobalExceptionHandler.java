package com.chatatda.api.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApi(ApiException e) {
        return respond(e.code().status(), e.code(), e.getMessage(), e.details());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> details = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(f -> details.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return validationFailed("요청 값이 올바르지 않아요.", details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException e) {
        return validationFailed("요청 본문을 읽을 수 없어요.", null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return validationFailed("요청 값이 올바르지 않아요.", Map.of(e.getName(), "형식이 올바르지 않아요."));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException e) {
        return validationFailed("필수 값이 없어요.", Map.of(e.getParameterName(), "필수 값이에요."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> handleNoResource(NoResourceFoundException e) {
        return respond(ErrorCode.NOT_FOUND.status(), ErrorCode.NOT_FOUND, "대상을 찾을 수 없어요.", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED.status(), ErrorCode.METHOD_NOT_ALLOWED,
                "지원하지 않는 요청 방식이에요.", null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception e) {
        if (e instanceof ErrorResponse er && er.getStatusCode().is4xxClientError()) {
            return respond(er.getStatusCode(), ErrorCode.VALIDATION_FAILED, "요청이 올바르지 않아요.", null);
        }
        log.error("처리되지 않은 예외", e);
        return respond(ErrorCode.INTERNAL_ERROR.status(), ErrorCode.INTERNAL_ERROR, "서버에 문제가 생겼어요.", null);
    }

    private ResponseEntity<ApiError> validationFailed(String message, Object details) {
        return respond(ErrorCode.VALIDATION_FAILED.status(), ErrorCode.VALIDATION_FAILED, message, details);
    }

    private ResponseEntity<ApiError> respond(HttpStatusCode status, ErrorCode code, String message, Object details) {
        return ResponseEntity.status(status).body(ApiError.of(code, message, details));
    }
}
