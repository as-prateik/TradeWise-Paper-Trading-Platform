package com.tradewise.exception;

import lombok.Getter;

/**
 * Business exception carrying a stable {@link ErrorCode}; translated to the standard
 * error envelope by {@link GlobalExceptionHandler}.
 */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
