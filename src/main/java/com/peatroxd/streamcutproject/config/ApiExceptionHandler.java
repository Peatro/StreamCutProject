package com.peatroxd.streamcutproject.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {

    private final String uploadLimitLabel;

    public ApiExceptionHandler(
            @Value("${SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE:512MB}") String uploadLimitProperty
    ) {
        this.uploadLimitLabel = normalizeUploadLimitLabel(uploadLimitProperty);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request
    ) {
        return errorResponse(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Upload exceeds the " + uploadLimitLabel + " limit.",
                request
        );
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiErrorResponse> handleMultipartException(
            MultipartException exception,
            HttpServletRequest request
    ) {
        if (exception.getCause() instanceof MaxUploadSizeExceededException) {
            return errorResponse(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Upload exceeds the " + uploadLimitLabel + " limit.",
                    request
            );
        }
        return errorResponse(HttpStatus.BAD_REQUEST, "Invalid multipart upload request.", request);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingRequestPart(
            MissingServletRequestPartException exception,
            HttpServletRequest request
    ) {
        return errorResponse(HttpStatus.BAD_REQUEST, "Upload file is required.", request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(
            ResponseStatusException exception,
            HttpServletRequest request
    ) {
        return errorResponse(HttpStatus.valueOf(exception.getStatusCode().value()), exception.getReason(), request);
    }

    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ApiErrorResponse> handleErrorResponseException(
            ErrorResponseException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String message = exception.getBody().getDetail();
        if (message == null || message.isBlank()) {
            message = status.getReasonPhrase();
        }
        return errorResponse(status, message, request);
    }

    private ResponseEntity<ApiErrorResponse> errorResponse(
            HttpStatus status,
            String message,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(
                        Instant.now(),
                        status.value(),
                        status.getReasonPhrase(),
                        message == null || message.isBlank() ? status.getReasonPhrase() : message,
                        request.getRequestURI()
                ));
    }

    private static String normalizeUploadLimitLabel(String rawValue) {
        String trimmed = rawValue == null ? "" : rawValue.trim();
        if (trimmed.matches("\\d+MB")) {
            return trimmed.substring(0, trimmed.length() - 2) + " MB";
        }
        if (trimmed.matches("\\d+KB")) {
            return trimmed.substring(0, trimmed.length() - 2) + " KB";
        }
        if (trimmed.matches("\\d+GB")) {
            return trimmed.substring(0, trimmed.length() - 2) + " GB";
        }
        return trimmed.isBlank() ? "configured upload" : trimmed;
    }
}
