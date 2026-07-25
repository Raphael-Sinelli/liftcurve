package com.rsinelli.gymtracker.resource;

import com.rsinelli.gymtracker.dto.PlateauAlertResponse;
import com.rsinelli.gymtracker.dto.ProgressionResponse;
import com.rsinelli.gymtracker.dto.VolumeBucketResponse;
import com.rsinelli.gymtracker.service.DashboardService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
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

@Path("/dashboard")
@Tag(name = "Dashboard", description = "Insights derivados: progressão de 1RM, volume por grupo/semana, alertas de platô")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class DashboardResource {

    @Inject
    DashboardService dashboardService;

    @GET
    @Path("/progression/{exerciseId}")
    @Operation(summary = "Série temporal de 1RM estimado para um exercício",
            description = "1 ponto por sessão em que o exercício foi executado, valor = maior estimated_1rm_best entre as séries daquela sessão para esse exercício, em ordem cronológica. Exercício sem nenhuma série registrada retorna 200 com lista vazia (não é erro).")
    @APIResponse(responseCode = "200", description = "Pontos de progressão (pode ser lista vazia)")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
    @APIResponse(responseCode = "404", description = "Exercício não encontrado ou não visível para este usuário")
    public Response progression(@PathParam("exerciseId") UUID exerciseId) {
        ProgressionResponse response = dashboardService.progression(exerciseId);
        return Response.ok(response).build();
    }

    @GET
    @Path("/volume")
    @Operation(summary = "Volume de treino agregado por grupo muscular e semana",
            description = "volume = soma de (reps × peso) de todas as séries, agrupado por grupo muscular e por semana ISO (segunda-feira 00:00 UTC), sem paginação — cobre todo o histórico do usuário atual.")
    @APIResponse(responseCode = "200", description = "Buckets de volume semanal")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
    public Response volume() {
        List<VolumeBucketResponse> response = dashboardService.volume();
        return Response.ok(response).build();
    }

    @GET
    @Path("/plateaus")
    @Operation(summary = "Alertas de platô ativos (exercícios estagnados)",
            description = "Retorna só os exercícios com platô ativo (3+ sessões consecutivas sem novo recorde de 1RM, streak terminando na sessão mais recente, mínimo de 4 sessões no histórico). Exercícios sem platô ou com histórico insuficiente não aparecem na lista.")
    @APIResponse(responseCode = "200", description = "Lista de alertas ativos")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
    public Response plateaus() {
        List<PlateauAlertResponse> response = dashboardService.plateaus();
        return Response.ok(response).build();
    }
}
