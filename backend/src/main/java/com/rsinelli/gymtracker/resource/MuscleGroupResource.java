package com.rsinelli.gymtracker.resource;

import com.rsinelli.gymtracker.dto.MuscleGroupResponse;
import com.rsinelli.gymtracker.repository.MuscleGroupRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/muscle-groups")
@Tag(name = "MuscleGroups", description = "Catálogo fixo de grupos musculares")
@Produces(MediaType.APPLICATION_JSON)
public class MuscleGroupResource {

    @Inject
    MuscleGroupRepository muscleGroupRepository;

    @GET
    @Operation(summary = "Lista todos os grupos musculares")
    @APIResponse(responseCode = "200", description = "Lista de grupos musculares")
    public Response list() {
        List<MuscleGroupResponse> response = muscleGroupRepository.listAllOrderedByName().stream()
                .map(mg -> new MuscleGroupResponse(mg.getId(), mg.getName()))
                .toList();
        return Response.ok(response).build();
    }
}
