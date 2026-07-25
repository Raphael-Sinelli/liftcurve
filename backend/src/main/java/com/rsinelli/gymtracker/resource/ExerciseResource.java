package com.rsinelli.gymtracker.resource;

import com.rsinelli.gymtracker.dto.ExerciseRequest;
import com.rsinelli.gymtracker.dto.ExerciseResponse;
import com.rsinelli.gymtracker.service.ExerciseService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

@Path("/exercises")
@Tag(name = "Exercises", description = "Catálogo global e exercícios custom por usuário")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class ExerciseResource {

    @Inject
    ExerciseService exerciseService;

    @GET
    @Operation(summary = "Lista exercícios visíveis ao usuário (catálogo global + custom próprios)")
    @APIResponse(responseCode = "200", description = "Lista de exercícios")
    public Response list() {
        List<ExerciseResponse> response = exerciseService.list();
        return Response.ok(response).build();
    }

    @POST
    @Operation(summary = "Cria um exercício custom pertencente ao usuário autenticado")
    @APIResponse(responseCode = "201", description = "Exercício criado")
    @APIResponse(responseCode = "400", description = "Payload inválido ou grupo muscular inexistente")
    public Response create(@Valid ExerciseRequest request) {
        ExerciseResponse response = exerciseService.create(request.name(), request.muscleGroupId());
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Atualiza um exercício custom do usuário autenticado")
    @APIResponse(responseCode = "200", description = "Exercício atualizado")
    @APIResponse(responseCode = "403", description = "Exercício é do catálogo global, não pode ser editado")
    @APIResponse(responseCode = "404", description = "Exercício não encontrado ou pertence a outro usuário")
    public Response update(@PathParam("id") UUID id, @Valid ExerciseRequest request) {
        ExerciseResponse response = exerciseService.update(id, request.name(), request.muscleGroupId());
        return Response.ok(response).build();
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Remove um exercício custom do usuário autenticado")
    @APIResponse(responseCode = "204", description = "Exercício removido")
    @APIResponse(responseCode = "403", description = "Exercício é do catálogo global, não pode ser removido")
    @APIResponse(responseCode = "404", description = "Exercício não encontrado ou pertence a outro usuário")
    @APIResponse(responseCode = "409", description = "Exercício está em uso (em rotinas ou sessões de treino registradas)")
    public Response delete(@PathParam("id") UUID id) {
        exerciseService.delete(id);
        return Response.noContent().build();
    }
}
