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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
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

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> handleTooLarge(MaxUploadSizeExceededException e) {
        return validationFailed("올릴 수 없는 파일이에요.", Map.of("file", "5MB 이하만 올릴 수 있어요."));
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

    /** 동시 수정: 다른 요청이 먼저 고쳤다 (양도 글 @Version) */
    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiError> handleOptimisticLock(Exception e) {
        return respond(ErrorCode.CONFLICT.status(), ErrorCode.CONFLICT, "다른 곳에서 먼저 바뀌었어요. 새로 불러온 뒤 다시 해 주세요.", null);
    }

    /** 고유 제약 위반: 동시에 같은 것을 두 번 만들려 했다 (500 대신 409) */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleIntegrity(org.springframework.dao.DataIntegrityViolationException e) {
        log.warn("제약 위반 → 409: {}", e.getMostSpecificCause().getMessage());
        return respond(ErrorCode.ALREADY_EXISTS.status(), ErrorCode.ALREADY_EXISTS, "이미 처리된 요청이에요.", null);
    }

    /** multipart 가 아닌 업로드 요청 */
    @ExceptionHandler(org.springframework.web.multipart.MultipartException.class)
    ResponseEntity<ApiError> handleMultipart(org.springframework.web.multipart.MultipartException e) {
        return validationFailed("파일을 multipart/form-data 의 file 로 보내 주세요.", Map.of("file", "필수 값이에요."));
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
