package com.rsinelli.gymtracker.resource;

import com.rsinelli.gymtracker.dto.RoutineRequest;
import com.rsinelli.gymtracker.dto.RoutineResponse;
import com.rsinelli.gymtracker.dto.RoutineSummaryResponse;
import com.rsinelli.gymtracker.service.RoutineService;
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

@Path("/routines")
@Tag(name = "Routines", description = "Rotinas/templates de treino do usuário autenticado")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class RoutineResource {

    @Inject
    RoutineService routineService;

    @GET
    @Operation(summary = "Lista as rotinas do usuário autenticado",
            description = "Retorna só as rotinas do próprio usuário — rotinas não têm catálogo compartilhado.")
    @APIResponse(responseCode = "200", description = "Lista de rotinas (resumo)")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
    public Response list() {
        List<RoutineSummaryResponse> response = routineService.list();
        return Response.ok(response).build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Retorna o detalhe de uma rotina, incluindo exercícios ordenados",
            description = "Rotina de outro usuário sempre retorna 404 (nunca 403), já que não existe catálogo compartilhado de rotinas.")
    @APIResponse(responseCode = "200", description = "Detalhe da rotina")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
    @APIResponse(responseCode = "404", description = "Rotina não encontrada ou pertence a outro usuário")
    public Response get(@PathParam("id") UUID id) {
        RoutineResponse response = routineService.get(id);
        return Response.ok(response).build();
    }

    @POST
    @Operation(summary = "Cria uma rotina com seus exercícios ordenados",
            description = "A ordem dos exercícios (order_index) é derivada da posição no array recebido — não é um campo aceito no payload.")
    @APIResponse(responseCode = "201", description = "Rotina criada")
    @APIResponse(responseCode = "400", description = "Payload inválido ou exercício inexistente")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
    public Response create(@Valid RoutineRequest request) {
        RoutineResponse response = routineService.create(request.name(), request.description(), request.exercises());
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Substitui nome/descrição e recria a lista de exercícios da rotina",
            description = "A lista de exercícios inteira é substituída (delete-and-reinsert), não é um PATCH incremental.")
    @APIResponse(responseCode = "200", description = "Rotina atualizada")
    @APIResponse(responseCode = "400", description = "Payload inválido ou exercício inexistente")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
    @APIResponse(responseCode = "404", description = "Rotina não encontrada ou pertence a outro usuário")
    public Response update(@PathParam("id") UUID id, @Valid RoutineRequest request) {
        RoutineResponse response = routineService.update(id, request.name(), request.description(), request.exercises());
        return Response.ok(response).build();
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Remove uma rotina do usuário autenticado",
            description = "Sessões de treino que referenciavam essa rotina não são afetadas — o vínculo (routine_id) só é anulado (ON DELETE SET NULL), o histórico da sessão permanece.")
    @APIResponse(responseCode = "204", description = "Rotina removida")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
    @APIResponse(responseCode = "404", description = "Rotina não encontrada ou pertence a outro usuário")
    public Response delete(@PathParam("id") UUID id) {
        routineService.delete(id);
        return Response.noContent().build();
    }
}
