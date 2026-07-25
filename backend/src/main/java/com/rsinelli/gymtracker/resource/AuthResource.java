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
    @Operation(summary = "Cria uma nova conta de usuário",
            description = "Endpoint público. Faz hash da senha (BCrypt) e já emite o par de tokens (access + refresh) no corpo da resposta.")
    @APIResponse(responseCode = "201", description = "Conta criada, tokens emitidos")
    @APIResponse(responseCode = "400", description = "Payload inválido (email malformado, senha curta, nome vazio)")
    @APIResponse(responseCode = "409", description = "Email já cadastrado")
    public Response register(@Valid RegisterRequest request) {
        AuthResponse response = authService.register(request.email(), request.password(), request.name());
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @POST
    @Path("/login")
    @Operation(summary = "Autentica um usuário existente",
            description = "Endpoint público. Verifica a senha via BCrypt e emite um novo par de tokens.")
    @APIResponse(responseCode = "200", description = "Login bem-sucedido, tokens emitidos")
    @APIResponse(responseCode = "400", description = "Payload inválido (email ou senha ausentes)")
    @APIResponse(responseCode = "401", description = "Credenciais inválidas")
    public Response login(@Valid LoginRequest request) {
        AuthResponse response = authService.login(request.email(), request.password());
        return Response.ok(response).build();
    }

    @POST
    @Path("/refresh")
    @Operation(summary = "Rotaciona o refresh token e emite novo access token",
            description = "Endpoint público (a autenticação aqui é o próprio refresh token no corpo, não um Bearer header). O refresh token usado é revogado e um novo par é emitido.")
    @APIResponse(responseCode = "200", description = "Novo par de tokens emitido")
    @APIResponse(responseCode = "400", description = "Payload inválido (refresh token ausente)")
    @APIResponse(responseCode = "401", description = "Refresh token inválido, expirado ou já revogado")
    public Response refresh(@Valid RefreshRequest request) {
        AuthResponse response = authService.refresh(request.refreshToken());
        return Response.ok(response).build();
    }

    @POST
    @Path("/logout")
    @Operation(summary = "Revoga o refresh token, encerrando a sessão",
            description = "Endpoint público. Idempotente: revogar um token já revogado ou inexistente também retorna 204.")
    @APIResponse(responseCode = "204", description = "Sessão encerrada")
    @APIResponse(responseCode = "400", description = "Payload inválido (refresh token ausente)")
    public Response logout(@Valid RefreshRequest request) {
        authService.logout(request.refreshToken());
        return Response.noContent().build();
    }
}
