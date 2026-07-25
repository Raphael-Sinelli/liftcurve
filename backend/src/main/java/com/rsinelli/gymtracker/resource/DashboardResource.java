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
    @Operation(summary = "Série temporal de 1RM estimado para um exercício")
    @APIResponse(responseCode = "200", description = "Pontos de progressão (pode ser lista vazia)")
    @APIResponse(responseCode = "404", description = "Exercício não encontrado ou não visível para este usuário")
    public Response progression(@PathParam("exerciseId") UUID exerciseId) {
        ProgressionResponse response = dashboardService.progression(exerciseId);
        return Response.ok(response).build();
    }

    @GET
    @Path("/volume")
    @Operation(summary = "Volume de treino agregado por grupo muscular e semana")
    @APIResponse(responseCode = "200", description = "Buckets de volume semanal")
    public Response volume() {
        List<VolumeBucketResponse> response = dashboardService.volume();
        return Response.ok(response).build();
    }

    @GET
    @Path("/plateaus")
    @Operation(summary = "Alertas de platô ativos (exercícios estagnados)")
    @APIResponse(responseCode = "200", description = "Lista de alertas ativos")
    public Response plateaus() {
        List<PlateauAlertResponse> response = dashboardService.plateaus();
        return Response.ok(response).build();
    }
}
