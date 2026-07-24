package com.rsinelli.gymtracker.exception;

import io.quarkus.security.UnauthorizedException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Thrown when an anonymous request (no Authorization header) hits an
 * {@code @Authenticated} endpoint. This happens inside normal JAX-RS request
 * handling, so a plain {@code @Provider} is enough — contrast with
 * {@link AuthenticationFailedExceptionMapper}, which needs an explicit priority.
 */
@Provider
public class UnauthorizedExceptionMapper implements ExceptionMapper<UnauthorizedException> {

    @Override
    public Response toResponse(UnauthorizedException exception) {
        return Response.status(Response.Status.UNAUTHORIZED)
                .type(MediaType.APPLICATION_JSON)
                .entity(ErrorResponse.of("UNAUTHORIZED", "Autenticação necessária.",
                        Response.Status.UNAUTHORIZED.getStatusCode()))
                .build();
    }
}
