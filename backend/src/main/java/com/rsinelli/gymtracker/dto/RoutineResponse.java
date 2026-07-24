package com.rsinelli.gymtracker.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RoutineResponse(
        UUID id,
        String name,
        String description,
        Instant createdAt,
        List<RoutineExerciseResponse> exercises) {
}
