package com.rsinelli.gymtracker.resource;

import com.rsinelli.gymtracker.dto.FinishWorkoutSessionRequest;
import com.rsinelli.gymtracker.dto.SessionSetRequest;
import com.rsinelli.gymtracker.dto.SessionSetResponse;
import com.rsinelli.gymtracker.dto.WorkoutSessionRequest;
import com.rsinelli.gymtracker.dto.WorkoutSessionResponse;
import com.rsinelli.gymtracker.dto.WorkoutSessionSummaryResponse;
import com.rsinelli.gymtracker.service.WorkoutSessionService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
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

@Path("/workout-sessions")
@Tag(name = "WorkoutSessions", description = "Registro de sessões de treino e séries executadas")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class WorkoutSessionResource {

    @Inject
    WorkoutSessionService workoutSessionService;

    @GET
    @Operation(summary = "Lista as sessões de treino do usuário autenticado",
            description = "Cada item traz set_count (total de séries já registradas), sem o detalhe de cada série — use GET /{id} para o detalhe completo.")
    @APIResponse(responseCode = "200", description = "Lista de sessões (resumo)")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
    public Response list() {
        List<WorkoutSessionSummaryResponse> response = workoutSessionService.list();
        return Response.ok(response).build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Retorna o detalhe de uma sessão, incluindo séries registradas",
            description = "Sessão de outro usuário sempre retorna 404 (nunca 403) — sem catálogo compartilhado de sessões.")
    @APIResponse(responseCode = "200", description = "Detalhe da sessão")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
    @APIResponse(responseCode = "404", description = "Sessão não encontrada ou pertence a outro usuário")
    public Response get(@PathParam("id") UUID id) {
        WorkoutSessionResponse response = workoutSessionService.get(id);
        return Response.ok(response).build();
    }

    @POST
    @Operation(summary = "Inicia uma nova sessão de treino",
            description = "Só é permitida 1 sessão ativa (finished_at nulo) por usuário por vez. routine_id é opcional — se informado, precisa pertencer ao usuário atual.")
    @APIResponse(responseCode = "201", description = "Sessão iniciada")
    @APIResponse(responseCode = "400", description = "Referência de rotina inválida ou payload malformado")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
    @APIResponse(responseCode = "409", description = "Já existe uma sessão ativa")
    public Response create(@Valid WorkoutSessionRequest request) {
        WorkoutSessionResponse response = workoutSessionService.create(request.routineId(), request.notes());
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @PATCH
    @Path("/{id}")
    @Operation(summary = "Finaliza uma sessão de treino",
            description = "Seta finished_at = now() no servidor. Não é possível finalizar uma sessão já finalizada nem adicionar séries depois de finalizada.")
    @APIResponse(responseCode = "200", description = "Sessão finalizada")
    @APIResponse(responseCode = "400", description = "Payload malformado")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
    @APIResponse(responseCode = "404", description = "Sessão não encontrada ou pertence a outro usuário")
    @APIResponse(responseCode = "409", description = "Sessão já estava finalizada")
    public Response finish(@PathParam("id") UUID id, @Valid FinishWorkoutSessionRequest request) {
        WorkoutSessionResponse response = workoutSessionService.finish(id, request.notes());
        return Response.ok(response).build();
    }

    @POST
    @Path("/{id}/sets")
    @Operation(summary = "Registra uma série executada na sessão",
            description = "set_number é calculado no servidor (posição entre as séries já registradas daquele exercício naquela sessão, reinicia por exercício) — não é aceito como campo do payload. O 1RM estimado (Epley/Brzycki/melhor) é calculado e persistido no insert.")
    @APIResponse(responseCode = "201", description = "Série registrada")
    @APIResponse(responseCode = "400", description = "Referência de exercício inválida ou payload inválido")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
    @APIResponse(responseCode = "404", description = "Sessão não encontrada ou pertence a outro usuário")
    @APIResponse(responseCode = "409", description = "Sessão já finalizada")
    public Response addSet(@PathParam("id") UUID id, @Valid SessionSetRequest request) {
        SessionSetResponse response = workoutSessionService.addSet(
                id, request.exerciseId(), request.weightKg(), request.reps(), request.rpe());
        return Response.status(Response.Status.CREATED).entity(response).build();
    }
}
