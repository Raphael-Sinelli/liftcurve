package com.rsinelli.gymtracker.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.List;

/**
 * Quarkus registers a built-in {@code ExceptionMapper<ValidationException>}
 * (io.quarkus.hibernate.validator.runtime.jaxrs.ResteasyReactiveViolationExceptionMapper).
 * JAX-RS mapper resolution picks the nearest-superclass match to the thrown
 * exception's type, and {@code ConstraintViolationException} is closer to
 * {@code ValidationException} than to {@code Throwable} — so a mapper typed
 * to {@code Throwable} (see {@link ApiExceptionMapper}) never wins against it.
 * This dedicated, more specific mapper is required for {@code @Valid} failures
 * to produce this project's {@code {"error":{...}}} contract instead of
 * Quarkus's default violation report shape.
 */
@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        List<ErrorResponse.FieldError> details = exception.getConstraintViolations().stream()
                .map(this::toFieldError)
                .toList();
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(ErrorResponse.of("VALIDATION_ERROR", "Dados inválidos.",
                        Response.Status.BAD_REQUEST.getStatusCode(), details))
                .build();
    }

    private ErrorResponse.FieldError toFieldError(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
        return new ErrorResponse.FieldError(field, violation.getMessage());
    }
}
