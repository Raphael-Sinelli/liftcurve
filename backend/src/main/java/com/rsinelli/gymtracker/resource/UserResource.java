package com.rsinelli.gymtracker.resource;

import com.rsinelli.gymtracker.dto.UserResponse;
import com.rsinelli.gymtracker.entity.UserEntity;
import com.rsinelli.gymtracker.exception.ApiException;
import com.rsinelli.gymtracker.repository.UserRepository;
import com.rsinelli.gymtracker.security.CurrentUser;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/users")
@Tag(name = "Users", description = "Perfil do usuário autenticado")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class UserResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    UserRepository userRepository;

    @GET
    @Path("/me")
    @Operation(summary = "Retorna o perfil do usuário autenticado")
    @APIResponse(responseCode = "200", description = "Perfil do usuário")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
    public Response me() {
        UserEntity user = userRepository.findByIdOptional(currentUser.getId())
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado.", Response.Status.NOT_FOUND));
        return Response.ok(new UserResponse(user.getId(), user.getEmail(), user.getName())).build();
    }
}
