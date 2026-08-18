package com.tradewise.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * The single error envelope used by every non-2xx response, including security failures.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String errorCode,
        String message,
        String path,
        List<FieldValidationError> fieldErrors
) {

    public static ErrorResponse of(ErrorCode errorCode, String message, String path) {
        return new ErrorResponse(Instant.now(), errorCode.getStatus().value(), errorCode.name(), message, path, null);
    }

    public static ErrorResponse validation(String path, List<FieldValidationError> fieldErrors) {
        return new ErrorResponse(Instant.now(), ErrorCode.VALIDATION_FAILED.getStatus().value(),
                ErrorCode.VALIDATION_FAILED.name(), "Request validation failed", path, fieldErrors);
    }
}
