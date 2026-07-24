package com.rsinelli.gymtracker.exception;

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

        LOG.error("Unhandled exception", exception);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(ErrorResponse.of("INTERNAL_ERROR", "Erro interno inesperado.",
                        Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()))
                .build();
    }
}
