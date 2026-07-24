package com.rsinelli.gymtracker.exception;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * Handles {@link ApiException} and any otherwise-unmapped exception. Bean
 * Validation failures ({@code ConstraintViolationException}) are handled by
 * the separate, more specifically-typed {@link ConstraintViolationExceptionMapper}
 * — see its Javadoc for why a {@code Throwable}-typed mapper cannot win that
 * resolution against Quarkus's built-in validation mapper.
 */
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

        // A WebApplicationException (e.g. NotFoundException for an unmatched route,
        // NotAllowedException for a wrong HTTP verb) already carries its own correct,
        // intentional HTTP status — it isn't an unexpected error and must not collapse
        // into a generic 500 below.
        if (exception instanceof WebApplicationException webApplicationException) {
            int statusCode = webApplicationException.getResponse().getStatus();
            Response.Status status = Response.Status.fromStatusCode(statusCode);
            String code = status != null ? status.name() : "ERROR";
            String message = status != null ? status.getReasonPhrase() : webApplicationException.getMessage();
            return Response.status(statusCode)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ErrorResponse.of(code, message, statusCode))
                    .build();
        }

        LOG.error("Unhandled exception", exception);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(ErrorResponse.of("INTERNAL_ERROR", "Erro interno inesperado.",
                        Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()))
                .build();
    }
}
