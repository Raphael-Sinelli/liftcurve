package com.rsinelli.gymtracker.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.util.List;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(ApiExceptionMapper.class);

    @Override
    public Response toResponse(Throwable exception) {
        if (exception instanceof ApiException apiException) {
            return Response.status(apiException.getStatus())
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ErrorResponse.of(apiException.getCode(), apiException.getMessage(),
                            apiException.getStatus().getStatusCode()))
                    .build();
        }

        if (exception instanceof ConstraintViolationException constraintViolationException) {
            List<ErrorResponse.FieldError> details = constraintViolationException.getConstraintViolations().stream()
                    .map(this::toFieldError)
                    .toList();
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ErrorResponse.of("VALIDATION_ERROR", "Dados inválidos.",
                            Response.Status.BAD_REQUEST.getStatusCode(), details))
                    .build();
        }

        LOG.error("Unhandled exception", exception);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(ErrorResponse.of("INTERNAL_ERROR", "Erro interno inesperado.",
                        Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()))
                .build();
    }

    private ErrorResponse.FieldError toFieldError(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
        return new ErrorResponse.FieldError(field, violation.getMessage());
    }
}
