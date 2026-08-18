package com.tradewise.exception;

public record FieldValidationError(String field, String message) {
}
