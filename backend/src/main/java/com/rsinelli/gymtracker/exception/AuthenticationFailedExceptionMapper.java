package com.rsinelli.gymtracker.exception;

import io.quarkus.security.AuthenticationFailedException;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Quarkus's JWT auth mechanism raises this before normal JAX-RS exception-mapper
 * resolution runs, so a bare {@code @Provider} can be silently skipped in some
 * Quarkus versions — {@code @Priority(Priorities.AUTHENTICATION)} is required for
 * this mapper to actually participate (see quarkusio/quarkus#25732, #29896).
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class AuthenticationFailedExceptionMapper implements ExceptionMapper<AuthenticationFailedException> {

    @Override
    public Response toResponse(AuthenticationFailedException exception) {
        return Response.status(Response.Status.UNAUTHORIZED)
                .type(MediaType.APPLICATION_JSON)
                .entity(ErrorResponse.of("INVALID_TOKEN", "Token de acesso inválido ou expirado.",
                        Response.Status.UNAUTHORIZED.getStatusCode()))
                .build();
    }
}
