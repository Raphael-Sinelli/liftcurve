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
    @Operation(summary = "Lista as sessões de treino do usuário autenticado")
    @APIResponse(responseCode = "200", description = "Lista de sessões (resumo)")
    public Response list() {
        List<WorkoutSessionSummaryResponse> response = workoutSessionService.list();
        return Response.ok(response).build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Retorna o detalhe de uma sessão, incluindo séries registradas")
    @APIResponse(responseCode = "200", description = "Detalhe da sessão")
    @APIResponse(responseCode = "404", description = "Sessão não encontrada ou pertence a outro usuário")
    public Response get(@PathParam("id") UUID id) {
        WorkoutSessionResponse response = workoutSessionService.get(id);
        return Response.ok(response).build();
    }

    @POST
    @Operation(summary = "Inicia uma nova sessão de treino")
    @APIResponse(responseCode = "201", description = "Sessão iniciada")
    @APIResponse(responseCode = "400", description = "Referência de rotina inválida")
    @APIResponse(responseCode = "409", description = "Já existe uma sessão ativa")
    public Response create(@Valid WorkoutSessionRequest request) {
        WorkoutSessionResponse response = workoutSessionService.create(request.routineId(), request.notes());
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @PATCH
    @Path("/{id}")
    @Operation(summary = "Finaliza uma sessão de treino")
    @APIResponse(responseCode = "200", description = "Sessão finalizada")
    @APIResponse(responseCode = "404", description = "Sessão não encontrada ou pertence a outro usuário")
    @APIResponse(responseCode = "409", description = "Sessão já estava finalizada")
    public Response finish(@PathParam("id") UUID id, @Valid FinishWorkoutSessionRequest request) {
        WorkoutSessionResponse response = workoutSessionService.finish(id, request.notes());
        return Response.ok(response).build();
    }

    @POST
    @Path("/{id}/sets")
    @Operation(summary = "Registra uma série executada na sessão")
    @APIResponse(responseCode = "201", description = "Série registrada")
    @APIResponse(responseCode = "400", description = "Referência de exercício inválida ou payload inválido")
    @APIResponse(responseCode = "404", description = "Sessão não encontrada ou pertence a outro usuário")
    @APIResponse(responseCode = "409", description = "Sessão já finalizada")
    public Response addSet(@PathParam("id") UUID id, @Valid SessionSetRequest request) {
        SessionSetResponse response = workoutSessionService.addSet(
                id, request.exerciseId(), request.weightKg(), request.reps(), request.rpe());
        return Response.status(Response.Status.CREATED).entity(response).build();
    }
}
