package com.rsinelli.gymtracker.exception;

import java.util.List;

public record ErrorResponse(ErrorBody error) {

    public record ErrorBody(String code, String message, int status, List<FieldError> details) {
    }

    public record FieldError(String field, String message) {
    }

    public static ErrorResponse of(String code, String message, int status) {
        return new ErrorResponse(new ErrorBody(code, message, status, List.of()));
    }

    public static ErrorResponse of(String code, String message, int status, List<FieldError> details) {
        return new ErrorResponse(new ErrorBody(code, message, status, details));
    }
}
