package com.rsinelli.gymtracker.dto;

import java.time.Instant;
import java.util.UUID;

public record ExerciseResponse(
        UUID id,
        String name,
        UUID muscleGroupId,
        String muscleGroupName,
        UUID ownerId,
        Instant createdAt) {
}
