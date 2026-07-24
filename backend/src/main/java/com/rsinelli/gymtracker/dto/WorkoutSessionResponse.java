package com.rsinelli.gymtracker.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkoutSessionResponse(
        UUID id,
        UUID routineId,
        Instant startedAt,
        Instant finishedAt,
        String notes,
        List<SessionSetResponse> sets) {
}
