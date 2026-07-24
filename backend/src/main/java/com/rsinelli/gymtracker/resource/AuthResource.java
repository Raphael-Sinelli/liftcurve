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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/auth")
@Tag(name = "Auth", description = "Registro, login e ciclo de vida de tokens")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService authService;

    @POST
    @Path("/register")
    @Operation(summary = "Cria uma nova conta de usuário")
    @APIResponse(responseCode = "201", description = "Conta criada, tokens emitidos")
    @APIResponse(responseCode = "409", description = "Email já cadastrado")
    public Response register(@Valid RegisterRequest request) {
        AuthResponse response = authService.register(request.email(), request.password(), request.name());
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @POST
    @Path("/login")
    @Operation(summary = "Autentica um usuário existente")
    @APIResponse(responseCode = "200", description = "Login bem-sucedido, tokens emitidos")
    @APIResponse(responseCode = "401", description = "Credenciais inválidas")
    public Response login(@Valid LoginRequest request) {
        AuthResponse response = authService.login(request.email(), request.password());
        return Response.ok(response).build();
    }

    @POST
    @Path("/refresh")
    @Operation(summary = "Rotaciona o refresh token e emite novo access token")
    @APIResponse(responseCode = "200", description = "Novo par de tokens emitido")
    @APIResponse(responseCode = "401", description = "Refresh token inválido, expirado ou já revogado")
    public Response refresh(@Valid RefreshRequest request) {
        AuthResponse response = authService.refresh(request.refreshToken());
        return Response.ok(response).build();
    }

    @POST
    @Path("/logout")
    @Operation(summary = "Revoga o refresh token, encerrando a sessão")
    @APIResponse(responseCode = "204", description = "Sessão encerrada")
    public Response logout(@Valid RefreshRequest request) {
        authService.logout(request.refreshToken());
        return Response.noContent().build();
    }
}
