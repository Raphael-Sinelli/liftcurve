package com.rsinelli.gymtracker.resource;

import com.rsinelli.gymtracker.dto.AuthResponse;
import com.rsinelli.gymtracker.dto.LoginRequest;
import com.rsinelli.gymtracker.dto.RefreshRequest;
import com.rsinelli.gymtracker.dto.RegisterRequest;
import com.rsinelli.gymtracker.service.AuthService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService authService;

    @POST
    @Path("/register")
    public Response register(@Valid RegisterRequest request) {
        AuthResponse response = authService.register(request.email(), request.password(), request.name());
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @POST
    @Path("/login")
    public Response login(@Valid LoginRequest request) {
        AuthResponse response = authService.login(request.email(), request.password());
        return Response.ok(response).build();
    }

    @POST
    @Path("/refresh")
    public Response refresh(@Valid RefreshRequest request) {
        AuthResponse response = authService.refresh(request.refreshToken());
        return Response.ok(response).build();
    }

    @POST
    @Path("/logout")
    public Response logout(@Valid RefreshRequest request) {
        authService.logout(request.refreshToken());
        return Response.noContent().build();
    }
}
